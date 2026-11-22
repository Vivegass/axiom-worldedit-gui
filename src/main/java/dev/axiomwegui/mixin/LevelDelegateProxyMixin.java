package dev.axiomwegui.mixin;

import com.sk89q.worldedit.coremc.internal.CoreMcServerLevelDelegateProxy;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldEdit grows trees and features in a stand-in level that records every block into the edit, so the
 * edit can be undone. It only catches {@code setBlock}, though; features that call
 * {@code setBlockAndUpdate} (the logs of fallen trees, for one) wrote straight into the world, and undo left
 * them behind. This sends those calls into the edit as well.
 */
@Mixin(value = CoreMcServerLevelDelegateProxy.class, remap = false)
public abstract class LevelDelegateProxyMixin {
    @Shadow
    private boolean setBlock(BlockPos blockPos, BlockState blockState) {
        throw new AssertionError();
    }

    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$recordSetBlockAndUpdate(Object proxy, Method method, Object[] args,
                                                    CallbackInfoReturnable<Object> cir) {
        if (method.getName().equals("setBlockAndUpdate") && args != null && args.length == 2
            && args[0] instanceof BlockPos pos && args[1] instanceof BlockState state) {
            cir.setReturnValue(setBlock(pos, state));
        }
    }
}
