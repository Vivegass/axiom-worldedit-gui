package dev.axiomwegui.mixin;

import com.sk89q.worldedit.coremc.internal.CoreMcPlayer;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import dev.axiomwegui.we.AsyncOutput;
import dev.axiomwegui.we.CommandContext;
import dev.axiomwegui.we.Singleplayer;
import dev.axiomwegui.we.WeResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a command sent by the addon runs, WorldEdit's messages go to the addon console instead of the chat. */
@Mixin(value = CoreMcPlayer.class, remap = false)
public abstract class CoreMcPlayerMixin {
    private boolean axiomwegui$capture(String text, boolean error) {
        CommandContext ctx = CommandContext.current();
        if (ctx == null) {
            return Singleplayer.isLocal((Actor) (Object) this) && AsyncOutput.offer(text, error);
        }
        if (!ctx.captureOutput) {
            return false;
        }
        for (String part : text.split("\n")) {
            if (!part.isBlank()) {
                ctx.lines.add(new WeResult.Line(part, error));
            }
        }
        return true;
    }

    @Inject(method = "print(Lcom/sk89q/worldedit/util/formatting/text/Component;)V", at = @At("HEAD"),
        cancellable = true, remap = false)
    private void axiomwegui$printComponent(Component component, CallbackInfo ci) {
        CommandContext ctx = CommandContext.current();
        if (ctx != null && !ctx.captureOutput) {
            return;
        }
        String text = WorldEditText.reduceToText(component, ((Actor) (Object) this).getLocale());
        if (axiomwegui$capture(text, TextColor.RED.equals(component.color()))) {
            ci.cancel();
        }
    }

    @Inject(method = "print(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$print(String msg, CallbackInfo ci) {
        if (axiomwegui$capture(msg, false)) {
            ci.cancel();
        }
    }

    @Inject(method = "printError(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$printError(String msg, CallbackInfo ci) {
        if (axiomwegui$capture(msg, true)) {
            ci.cancel();
        }
    }

    @Inject(method = "printRaw(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$printRaw(String msg, CallbackInfo ci) {
        if (axiomwegui$capture(msg, false)) {
            ci.cancel();
        }
    }

    @Inject(method = "printDebug(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void axiomwegui$printDebug(String msg, CallbackInfo ci) {
        if (axiomwegui$capture(msg, false)) {
            ci.cancel();
        }
    }
}
