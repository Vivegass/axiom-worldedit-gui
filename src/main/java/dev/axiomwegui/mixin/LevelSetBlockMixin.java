package dev.axiomwegui.mixin;

import dev.axiomwegui.journal.WorldCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets {@link WorldCapture} see block changes that bypass WorldEdit (the item brush). */
@Mixin(value = Level.class, remap = false)
public abstract class LevelSetBlockMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), remap = false)
    private void axiomwegui$capture(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                    CallbackInfoReturnable<Boolean> cir) {
        WorldCapture.beforeSetBlock((Level) (Object) this, pos);
    }
}
