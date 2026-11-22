package dev.axiomwegui.journal;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Notes the old state of every block a level changes while capturing is on (server thread only).
 *
 * <p>WorldEdit's item brush uses the item on the world directly, like a player would: bone meal growing
 * grass and flowers, saplings growing into trees. Those blocks never pass through WorldEdit's edit, so
 * nothing could undo them. The brush backend captures them here and adds them to the stroke's edit.
 */
public final class WorldCapture {
    /** Cheap check for every setBlock in the game; only true while a capture runs. */
    private static volatile boolean active = false;
    private static Level level = null;
    private static Thread owner = null;
    private static Long2ObjectLinkedOpenHashMap<BlockState> before = null;

    private WorldCapture() {
    }

    public static void begin(Level target) {
        level = target;
        owner = Thread.currentThread();
        before = new Long2ObjectLinkedOpenHashMap<>();
        active = true;
    }

    /** Ends the capture: position to the state the block had before the first change. */
    public static Long2ObjectLinkedOpenHashMap<BlockState> end() {
        active = false;
        Long2ObjectLinkedOpenHashMap<BlockState> out = before;
        before = null;
        level = null;
        owner = null;
        return out == null ? new Long2ObjectLinkedOpenHashMap<>() : out;
    }

    /** Called from the start of {@code Level.setBlock}. */
    public static void beforeSetBlock(Level changed, BlockPos pos) {
        if (!active || changed != level || Thread.currentThread() != owner) {
            return;
        }
        long key = pos.asLong();
        if (!before.containsKey(key)) {
            before.put(key, changed.getBlockState(pos));
        }
    }
}
