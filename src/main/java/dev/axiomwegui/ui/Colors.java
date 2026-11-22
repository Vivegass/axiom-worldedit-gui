package dev.axiomwegui.ui;

import imgui.moulberry92.ImGui;

/** Text colours used by the addon windows. */
final class Colors {
    private Colors() {
    }

    static void error(String text) {
        ImGui.pushTextWrapPos(0);
        ImGui.textColored(1.0f, 0.42f, 0.42f, 1.0f, Ui.e(text));
        ImGui.popTextWrapPos();
    }

    static void ok(String text) {
        ImGui.pushTextWrapPos(0);
        ImGui.textColored(0.45f, 0.85f, 0.55f, 1.0f, Ui.e(text));
        ImGui.popTextWrapPos();
    }

    static void accent(String text) {
        ImGui.textColored(0.58f, 0.70f, 1.0f, 1.0f, Ui.e(text));
    }

    static void warn(String text) {
        ImGui.pushTextWrapPos(0);
        ImGui.textColored(0.95f, 0.75f, 0.40f, 1.0f, Ui.e(text));
        ImGui.popTextWrapPos();
    }
}
