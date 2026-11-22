package dev.axiomwegui.selection;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.SelectorLimits;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import java.util.List;

/**
 * Puts an Axiom selection of any shape into a WorldEdit session. Selecting a point the WorldEdit way
 * (wand click, //pos1, //pos2) turns it into an ordinary cuboid selection.
 */
public final class AxiomRegionSelector implements RegionSelector {
    private final AxiomShapeRegion region;
    private World world;
    /** Set once the player starts a cuboid selection; from then on every call goes to it. */
    private CuboidRegionSelector cuboid = null;

    public AxiomRegionSelector(World world, AxiomShapeRegion region) {
        this.world = world;
        this.region = region;
    }

    private CuboidRegionSelector cuboid() {
        if (cuboid == null) {
            cuboid = new CuboidRegionSelector(world);
        }
        return cuboid;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public void setWorld(World world) {
        this.world = world;
        region.setWorld(world);
        if (cuboid != null) {
            cuboid.setWorld(world);
        }
    }

    @Override
    public boolean selectPrimary(BlockVector3 position, SelectorLimits limits) {
        return cuboid().selectPrimary(position, limits);
    }

    @Override
    public boolean selectSecondary(BlockVector3 position, SelectorLimits limits) {
        return cuboid().selectSecondary(position, limits);
    }

    @Override
    public void explainPrimarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        cuboid().explainPrimarySelection(actor, session, position);
    }

    @Override
    public void explainSecondarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        cuboid().explainSecondarySelection(actor, session, position);
    }

    @Override
    public void explainRegionAdjust(Actor actor, LocalSession session) {
        if (cuboid != null) {
            cuboid.explainRegionAdjust(actor, session);
        }
    }

    @Override
    public BlockVector3 getPrimaryPosition() throws IncompleteRegionException {
        return cuboid != null ? cuboid.getPrimaryPosition() : region.getMinimumPoint();
    }

    @Override
    public Region getRegion() throws IncompleteRegionException {
        return cuboid != null ? cuboid.getRegion() : region;
    }

    @Override
    public Region getIncompleteRegion() {
        return cuboid != null ? cuboid.getIncompleteRegion() : region;
    }

    @Override
    public boolean isDefined() {
        return cuboid != null ? cuboid.isDefined() : true;
    }

    @Override
    public void learnChanges() {
        if (cuboid != null) {
            cuboid.learnChanges();
        }
    }

    @Override
    public void clear() {
        cuboid().clear();
    }

    @Override
    public String getTypeName() {
        return cuboid != null ? cuboid.getTypeName() : "axiom";
    }

    @Override
    public List<Component> getSelectionInfoLines() {
        if (cuboid != null) {
            return cuboid.getSelectionInfoLines();
        }
        return List.of(TextComponent.of("Axiom selection, " + region.getVolume() + " blocks"));
    }
}
