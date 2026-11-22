package dev.axiomwegui.we;

import java.util.ArrayList;
import java.util.List;

/**
 * State of the WorldEdit command that is running on the server thread right now.
 * Set by the PlatformCommandManager mixin around {@code handleCommand}, read by the journal and the backend.
 * Server thread only.
 */
public final class CommandContext {
    private static final ThreadLocal<CommandContext> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<CommandContext> LAST = new ThreadLocal<>();

    public final String command;
    /** True while WorldEdit's chat output must go to the addon console instead of the chat. */
    public final boolean captureOutput;
    public final List<WeResult.Line> lines = new ArrayList<>();
    public WeResult.Journal journal = WeResult.Journal.NONE;
    public int journaledBlocks = 0;
    /** Filled in by the selection sync before the command runs. */
    public SelectionState selectionBefore = null;

    private CommandContext(String command, boolean captureOutput) {
        this.command = command;
        this.captureOutput = captureOutput;
    }

    public static CommandContext begin(String command, boolean captureOutput) {
        CommandContext ctx = new CommandContext(command, captureOutput);
        CURRENT.set(ctx);
        return ctx;
    }

    public static CommandContext current() {
        return CURRENT.get();
    }

    public static void end() {
        CommandContext ctx = CURRENT.get();
        CURRENT.remove();
        LAST.set(ctx);
    }

    /** The context of the command that finished last on this thread, then forgets it. */
    public static CommandContext takeLast() {
        CommandContext ctx = LAST.get();
        LAST.remove();
        return ctx;
    }

    /** Whether the next {@code handleCommand} on this thread was started by the addon (capture its output). */
    private static final ThreadLocal<Boolean> FROM_ADDON = ThreadLocal.withInitial(() -> false);

    public static void markNextFromAddon(boolean value) {
        FROM_ADDON.set(value);
    }

    public static boolean isNextFromAddon() {
        return FROM_ADDON.get();
    }
}
