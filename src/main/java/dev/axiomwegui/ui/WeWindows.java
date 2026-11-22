package dev.axiomwegui.ui;

import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.GuiConfig;
import dev.axiomwegui.selection.SelectionSync;
import dev.axiomwegui.we.Singleplayer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * The addon's own editor windows. Axiom's {@code EditorWindowType} is an enum and cannot be extended,
 * so open/closed state lives in {@link GuiConfig}, and imgui.ini keeps position and size.
 */
public final class WeWindows {
    public static final String OPERATIONS = "operations";
    public static final String CONSOLE = "console";
    public static final String COMMANDS = "commands";
    public static final String BRUSHES = "brushes";

    private static MinecraftServer lastServer = null;

    private WeWindows() {
    }

    public static boolean isOpen(String id) {
        return GuiConfig.isWindowOpen(id);
    }

    public static void setOpen(String id, boolean open) {
        GuiConfig.setWindowOpen(id, open);
    }

    public static void toggle(String id) {
        setOpen(id, !isOpen(id));
    }

    /** Called every editor frame from EditorUI. A failing window must not take the whole editor down. */
    public static void renderAll() {
        Singleplayer.rememberLocalPlayer();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != lastServer) {
            lastServer = server;
            SelectionSync.reset();
        }
        safely(OPERATIONS, OperationsWindow::render);
        safely(CONSOLE, ConsoleWindow::render);
        safely(COMMANDS, CommandBrowserWindow::render);
        if (GuiConfig.introduceBrushesWindow()) {
            // New in this version: show the brushes window once; after that it stays as the player leaves it.
            setOpen(BRUSHES, true);
        }
        safely(BRUSHES, BrushesWindow::render);
    }

    private static void safely(String id, Runnable window) {
        try {
            window.run();
        } catch (RuntimeException e) {
            AxiomWorldEditGui.LOGGER.error("Window {} failed, closing it", id, e);
            setOpen(id, false);
        }
    }
}
