package dev.axiomwegui.journal;

import com.moulberry.axiom.world_modification.BiomeBuffer;
import com.moulberry.axiom.world_modification.BlockBuffer;
import com.moulberry.axiom.world_modification.BlockOrBiomeBuffer;
import com.moulberry.axiom.world_modification.CompressedBlockEntity;
import com.moulberry.axiom.world_modification.HistoryEntry;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.coremc.internal.CoreMcWorld;
import com.sk89q.worldedit.coremc.internal.NBTConverter;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.history.change.BiomeChange3D;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.GuiConfig;
import dev.axiomwegui.we.CommandContext;
import dev.axiomwegui.we.Singleplayer;
import dev.axiomwegui.we.WeResult;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.enginehub.linbus.tree.LinCompoundTag;

/**
 * The shared undo history. When WorldEdit is about to remember an edit in its own history, the edit is
 * turned into an Axiom history entry instead, so Ctrl+Z and the History window in Axiom undo it.
 * Biome changes become an Axiom biome entry. Edits that touch entities stay in WorldEdit's history,
 * because Axiom's history cannot restore them.
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
            // Biomes are stored per 4x4x4 cell; WorldEdit records them per block. Keyed by cell, the earliest
            // old biome and the latest new one.
            Long2ObjectLinkedOpenHashMap<BiomeType[]> biomes = new Long2ObjectLinkedOpenHashMap<>();
            Iterator<Change> forward = editSession.getChangeSet().forwardIterator();
            while (forward.hasNext()) {
                Change change = forward.next();
                if (change instanceof BiomeChange3D biome) {
                    BlockVector3 p = biome.position();
                    long key = BlockPos.asLong(p.x() >> 2, p.y() >> 2, p.z() >> 2);
                    BiomeType[] pair = biomes.get(key);
                    if (pair == null) {
                        biomes.put(key, new BiomeType[] {biome.previous(), biome.current()});
                    } else {
                        pair[1] = biome.current();
                    }
                    continue;
                }
                if (!(change instanceof BlockChange block)) {
                    // Entities: keep the whole edit in WorldEdit's history.
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
            if (changes.isEmpty() && biomes.isEmpty()) {
                return false;
            }
            HistoryEntry<BlockOrBiomeBuffer> biomeEntry = biomes.isEmpty() ? null : biomeEntry(biomes, ctx);
            if (changes.isEmpty()) {
                if (ctx != null) {
                    ctx.journal = WeResult.Journal.AXIOM;
                    ctx.journaledBlocks += biomes.size();
                }
                Minecraft.getInstance().execute(() -> AxiomHistory.push(biomeEntry));
                return true;
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
            int reshaped = 0;
            for (Long2ObjectLinkedOpenHashMap.Entry<BlockState[]> entry
                : neighbourShapes(editSession, changes, adapter).long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                int x = BlockPos.getX(key);
                int y = BlockPos.getY(key);
                int z = BlockPos.getZ(key);
                backwards.set(x, y, z, entry.getValue()[0]);
                forwards.set(x, y, z, entry.getValue()[1]);
                reshaped++;
            }

            String command = ctx != null ? ctx.command : null;
            String description = "WorldEdit" + (command == null ? "" : ": " + shorten(command));
            BlockPos origin = BlockPos.of(first);
            HistoryEntry<BlockOrBiomeBuffer> historyEntry = new HistoryEntry<>(forwards, backwards, origin, description,
                HistoryEntry.MODIFIER_CAN_BE_UNDONE_INGAME);
            int count = changes.size() + reshaped;
            if (ctx != null) {
                ctx.journal = WeResult.Journal.AXIOM;
                ctx.journaledBlocks += count;
            }
            Minecraft.getInstance().execute(() -> {
                AxiomHistory.push(historyEntry);
                if (biomeEntry != null) {
                    // Blocks and biomes are separate Axiom history entries; undoing all of it takes two steps.
                    AxiomHistory.push(biomeEntry);
                }
            });
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

    /** Edits larger than this skip the neighbour check below; it reads six neighbours per changed block. */
    private static final int MAX_NEIGHBOUR_CHECK = 500_000;
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * Blocks next to the edit that Minecraft changed by itself when the edit was placed: grass turning snowy
     * under new snow, fences and panes connecting, stairs changing shape. WorldEdit does not record those, and
     * Axiom's undo places blocks without such updates, so undo would leave them changed. For each neighbour
     * the state it had before is worked out by updating its shape against the old blocks next to it.
     *
     * <p>A neighbour is only taken if its current state is exactly what the new blocks next to it make it,
     * so blocks the player left unconnected on purpose (placed without updates) are not touched.
     *
     * @return position to {before, after}
     */
    private static Long2ObjectLinkedOpenHashMap<BlockState[]> neighbourShapes(
            EditSession editSession, Long2ObjectLinkedOpenHashMap<BaseBlock[]> changes, FabricAdapter adapter) {
        Long2ObjectLinkedOpenHashMap<BlockState[]> out = new Long2ObjectLinkedOpenHashMap<>();
        if (changes.size() > MAX_NEIGHBOUR_CHECK || !(editSession.getWorld() instanceof CoreMcWorld world)) {
            return out;
        }
        ServerLevel level = world.getWorld();
        RandomSource random = RandomSource.create();
        LongOpenHashSet checked = new LongOpenHashSet();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (long key : changes.keySet()) {
            BlockPos changed = BlockPos.of(key);
            for (Direction direction : DIRECTIONS) {
                neighbour.setWithOffset(changed, direction);
                long neighbourKey = neighbour.asLong();
                if (changes.containsKey(neighbourKey) || !checked.add(neighbourKey)) {
                    continue;
                }
                BlockState current = level.getBlockState(neighbour);
                if (current.isAir()) {
                    continue;
                }
                BlockState before = current;
                BlockState expected = current;
                for (Direction towards : DIRECTIONS) {
                    next.setWithOffset(neighbour, towards);
                    BaseBlock[] pair = changes.get(next.asLong());
                    if (pair == null) {
                        continue;
                    }
                    BlockState old = adapter.toNativeBlockState(pair[0].toImmutableState());
                    BlockState now = adapter.toNativeBlockState(pair[1].toImmutableState());
                    BlockPos fixed = neighbour.immutable();
                    before = before.updateShape(level, level, fixed, towards, next.immutable(), old, random);
                    expected = expected.updateShape(level, level, fixed, towards, next.immutable(), now, random);
                }
                if (!before.equals(current) && expected.equals(current)) {
                    out.put(neighbourKey, new BlockState[] {before, current});
                }
            }
        }
        return out;
    }

    private static HistoryEntry<BlockOrBiomeBuffer> biomeEntry(Long2ObjectLinkedOpenHashMap<BiomeType[]> biomes,
                                                               CommandContext ctx) {
        BiomeBuffer forwards = new BiomeBuffer();
        BiomeBuffer backwards = new BiomeBuffer();
        for (Long2ObjectLinkedOpenHashMap.Entry<BiomeType[]> entry : biomes.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            backwards.set(x, y, z, biomeKey(entry.getValue()[0]));
            forwards.set(x, y, z, biomeKey(entry.getValue()[1]));
        }
        long first = biomes.firstLongKey();
        BlockPos origin = new BlockPos(BlockPos.getX(first) << 2, BlockPos.getY(first) << 2, BlockPos.getZ(first) << 2);
        String command = ctx != null ? ctx.command : null;
        String description = "WorldEdit biomes" + (command == null ? "" : ": " + shorten(command));
        return new HistoryEntry<>(forwards, backwards, origin, description, HistoryEntry.MODIFIER_CAN_BE_UNDONE_INGAME);
    }

    private static ResourceKey<Biome> biomeKey(BiomeType biome) {
        return ResourceKey.create(Registries.BIOME, Identifier.parse(biome.id()));
    }

    private static String shorten(String command) {
        return command.length() <= 48 ? command : command.substring(0, 45) + "...";
    }
}
