package dev.axiomwegui.we;

import java.util.List;

/**
 * What one WorldEdit command produced.
 *
 * @param command   the command line as sent, e.g. {@code //set stone}
 * @param lines     WorldEdit's messages, in order; late messages of asynchronous commands are appended
 *                  on the client thread
 * @param journal   how the change was recorded for undo
 * @param blocks    blocks written to the shared undo history (0 if none)
 */
public record WeResult(String command, List<Line> lines, Journal journal, int blocks) {

    public record Line(String text, boolean error) {
    }

    public enum Journal {
        /** Nothing was changed in the world. */
        NONE,
        /** The change is in Axiom's history: Ctrl+Z undoes it. */
        AXIOM,
        /** The change touched entities or biomes and stayed in WorldEdit's own history (//undo). */
        WORLDEDIT
    }

    public boolean hasError() {
        for (Line line : lines) {
            if (line.error()) {
                return true;
            }
        }
        return false;
    }
}
