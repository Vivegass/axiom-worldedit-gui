package dev.axiomwegui.mixin;

import com.moulberry.axiom.editor.windows.MainMenuBar;
import dev.axiomwegui.ui.WeMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the "WorldEdit" menu as the last entry of Axiom's main menu bar. */
@Mixin(value = MainMenuBar.class, remap = false)
public abstract class MainMenuBarMixin {
    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Limgui/moulberry92/ImGui;endMainMenuBar()V", remap = false),
        remap = false
    )
    private static void axiomwegui$addMenu(CallbackInfo ci) {
        WeMenu.render();
    }
}
