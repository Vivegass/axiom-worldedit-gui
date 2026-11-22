package dev.axiomwegui.ui;

import dev.axiomwegui.Tr;
import dev.axiomwegui.we.WeResult;
import imgui.moulberry92.ImGui;

/** Draws one command result: WorldEdit's messages and where the change can be undone. */
final class ResultView {
    private ResultView() {
    }

    static void render(WeResult result, boolean showCommand) {
        if (showCommand) {
            Colors.accent(result.command());
        }
        for (WeResult.Line line : result.lines()) {
            if (line.error()) {
                Colors.error(line.text());
            } else {
                ImGui.pushTextWrapPos(0);
                ImGui.text(Ui.e(line.text()));
                ImGui.popTextWrapPos();
            }
        }
        switch (result.journal()) {
            case AXIOM -> ImGui.textDisabled(Ui.e(Tr.t("console.journal.axiom", result.blocks())));
            case WORLDEDIT -> Colors.warn(Tr.t("console.journal.worldedit"));
            case NONE -> {
            }
        }
    }
}
