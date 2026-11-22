package dev.axiomwegui.mixin.compat;

import com.moulberry.axiom.editor.EditorUI;
import dev.axiomwegui.GuiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldEditCUI draws WorldEdit's selection on top of Axiom's. While the Axiom editor is open the addon keeps
 * the two selections equal and Axiom already draws it, so WorldEditCUI's copy is hidden (can be turned off).
 * Applied only if WorldEditCUI is installed.
 */
@Pseudo
@Mixin(targets = "org.enginehub.worldeditcui.event.listeners.CUIListenerWorldRender", remap = false)
public abstract class WorldEditCuiRenderMixin {
    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void axiomwegui$hideInEditor(CallbackInfo ci) {
        if (GuiConfig.isHideCuiInEditor() && EditorUI.isActive()) {
            ci.cancel();
        }
    }
}
