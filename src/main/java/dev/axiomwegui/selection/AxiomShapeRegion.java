package dev.axiomwegui.selection;

import com.moulberry.axiom.collections.PositionSet;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.AbstractRegion;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.world.World;
import dev.axiomwegui.Tr;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.core.BlockPos;

/**
 * A WorldEdit region with the exact shape of an Axiom selection (magic, lasso, freehand...).
 * The position set is a private copy made on the client thread and is never changed; moving the region
 * ({@code //move -s}, {@code //stack -s}) only changes an offset. Expanding or contracting is not supported.
 */
public final class AxiomShapeRegion extends AbstractRegion {
    private final PositionSet positions;
    private final BlockVector3 baseMin;
    private final BlockVector3 baseMax;
    private final int count;
    private BlockVector3 offset = BlockVector3.ZERO;

    public AxiomShapeRegion(World world, PositionSet positions, BlockPos min, BlockPos max) {
        this(world, positions, BlockVector3.at(min.getX(), min.getY(), min.getZ()),
            BlockVector3.at(max.getX(), max.getY(), max.getZ()), positions.count(), BlockVector3.ZERO);
    }

    private AxiomShapeRegion(World world, PositionSet positions, BlockVector3 baseMin, BlockVector3 baseMax, int count,
                             BlockVector3 offset) {
        super(world);
        this.positions = positions;
        this.baseMin = baseMin;
        this.baseMax = baseMax;
        this.count = count;
        this.offset = offset;
    }

    /** Calls the consumer for every block of the region, offset included. */
    public void forEachPosition(PositionVisitor visitor) {
        int dx = offset.x();
        int dy = offset.y();
        int dz = offset.z();
        positions.forEach((x, y, z) -> visitor.accept(x + dx, y + dy, z + dz));
    }

    @FunctionalInterface
    public interface PositionVisitor {
        void accept(int x, int y, int z);
    }

    public boolean isUnmoved() {
        return offset.equals(BlockVector3.ZERO);
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        return baseMin.add(offset);
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        return baseMax.add(offset);
    }

    @Override
    public long getVolume() {
        return count;
    }

    @Override
    public boolean contains(BlockVector3 position) {
        return positions.contains(position.x() - offset.x(), position.y() - offset.y(), position.z() - offset.z());
    }

    @Override
    public Iterator<BlockVector3> iterator() {
        LongArrayList packed = new LongArrayList(count);
        positions.forEach((x, y, z) -> packed.add(BlockPos.asLong(x, y, z)));
        int dx = offset.x();
        int dy = offset.y();
        int dz = offset.z();
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < packed.size();
            }

            @Override
            public BlockVector3 next() {
                if (index >= packed.size()) {
                    throw new NoSuchElementException();
                }
                long p = packed.getLong(index++);
                return BlockVector3.at(BlockPos.getX(p) + dx, BlockPos.getY(p) + dy, BlockPos.getZ(p) + dz);
            }
        };
    }

    @Override
    public void expand(BlockVector3... changes) throws RegionOperationException {
        throw new RegionOperationException(Tr.t("selection.shape_fixed"));
    }

    @Override
    public void contract(BlockVector3... changes) throws RegionOperationException {
        throw new RegionOperationException(Tr.t("selection.shape_fixed"));
    }

    @Override
    public void shift(BlockVector3 change) {
        offset = offset.add(change);
    }

    @Override
    public AxiomShapeRegion clone() {
        return new AxiomShapeRegion(getWorld(), positions, baseMin, baseMax, count, offset);
    }
}
