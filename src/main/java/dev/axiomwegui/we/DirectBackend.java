package dev.axiomwegui.we;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.event.platform.CommandSuggestionEvent;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.PlatformCommandManager;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.Tr;
import dev.axiomwegui.selection.SelectionSync;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Runs WorldEdit on the integrated server, the same way a typed command runs, but with the output
 * collected for the addon console. All public methods are called on the client thread and complete
 * their futures on the client thread.
 */
public final class DirectBackend {
    private DirectBackend() {
    }

    public enum ParseKind { PATTERN, MASK }

    /** Code that runs on the server thread right around one command. */
    public interface ServerHook {
        void before(Player actor, LocalSession session);

        void after(Player actor, LocalSession session, WeResult result);
    }

    /** Runs one command line such as {@code //set stone} or {@code /butcher -a 30}. */
    public static CompletableFuture<WeResult> run(String commandLine) {
        return run(commandLine, null);
    }

    /** Like {@link #run(String)}, with a hook around the command on the server thread. */
    public static CompletableFuture<WeResult> run(String commandLine, ServerHook hook) {
        String command = commandLine.trim();
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        String line = command;
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<WeResult> future = new CompletableFuture<>();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            future.complete(error(line, Tr.t("status.multiplayer")));
            return future;
        }
        Singleplayer.rememberLocalPlayer();
        SelectionSync.publish();
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            WeResult result;
            try {
                result = runOnServer(server, uuid, line, hook);
            } catch (Throwable t) {
                AxiomWorldEditGui.LOGGER.error("WorldEdit command {} failed", line, t);
                result = error(line, t.toString());
            }
            WeResult done = result;
            mc.execute(() -> {
                Console.add(done);
                future.complete(done);
            });
        });
        return future;
    }

    private static WeResult runOnServer(MinecraftServer server, UUID uuid, String line, ServerHook hook) {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
        if (serverPlayer == null) {
            return error(line, Tr.t("status.no_player"));
        }
        Player actor = FabricAdapter.get().fromNativePlayer(serverPlayer);
        PlatformCommandManager commands = WorldEdit.getInstance().getPlatformManager().getPlatformCommandManager();
        String name = line.substring(1).split(" ", 2)[0];
        if (!commands.getCommandManager().containsCommand(name)) {
            return error(line, Tr.t("console.unknown_command", line.split(" ", 2)[0]));
        }
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        if (hook != null) {
            hook.before(actor, session);
        }
        CommandContext.markNextFromAddon(true);
        try {
            commands.handleCommand(new CommandEvent(actor, line));
        } finally {
            CommandContext.markNextFromAddon(false);
        }
        CommandContext ctx = CommandContext.takeLast();
        WeResult result = ctx == null
            ? new WeResult(line, new ArrayList<>(), WeResult.Journal.NONE, 0)
            : new WeResult(line, new ArrayList<>(ctx.lines), ctx.journal, ctx.journaledBlocks);
        AsyncOutput.expect(result);
        if (hook != null) {
            hook.after(actor, session, result);
        }
        return result;
    }

    private static WeResult error(String line, String message) {
        List<WeResult.Line> lines = new ArrayList<>();
        lines.add(new WeResult.Line(message, true));
        return new WeResult(line, lines, WeResult.Journal.NONE, 0);
    }

    /**
     * Checks a pattern or mask the way WorldEdit would read it. The future gets null if it is valid,
     * or WorldEdit's error message.
     */
    public static CompletableFuture<String> validate(ParseKind kind, String input) {
        return onServer(actor -> {
            if (input.isBlank()) {
                return null;
            }
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
            ParserContext context = new ParserContext();
            context.setActor(actor);
            context.setWorld(actor.getWorld());
            context.setExtent(actor.getWorld());
            context.setSession(session);
            context.setRestricted(true);
            try {
                if (kind == ParseKind.PATTERN) {
                    WorldEdit.getInstance().getPatternFactory().parseFromInput(input, context);
                } else {
                    WorldEdit.getInstance().getMaskFactory().parseFromInput(input, context);
                }
                return null;
            } catch (InputParseException e) {
                return WorldEditText.reduceToText(e.getRichMessage(), actor.getLocale());
            } catch (RuntimeException e) {
                return e.getMessage() == null ? e.toString() : e.getMessage();
            }
        }, "");
    }

    /** WorldEdit's own completions for a partly typed command line, e.g. {@code //set ston}. */
    public static CompletableFuture<List<String>> suggest(String partialLine) {
        return onServer(actor -> {
            CommandSuggestionEvent event = new CommandSuggestionEvent(actor, partialLine);
            WorldEdit.getInstance().getPlatformManager().getPlatformCommandManager().handleCommandSuggestion(event);
            List<String> out = new ArrayList<>();
            for (Substring s : event.getSuggestions()) {
                out.add(s.getSubstring());
            }
            return out;
        }, List.of());
    }

    private static <T> CompletableFuture<T> onServer(Function<Player, T> body, T fallback) {
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture<T> future = new CompletableFuture<>();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            future.complete(fallback);
            return future;
        }
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            T value = fallback;
            try {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
                if (serverPlayer != null) {
                    value = body.apply(FabricAdapter.get().fromNativePlayer(serverPlayer));
                }
            } catch (Throwable t) {
                AxiomWorldEditGui.LOGGER.warn("WorldEdit query failed", t);
            }
            T done = value;
            mc.execute(() -> future.complete(done));
        });
        return future;
    }
}
