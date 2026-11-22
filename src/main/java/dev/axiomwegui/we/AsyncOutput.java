package dev.axiomwegui.we;

import net.minecraft.client.Minecraft;

/**
 * Some WorldEdit commands (//distr, //count, //regen, schematic listing...) finish on a worker thread and
 * print their answer after the command itself has returned. For a short time after a command sent by the
 * addon, such late messages are added to that command's result instead of the chat.
 */
public final class AsyncOutput {
    private static final long WINDOW_MS = 20_000;

    private static volatile WeResult target = null;
    private static volatile long until = 0;

    private AsyncOutput() {
    }

    /** Server thread, right after an addon command returned. */
    static void expect(WeResult result) {
        target = result;
        until = System.currentTimeMillis() + WINDOW_MS;
    }

    /** A command typed in chat ends the window, so its late output goes to the chat as usual. */
    public static void clear() {
        target = null;
    }

    /** Any thread. Returns true if the message was taken for the addon console. */
    public static boolean offer(String text, boolean error) {
        WeResult result = target;
        if (result == null || System.currentTimeMillis() > until) {
            return false;
        }
        Minecraft.getInstance().execute(() -> {
            for (String part : text.split("\n")) {
                if (!part.isBlank()) {
                    result.lines().add(new WeResult.Line(part, error));
                }
            }
            Console.touch();
        });
        return true;
    }
}
