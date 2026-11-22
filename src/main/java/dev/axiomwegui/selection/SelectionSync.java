package dev.axiomwegui.selection;

import com.moulberry.axiom.clipboard.Selection;
import com.moulberry.axiom.clipboard.SelectionBuffer;
import com.moulberry.axiom.collections.PositionSet;
import com.moulberry.axiom.render.regions.ChunkedBooleanRegion;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.world.World;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.Tr;
import dev.axiomwegui.we.CommandContext;
import dev.axiomwegui.we.SelectionState;
import dev.axiomwegui.we.WeResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Keeps WorldEdit's selection equal to Axiom's, with Axiom as the source of truth.
 * <ul>
 *   <li>Axiom → WorldEdit: right before a command is sent (from the addon or the chat), the client thread
 *   publishes a copy of the Axiom selection if it changed; the server thread puts it into the session.</li>
 *   <li>WorldEdit → Axiom: after a command, if WorldEdit's selection changed (//expand, //shift, //chunk,
 *   //paste -s...), the new shape is sent back and becomes the Axiom selection.</li>
 * </ul>
 */
public final class SelectionSync {
    /** Largest WorldEdit selection turned into an exact Axiom voxel selection; bigger ones become a box. */
    private static final long MAX_RASTERIZE = 16_000_000L;

    /** Client → server. Set on the client thread, taken on the server thread. */
    private static volatile Snapshot pending = null;
    /** Client thread only: fingerprint of the Axiom selection that WorldEdit already has. */
    private static Fingerprint synced = null;

    private SelectionSync() {
    }

    /** A copy of the Axiom selection that the server thread may read. {@code positions} is null for a box. */
    private record Snapshot(BlockPos min, BlockPos max, PositionSet positions) {
    }

    private record Fingerprint(int identity, int size, BlockPos min, BlockPos max) {
        static Fingerprint of(SelectionBuffer buffer) {
            return new Fingerprint(System.identityHashCode(buffer), buffer.size(), buffer.min(), buffer.max());
        }
    }

    // ------------------------------------------------------------------ client thread

    /** Call on the client thread right before a WorldEdit command is sent. */
    public static void publish() {
        try {
            SelectionBuffer buffer = Selection.getSelectionBuffer();
            if (buffer == null || buffer.isEmpty()) {
                // Leave WorldEdit's own selection alone, so //pos1 + //pos2 typed in chat still work.
                return;
            }
            Fingerprint fingerprint = Fingerprint.of(buffer);
            if (fingerprint.equals(synced)) {
                return;
            }
            if (buffer instanceof SelectionBuffer.Set set) {
                pending = new Snapshot(buffer.min(), buffer.max(), set.selectionRegion.copyPositionSet());
            } else {
                pending = new Snapshot(buffer.min(), buffer.max(), null);
            }
            synced = fingerprint;
        } catch (RuntimeException e) {
            AxiomWorldEditGui.LOGGER.warn("Could not read the Axiom selection", e);
        }
    }

    /** Forget everything when the world changes. */
    public static void reset() {
        pending = null;
        synced = null;
    }

    private static void applyFromWorldEdit(SelectionBuffer buffer) {
        Selection.setBufferWithHistory(buffer);
        SelectionBuffer now = Selection.getSelectionBuffer();
        synced = now == null || now.isEmpty() ? null : Fingerprint.of(now);
    }

    // ------------------------------------------------------------------ server thread

    /** Before a command: put the pending Axiom selection into the session. Server thread. */
    public static void beforeCommand(LocalSession session, World world, CommandContext ctx) {
        Snapshot snapshot = pending;
        pending = null;
        if (snapshot != null) {
            BlockVector3 min = BlockVector3.at(snapshot.min().getX(), snapshot.min().getY(), snapshot.min().getZ());
            BlockVector3 max = BlockVector3.at(snapshot.max().getX(), snapshot.max().getY(), snapshot.max().getZ());
            if (snapshot.positions() == null) {
                session.setRegionSelector(world, new CuboidRegionSelector(world, min, max));
            } else {
                session.setRegionSelector(world, new AxiomRegionSelector(world,
                    new AxiomShapeRegion(world, snapshot.positions(), snapshot.min(), snapshot.max())));
            }
        }
        ctx.selectionBefore = SelectionState.of(session, world);
    }

    /** After a command: if WorldEdit changed the selection, send the new one to Axiom. Server thread. */
    public static void afterCommand(LocalSession session, World world, CommandContext ctx) {
        if (ctx.selectionBefore != null) {
            afterChange(session, world, ctx.selectionBefore, ctx);
        }
    }

    /** After anything that may have changed WorldEdit's selection (a command, a wand click). Server thread. */
    public static void afterChange(LocalSession session, World world, SelectionState before) {
        afterChange(session, world, before, null);
    }

    private static void afterChange(LocalSession session, World world, SelectionState before, CommandContext ctx) {
        SelectionState after = SelectionState.of(session, world);
        if (after.sameAs(before) || !after.defined()) {
            return;
        }
        Snapshot shape = toAxiom(after.region(), ctx);
        // Axiom's selection buffers own render resources, so they are built on the client thread.
        Minecraft.getInstance().execute(() -> applyFromWorldEdit(shape.positions() == null
            ? new SelectionBuffer.AABB(shape.min(), shape.max())
            : new SelectionBuffer.Set(new ChunkedBooleanRegion(shape.positions()))));
    }

    private static Snapshot toAxiom(Region region, CommandContext ctx) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        BlockPos minPos = new BlockPos(min.x(), min.y(), min.z());
        BlockPos maxPos = new BlockPos(max.x(), max.y(), max.z());
        if (region instanceof CuboidRegion) {
            return new Snapshot(minPos, maxPos, null);
        }
        if (region.getVolume() > MAX_RASTERIZE) {
            if (ctx != null) ctx.lines.add(new WeResult.Line(Tr.t("selection.too_big_for_shape"), false));
            return new Snapshot(minPos, maxPos, null);
        }
        PositionSet positions = new PositionSet();
        if (region instanceof AxiomShapeRegion shape) {
            shape.forEachPosition(positions::add);
        } else {
            for (BlockVector3 v : region) {
                positions.add(v.x(), v.y(), v.z());
            }
        }
        if (positions.isEmpty()) {
            return new Snapshot(minPos, maxPos, null);
        }
        return new Snapshot(minPos, maxPos, positions);
    }
}
