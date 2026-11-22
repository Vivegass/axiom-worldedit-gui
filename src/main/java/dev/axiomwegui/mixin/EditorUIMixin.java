package dev.axiomwegui.mixin;

import com.moulberry.axiom.editor.EditorUI;
import dev.axiomwegui.ui.WeWindows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the addon's windows in the same frame as Axiom's own windows, right before the tick-blocks modal. */
@Mixin(value = EditorUI.class, remap = false)
public abstract class EditorUIMixin {
    @Inject(
        method = "drawOverlayInternal",
        at = @At(value = "INVOKE", target = "Lcom/moulberry/axiom/editor/windows/TickBlocksModal;render()V", remap = false),
        remap = false
    )
    private static void axiomwegui$renderWindows(CallbackInfo ci) {
        WeWindows.renderAll();
    }
}
