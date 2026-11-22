package dev.axiomwegui.mixin;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.extension.platform.PlatformCommandManager;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.selection.SelectionSync;
import dev.axiomwegui.we.AsyncOutput;
import dev.axiomwegui.we.CommandContext;
import dev.axiomwegui.we.Singleplayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps every WorldEdit command of the local player, typed in chat or sent by the addon:
 * the Axiom selection goes in before it, and a changed WorldEdit selection comes back after it.
 */
@Mixin(value = PlatformCommandManager.class, remap = false)
public abstract class PlatformCommandManagerMixin {
    @Inject(method = "handleCommand", at = @At("HEAD"), remap = false)
    private void axiomwegui$before(CommandEvent event, CallbackInfo ci) {
        boolean fromAddon = CommandContext.isNextFromAddon();
        CommandContext.markNextFromAddon(false);
        CommandContext ctx = CommandContext.begin(event.getArguments(), fromAddon);
        if (!fromAddon && Singleplayer.isLocal(event.getActor())) {
            AsyncOutput.clear();
        }
        try {
            if (Singleplayer.isLocal(event.getActor()) && event.getActor() instanceof Player player) {
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
                SelectionSync.beforeCommand(session, player.getWorld(), ctx);
            }
        } catch (RuntimeException e) {
            AxiomWorldEditGui.LOGGER.warn("Could not give the Axiom selection to WorldEdit", e);
        }
    }

    @Inject(method = "handleCommand", at = @At("RETURN"), remap = false)
    private void axiomwegui$after(CommandEvent event, CallbackInfo ci) {
        CommandContext ctx = CommandContext.current();
        try {
            if (ctx != null && ctx.selectionBefore != null && event.getActor() instanceof Player player) {
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
                SelectionSync.afterCommand(session, player.getWorld(), ctx);
            }
        } catch (RuntimeException e) {
            AxiomWorldEditGui.LOGGER.warn("Could not give WorldEdit's selection back to Axiom", e);
        } finally {
            CommandContext.end();
        }
    }
}
