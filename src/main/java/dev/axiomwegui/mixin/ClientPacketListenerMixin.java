package dev.axiomwegui.mixin;

import dev.axiomwegui.selection.SelectionSync;
import dev.axiomwegui.we.Singleplayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A WorldEdit command typed in chat also works on the current Axiom selection. */
@Mixin(value = ClientPacketListener.class, remap = false)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"), remap = false)
    private void axiomwegui$publishSelection(String command, CallbackInfo ci) {
        if (Singleplayer.active()) {
            Singleplayer.rememberLocalPlayer();
            SelectionSync.publish();
        }
    }
}
