package dev.axiomwegui.we;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.world.World;

/**
 * A cheap fingerprint of WorldEdit's selection, used to tell whether a command changed it.
 * Server thread only.
 */
public record SelectionState(RegionSelector selector, String type, Region region, BlockVector3 min, BlockVector3 max,
                             long volume) {

    public static SelectionState of(LocalSession session, World world) {
        RegionSelector selector = session.getRegionSelector(world);
        Region region = null;
        if (selector.isDefined()) {
            try {
                region = selector.getRegion();
            } catch (IncompleteRegionException ignored) {
                // stays null
            }
        }
        if (region == null) {
            return new SelectionState(selector, selector.getTypeName(), null, null, null, 0);
        }
        return new SelectionState(selector, selector.getTypeName(), region, region.getMinimumPoint(),
            region.getMaximumPoint(), region.getVolume());
    }

    public boolean defined() {
        return region != null;
    }

    /** True if a command left the selection as it was. */
    public boolean sameAs(SelectionState other) {
        return selector == other.selector
            && type.equals(other.type)
            && java.util.Objects.equals(min, other.min)
            && java.util.Objects.equals(max, other.max)
            && volume == other.volume;
    }
}
