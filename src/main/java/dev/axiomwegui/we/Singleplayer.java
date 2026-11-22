package dev.axiomwegui.we;

import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.entity.Player;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** Checks that decide whether the addon acts on a WorldEdit call. Safe to call from both threads. */
public final class Singleplayer {
    private static volatile UUID localPlayer = null;

    private Singleplayer() {
    }

    /** Called on the client thread whenever the editor draws, so the server thread knows who the local player is. */
    public static void rememberLocalPlayer() {
        Minecraft mc = Minecraft.getInstance();
        localPlayer = mc.player == null ? null : mc.player.getUUID();
    }

    public static UUID localPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.isSameThread()) {
            localPlayer = mc.player.getUUID();
        }
        return localPlayer;
    }

    /** True in a singleplayer world that is not opened to LAN. */
    public static boolean active() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && !server.isPublished();
    }

    /** True if the WorldEdit actor is the player sitting at this computer. */
    public static boolean isLocal(Actor actor) {
        UUID local = localPlayer;
        return active() && local != null && actor instanceof Player player && local.equals(player.getUniqueId());
    }
}
