package dev.axiomwegui.journal;

import com.moulberry.axiom.world_modification.BlockBuffer;
import com.moulberry.axiom.world_modification.BlockOrBiomeBuffer;
import com.moulberry.axiom.world_modification.CompressedBlockEntity;
import com.moulberry.axiom.world_modification.HistoryEntry;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.coremc.internal.NBTConverter;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.GuiConfig;
import dev.axiomwegui.we.CommandContext;
import dev.axiomwegui.we.Singleplayer;
import dev.axiomwegui.we.WeResult;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.enginehub.linbus.tree.LinCompoundTag;

/**
 * The shared undo history. When WorldEdit is about to remember an edit in its own history, the edit is
 * turned into an Axiom history entry instead, so Ctrl+Z and the History window in Axiom undo it.
 * Edits that touch entities or biomes stay in WorldEdit's history, because an Axiom block entry cannot
 * restore them.
 */
public final class Journal {
    private Journal() {
    }

    /**
     * Called on the server thread from {@code LocalSession.remember}. Returns true if the edit went to
     * the Axiom history, in which case WorldEdit must not remember it.
     */
    public static boolean tryRecord(EditSession editSession) {
        if (!GuiConfig.isJournalEnabled() || !AxiomHistory.isAvailable() || !Singleplayer.active()
            || editSession.size() == 0) {
            return false;
        }
        CommandContext ctx = CommandContext.current();
        try {
            editSession.flushSession();
            // WorldEdit's default change set (BlockOptimizedHistory) keeps old and new blocks in separate lists:
            // the forward iterator only knows the new block of each position and the backward iterator only
            // the old one (both sides of each BlockChange hold the same block). So "after" is read going
            // forward (the last write wins) and "before" going backward (the earliest write wins).
            Long2ObjectLinkedOpenHashMap<BaseBlock[]> changes = new Long2ObjectLinkedOpenHashMap<>();
            Iterator<Change> forward = editSession.getChangeSet().forwardIterator();
            while (forward.hasNext()) {
                Change change = forward.next();
                if (!(change instanceof BlockChange block)) {
                    // Entities or biomes: keep the whole edit in WorldEdit's history.
                    if (ctx != null) {
                        ctx.journal = WeResult.Journal.WORLDEDIT;
                    }
                    return false;
                }
                BlockVector3 p = block.position();
                long key = BlockPos.asLong(p.x(), p.y(), p.z());
                BaseBlock[] pair = changes.get(key);
                if (pair == null) {
                    changes.put(key, new BaseBlock[] {null, block.current()});
                } else {
                    pair[1] = block.current();
                }
            }
            if (changes.isEmpty()) {
                return false;
            }
            Iterator<Change> backward = editSession.getChangeSet().backwardIterator();
            while (backward.hasNext()) {
                if (backward.next() instanceof BlockChange block) {
                    BlockVector3 p = block.position();
                    BaseBlock[] pair = changes.get(BlockPos.asLong(p.x(), p.y(), p.z()));
                    if (pair != null) {
                        pair[0] = block.previous();
                    }
                }
            }
            for (BaseBlock[] pair : changes.values()) {
                if (pair[0] == null) {
                    AxiomWorldEditGui.LOGGER.warn("WorldEdit's history has no old block for a change; keeping the edit in WorldEdit's history");
                    if (ctx != null) {
                        ctx.journal = WeResult.Journal.WORLDEDIT;
                    }
                    return false;
                }
            }

            BlockBuffer forwards = new BlockBuffer();
            BlockBuffer backwards = new BlockBuffer();
            ByteArrayOutputStream scratch = new ByteArrayOutputStream();
            FabricAdapter adapter = FabricAdapter.get();
            long first = changes.firstLongKey();
            for (Long2ObjectLinkedOpenHashMap.Entry<BaseBlock[]> entry : changes.long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                int x = BlockPos.getX(key);
                int y = BlockPos.getY(key);
                int z = BlockPos.getZ(key);
                put(backwards, x, y, z, entry.getValue()[0], adapter, scratch);
                put(forwards, x, y, z, entry.getValue()[1], adapter, scratch);
            }

            String command = ctx != null ? ctx.command : null;
            String description = "WorldEdit" + (command == null ? "" : ": " + shorten(command));
            BlockPos origin = BlockPos.of(first);
            HistoryEntry<BlockOrBiomeBuffer> historyEntry = new HistoryEntry<>(forwards, backwards, origin, description,
                HistoryEntry.MODIFIER_CAN_BE_UNDONE_INGAME);
            int count = changes.size();
            if (ctx != null) {
                ctx.journal = WeResult.Journal.AXIOM;
                ctx.journaledBlocks += count;
            }
            Minecraft.getInstance().execute(() -> AxiomHistory.push(historyEntry));
            return true;
        } catch (Throwable t) {
            AxiomWorldEditGui.LOGGER.error("Could not move a WorldEdit edit into the Axiom history; it stays in WorldEdit's", t);
            if (ctx != null) {
                ctx.journal = WeResult.Journal.WORLDEDIT;
            }
            return false;
        }
    }

    private static void put(BlockBuffer buffer, int x, int y, int z, BaseBlock block, FabricAdapter adapter,
                            ByteArrayOutputStream scratch) {
        BlockState state = adapter.toNativeBlockState(block.toImmutableState());
        buffer.set(x, y, z, state);
        LazyReference<LinCompoundTag> nbt = block.getNbtReference();
        if (nbt != null) {
            LinCompoundTag value = nbt.getValue();
            if (value != null) {
                CompoundTag tag = NBTConverter.toNative(value);
                // Axiom stores only the block entity's own data, like a schematic does.
                for (String key : new String[] {"id", "Id", "x", "y", "z", "Pos", "keepPacked"}) {
                    tag.remove(key);
                }
                scratch.reset();
                buffer.putBlockEntity(x, y, z, CompressedBlockEntity.compress(tag, scratch));
            }
        }
    }

    private static String shorten(String command) {
        return command.length() <= 48 ? command : command.substring(0, 45) + "...";
    }
}
