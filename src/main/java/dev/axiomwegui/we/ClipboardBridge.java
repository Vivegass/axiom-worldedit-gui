package dev.axiomwegui.we;

import com.moulberry.axiom.clipboard.Clipboard;
import com.moulberry.axiom.clipboard.ClipboardObject;
import com.moulberry.axiom.render.regions.ChunkedBlockRegion;
import com.moulberry.axiom.world_modification.CompressedBlockEntity;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.coremc.internal.NBTConverter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.Tr;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.enginehub.linbus.tree.LinCompoundTag;

/**
 * Copies the clipboard between Axiom and WorldEdit. Axiom keeps clipboard blocks around (0,0,0) at the
 * centre of what was copied; WorldEdit keeps them around the player position at copy time (the origin).
 * Axiom → WorldEdit puts Axiom's centre at the WorldEdit origin, so //paste centres on the player.
 * WorldEdit → Axiom bakes in //rotate and //flip, then centres the blocks for Axiom's placement.
 */
public final class ClipboardBridge {
    private ClipboardBridge() {
    }

    private record Block(int x, int y, int z, BlockState state, CompoundTag nbt) {
    }

    /** Client thread. The future holds a message for the player. */
    public static CompletableFuture<String> axiomToWorldEdit() {
        CompletableFuture<String> future = new CompletableFuture<>();
        ClipboardObject clip = Clipboard.INSTANCE.getClipboard();
        if (clip == null || clip.blockRegion() == null || clip.blockRegion().count() == 0) {
            future.complete(Tr.t("clipboard.axiom_empty"));
            return future;
        }
        // Read Axiom's data on the client thread; the server thread only gets plain copies.
        List<Block> blocks = new ArrayList<>();
        Long2ObjectMap<CompressedBlockEntity> entities = clip.blockEntities();
        clip.blockRegion().forEachEntry((x, y, z, state) -> {
            CompoundTag nbt = null;
            if (entities != null) {
                CompressedBlockEntity be = entities.get(BlockPos.asLong(x, y, z));
                if (be != null) {
                    nbt = be.decompress();
                }
            }
            blocks.add(new Block(x, y, z, state, nbt));
        });
        BlockPos min = clip.blockRegion().min();
        BlockPos max = clip.blockRegion().max();
        onServer((actor, session) -> {
            BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(
                BlockVector3.at(min.getX(), min.getY(), min.getZ()), BlockVector3.at(max.getX(), max.getY(), max.getZ())));
            clipboard.setOrigin(BlockVector3.ZERO);
            FabricAdapter adapter = FabricAdapter.get();
            for (Block b : blocks) {
                com.sk89q.worldedit.world.block.BlockState weState = adapter.fromNativeBlockState(b.state());
                BaseBlock base = b.nbt() == null ? weState.toBaseBlock()
                    : weState.toBaseBlock(LazyReference.computed(NBTConverter.fromNative(b.nbt())));
                clipboard.setBlock(BlockVector3.at(b.x(), b.y(), b.z()), base);
            }
            session.setClipboard(new ClipboardHolder(clipboard));
            return Tr.t("clipboard.to_worldedit_done", blocks.size());
        }, future);
        return future;
    }

    /** Client thread. The future holds a message for the player. */
    public static CompletableFuture<String> worldEditToAxiom() {
        CompletableFuture<String> future = new CompletableFuture<>();
        CompletableFuture<List<Block>> blocksFuture = new CompletableFuture<>();
        onServer((actor, session) -> {
            ClipboardHolder holder;
            try {
                holder = session.getClipboard();
            } catch (EmptyClipboardException e) {
                blocksFuture.complete(null);
                return Tr.t("clipboard.worldedit_empty");
            }
            com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = holder.getClipboard();
            Transform transform = holder.getTransform();
            BlockVector3 origin = clipboard.getOrigin();
            FabricAdapter adapter = FabricAdapter.get();
            List<Block> out = new ArrayList<>();
            for (BlockVector3 pos : clipboard.getRegion()) {
                BaseBlock block = clipboard.getFullBlock(pos);
                if (block.getBlockType().getMaterial().isAir()) {
                    continue;
                }
                BlockVector3 rel = pos.subtract(origin);
                if (!transform.isIdentity()) {
                    Vector3 moved = transform.apply(rel.toVector3());
                    rel = moved.round().toBlockPoint();
                    block = BlockTransformExtent.transform(block, transform);
                }
                CompoundTag nbt = null;
                LazyReference<LinCompoundTag> ref = block.getNbtReference();
                if (ref != null && ref.getValue() != null) {
                    nbt = NBTConverter.toNative(ref.getValue());
                    for (String key : new String[] {"id", "Id", "x", "y", "z", "Pos", "keepPacked"}) {
                        nbt.remove(key);
                    }
                }
                out.add(new Block(rel.x(), rel.y(), rel.z(), adapter.toNativeBlockState(block.toImmutableState()), nbt));
            }
            blocksFuture.complete(out);
            return null;
        }, new CompletableFuture<>()).thenAccept(message -> {
            if (message != null) {
                future.complete(message);
            }
        });
        blocksFuture.thenAccept(blocks -> Minecraft.getInstance().execute(() -> {
            if (blocks == null) {
                if (!future.isDone()) {
                    future.complete(Tr.t("clipboard.worldedit_empty"));
                }
                return;
            }
            if (blocks.isEmpty()) {
                future.complete(Tr.t("clipboard.worldedit_empty"));
                return;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Block b : blocks) {
                minX = Math.min(minX, b.x()); minY = Math.min(minY, b.y()); minZ = Math.min(minZ, b.z());
                maxX = Math.max(maxX, b.x()); maxY = Math.max(maxY, b.y()); maxZ = Math.max(maxZ, b.z());
            }
            int cx = Math.floorDiv(minX + maxX, 2);
            int cy = minY;
            int cz = Math.floorDiv(minZ + maxZ, 2);
            ChunkedBlockRegion region = new ChunkedBlockRegion();
            Long2ObjectMap<CompressedBlockEntity> blockEntities = new Long2ObjectOpenHashMap<>();
            ByteArrayOutputStream scratch = new ByteArrayOutputStream();
            for (Block b : blocks) {
                int x = b.x() - cx;
                int y = b.y() - cy;
                int z = b.z() - cz;
                region.addBlock(x, y, z, b.state());
                if (b.nbt() != null) {
                    scratch.reset();
                    blockEntities.put(BlockPos.asLong(x, y, z), CompressedBlockEntity.compress(b.nbt(), scratch));
                }
            }
            Clipboard.INSTANCE.setClipboard(region, blockEntities, List.of(), "WorldEdit", 0f, false);
            future.complete(Tr.t("clipboard.to_axiom_done", blocks.size()));
        }));
        return future;
    }

    private interface ServerTask {
        String run(Player actor, LocalSession session);
    }

    private static CompletableFuture<String> onServer(ServerTask task, CompletableFuture<String> future) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            future.complete(Tr.t("status.multiplayer"));
            return future;
        }
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            String message;
            try {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
                if (serverPlayer == null) {
                    message = Tr.t("status.no_player");
                } else {
                    Player actor = FabricAdapter.get().fromNativePlayer(serverPlayer);
                    message = task.run(actor, WorldEdit.getInstance().getSessionManager().get(actor));
                }
            } catch (Throwable t) {
                AxiomWorldEditGui.LOGGER.error("Clipboard transfer failed", t);
                message = t.toString();
            }
            String done = message;
            mc.execute(() -> future.complete(done));
        });
        return future;
    }
}
