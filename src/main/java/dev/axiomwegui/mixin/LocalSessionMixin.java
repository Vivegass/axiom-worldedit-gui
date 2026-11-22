package dev.axiomwegui.mixin;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.world.item.ItemType;
import dev.axiomwegui.brush.BrushBackend;
import dev.axiomwegui.journal.Journal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two hooks into the player's WorldEdit session: edits go to the shared (Axiom) undo history, and a brush
 * set up by the addon's brush tool is kept by the addon instead of being bound to the item in hand.
 */
@Mixin(value = LocalSession.class, remap = false)
public abstract class LocalSessionMixin {
    @Inject(method = "remember", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$journal(EditSession editSession, CallbackInfo ci) {
        if (Journal.tryRecord(editSession)) {
            ci.cancel();
        }
    }

    @Inject(method = "forceBrush", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$captureBrush(ItemType item, Brush brush, String permission,
                                         CallbackInfoReturnable<BrushTool> cir) {
        if (BrushBackend.isCapturing()) {
            BrushTool tool = new BrushTool(brush, permission);
            BrushBackend.captured(tool);
            cir.setReturnValue(tool);
        }
    }
}
