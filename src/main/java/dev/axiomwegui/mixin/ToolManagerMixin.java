package dev.axiomwegui.mixin;

import com.moulberry.axiom.tools.ToolManager;
import dev.axiomwegui.brush.WeBrushTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the WorldEdit brush to Axiom's tool list, after Axiom's own tools. */
@Mixin(value = ToolManager.class, remap = false)
public abstract class ToolManagerMixin {
    @Inject(method = "initializeTools", at = @At("RETURN"), remap = false)
    private static void axiomwegui$addBrushTool(CallbackInfo ci) {
        if (!ToolManager.getTools().contains(WeBrushTool.INSTANCE)) {
            ToolManager.addTool(WeBrushTool.INSTANCE);
        }
    }
}
