package dev.axiomwegui.mixin;

import com.moulberry.axiom.mask.MaskElement;
import com.moulberry.axiom.mask.MaskManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the mask set in Axiom's Tool Masks window, whether or not that window is open. */
@Mixin(value = MaskManager.class, remap = false)
public interface MaskManagerAccessor {
    @Accessor(value = "configuredMask", remap = false)
    static MaskElement axiomwegui$getConfiguredMask() {
        throw new AssertionError();
    }
}
