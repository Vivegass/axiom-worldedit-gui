package dev.axiomwegui.we;

import dev.axiomwegui.GuiConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Recent results of commands sent by the addon, newest last. Client thread only. */
public final class Console {
    private static final Deque<WeResult> RESULTS = new ArrayDeque<>();
    private static int version = 0;

    private Console() {
    }

    public static void add(WeResult result) {
        RESULTS.addLast(result);
        while (RESULTS.size() > GuiConfig.consoleLines()) {
            RESULTS.removeFirst();
        }
        version++;
    }

    public static List<WeResult> results() {
        return new ArrayList<>(RESULTS);
    }

    public static WeResult last() {
        return RESULTS.peekLast();
    }

    /** A result got more lines. */
    public static void touch() {
        version++;
    }

    public static void clear() {
        RESULTS.clear();
        version++;
    }

    /** Changes whenever the list changes, so a window can scroll to the bottom. */
    public static int version() {
        return version;
    }
}
