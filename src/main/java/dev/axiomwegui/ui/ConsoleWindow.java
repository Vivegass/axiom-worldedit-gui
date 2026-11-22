package dev.axiomwegui.ui;

import dev.axiomwegui.GuiConfig;
import dev.axiomwegui.Tr;
import dev.axiomwegui.we.Console;
import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeResult;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiCond;
import imgui.moulberry92.type.ImBoolean;
import imgui.moulberry92.type.ImString;
import java.util.List;

/** WorldEdit's answers to the commands the addon sent, plus a line to type any command. */
public final class ConsoleWindow {
    private static final ImBoolean OPEN = new ImBoolean(true);
    private static final ImString INPUT = new ImString(1024);
    private static int seenVersion = -1;

    private ConsoleWindow() {
    }

    static void render() {
        if (!WeWindows.isOpen(WeWindows.CONSOLE)) {
            return;
        }
        OPEN.set(true);
        ImGui.setNextWindowSize(520, 360, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(380, 200, 100000, 100000);
        if (ImGui.begin(Tr.t("window.console") + "###axiom_we_console", OPEN)) {
            renderContents();
        }
        ImGui.end();
        if (!OPEN.get()) {
            WeWindows.setOpen(WeWindows.CONSOLE, false);
        }
    }

    private static void renderContents() {
        if (ImGui.button(Tr.t("console.we_undo") + "##we_undo")) {
            DirectBackend.run("//undo");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(Tr.t("console.we_undo.tooltip")));
        }
        ImGui.sameLine();
        if (ImGui.button(Tr.t("console.we_redo") + "##we_redo")) {
            DirectBackend.run("//redo");
        }
        ImGui.sameLine();
        if (ImGui.button(Tr.t("console.clear") + "##clear")) {
            Console.clear();
        }
        ImGui.sameLine();
        boolean journal = GuiConfig.isJournalEnabled();
        if (ImGui.checkbox(Tr.t("console.journal_toggle") + "##journal", journal)) {
            GuiConfig.setJournalEnabled(!journal);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(Tr.t("console.journal_toggle.tooltip")));
        }

        float inputHeight = ImGui.getFrameHeight() + 8;
        if (ImGui.beginChild("##console_log", 0, -inputHeight, true)) {
            List<WeResult> results = Console.results();
            if (results.isEmpty()) {
                ImGui.textDisabled(Ui.e(Tr.t("console.empty")));
            }
            for (WeResult result : results) {
                ResultView.render(result, true);
                ImGui.separator();
            }
            if (seenVersion != Console.version()) {
                seenVersion = Console.version();
                ImGui.setScrollHereY(1.0f);
            }
        }
        ImGui.endChild();

        ImGui.setNextItemWidth(-1);
        if (ImGui.inputTextWithHint("##console_input", Tr.t("console.input_hint"), INPUT,
            imgui.moulberry92.flag.ImGuiInputTextFlags.EnterReturnsTrue)) {
            String line = INPUT.get().trim();
            if (!line.isEmpty()) {
                DirectBackend.run(line);
                INPUT.set("");
            }
            ImGui.setKeyboardFocusHere(-1);
        }
    }
}
