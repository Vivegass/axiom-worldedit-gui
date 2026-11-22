package dev.axiomwegui.ui;

import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.GuiConfig;
import dev.axiomwegui.Tr;
import dev.axiomwegui.journal.AxiomHistory;
import dev.axiomwegui.we.WeSchema;
import imgui.moulberry92.ImGui;

/** The "WorldEdit" menu at the end of Axiom's main menu bar. */
public final class WeMenu {
    private WeMenu() {
    }

    public static void render() {
        if (!ImGui.beginMenu(Tr.t("menu") + "##axiom_we_menu")) {
            return;
        }
        try {
            boolean ready = WeSchema.status() == WeSchema.Status.READY;
            item("menu.brushes", WeWindows.BRUSHES, ready);
            item("menu.operations", WeWindows.OPERATIONS, ready);
            item("menu.console", WeWindows.CONSOLE, ready);
            item("menu.commands", WeWindows.COMMANDS, ready);
            ImGui.separator();
            boolean journal = GuiConfig.isJournalEnabled();
            if (ImGui.menuItem(Tr.t("menu.journal") + "##axiom_we_menu_journal", "", journal, ready)) {
                GuiConfig.setJournalEnabled(!journal);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(Tr.t("console.journal_toggle.tooltip")));
            }
            boolean hideCui = GuiConfig.isHideCuiInEditor();
            if (ImGui.menuItem(Tr.t("menu.hide_cui") + "##axiom_we_menu_cui", "", hideCui, true)) {
                GuiConfig.setHideCuiInEditor(!hideCui);
            }
            boolean carry = GuiConfig.isBrushCarryOver();
            if (ImGui.menuItem(Tr.t("menu.brush_carry") + "##axiom_we_menu_carry", "", carry, true)) {
                GuiConfig.setBrushCarryOver(!carry);
            }
            ImGui.separator();
            ImGui.textDisabled(Ui.e(statusLine()));
            if (!AxiomHistory.isAvailable()) {
                Colors.warn(Tr.t("status.journal_broken"));
            }
            ImGui.textDisabled(Ui.e(Tr.t("menu.about", AxiomWorldEditGui.version())));
        } finally {
            ImGui.endMenu();
        }
    }

    private static void item(String key, String window, boolean enabled) {
        if (ImGui.menuItem(Tr.t(key) + "##axiom_we_" + window, "", WeWindows.isOpen(window), enabled)) {
            WeWindows.toggle(window);
        }
    }

    static String statusLine() {
        return switch (WeSchema.status()) {
            case MULTIPLAYER -> Tr.t("status.multiplayer");
            case STARTING -> Tr.t("status.starting");
            case READY -> Tr.t("status.ready", WeSchema.worldEditVersion());
        };
    }
}
