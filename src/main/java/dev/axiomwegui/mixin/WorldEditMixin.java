package dev.axiomwegui.mixin;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Location;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.selection.SelectionSync;
import dev.axiomwegui.we.SelectionState;
import dev.axiomwegui.we.Singleplayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Selecting with the WorldEdit wand (outside the Axiom editor) also updates the Axiom selection. */
@Mixin(value = WorldEdit.class, remap = false)
public abstract class WorldEditMixin {
    private static final ThreadLocal<SelectionState> AXIOMWEGUI$BEFORE = new ThreadLocal<>();

    private static void axiomwegui$before(Player player) {
        AXIOMWEGUI$BEFORE.remove();
        try {
            if (Singleplayer.isLocal(player)) {
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
                AXIOMWEGUI$BEFORE.set(SelectionState.of(session, player.getWorld()));
            }
        } catch (RuntimeException e) {
            AxiomWorldEditGui.LOGGER.debug("Could not read the WorldEdit selection", e);
        }
    }

    private static void axiomwegui$after(Player player) {
        SelectionState before = AXIOMWEGUI$BEFORE.get();
        AXIOMWEGUI$BEFORE.remove();
        if (before == null) {
            return;
        }
        try {
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
            SelectionSync.afterChange(session, player.getWorld(), before);
        } catch (RuntimeException e) {
            AxiomWorldEditGui.LOGGER.warn("Could not give WorldEdit's selection to Axiom", e);
        }
    }

    @Inject(method = "handleBlockLeftClick(Lcom/sk89q/worldedit/entity/Player;Lcom/sk89q/worldedit/util/Location;Lcom/sk89q/worldedit/util/Direction;)Z",
        at = @At("HEAD"), remap = false)
    private void axiomwegui$leftHead(Player player, Location clicked, Direction face, CallbackInfoReturnable<Boolean> cir) {
        axiomwegui$before(player);
    }

    @Inject(method = "handleBlockLeftClick(Lcom/sk89q/worldedit/entity/Player;Lcom/sk89q/worldedit/util/Location;Lcom/sk89q/worldedit/util/Direction;)Z",
        at = @At("RETURN"), remap = false)
    private void axiomwegui$leftReturn(Player player, Location clicked, Direction face, CallbackInfoReturnable<Boolean> cir) {
        axiomwegui$after(player);
    }

    @Inject(method = "handleBlockRightClick(Lcom/sk89q/worldedit/entity/Player;Lcom/sk89q/worldedit/util/Location;Lcom/sk89q/worldedit/util/Direction;)Z",
        at = @At("HEAD"), remap = false)
    private void axiomwegui$rightHead(Player player, Location clicked, Direction face, CallbackInfoReturnable<Boolean> cir) {
        axiomwegui$before(player);
    }

    @Inject(method = "handleBlockRightClick(Lcom/sk89q/worldedit/entity/Player;Lcom/sk89q/worldedit/util/Location;Lcom/sk89q/worldedit/util/Direction;)Z",
        at = @At("RETURN"), remap = false)
    private void axiomwegui$rightReturn(Player player, Location clicked, Direction face, CallbackInfoReturnable<Boolean> cir) {
        axiomwegui$after(player);
    }
}
