package dev.axiomwegui.ui;

import dev.axiomwegui.Tr;
import dev.axiomwegui.we.WeCommand;
import dev.axiomwegui.we.WeParam;
import dev.axiomwegui.we.WeSchema;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiCond;
import imgui.moulberry92.type.ImBoolean;
import imgui.moulberry92.type.ImString;
import java.util.List;
import java.util.Locale;

/**
 * Every WorldEdit command with its arguments and flags, searchable, each with a form to run it.
 */
public final class CommandBrowserWindow {
    private static final ImBoolean OPEN = new ImBoolean(true);
    private static final ImString FILTER = new ImString(128);
    private static final CommandForm.Cache FORMS = new CommandForm.Cache();

    private CommandBrowserWindow() {
    }

    static void render() {
        if (!WeWindows.isOpen(WeWindows.COMMANDS)) {
            return;
        }
        OPEN.set(true);
        ImGui.setNextWindowSize(560, 640, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(380, 200, 100000, 100000);
        if (ImGui.begin(Tr.t("window.commands") + "###axiom_we_commands", OPEN)) {
            renderContents();
        }
        ImGui.end();
        if (!OPEN.get()) {
            WeWindows.setOpen(WeWindows.COMMANDS, false);
        }
    }

    private static void renderContents() {
        if (WeSchema.status() != WeSchema.Status.READY) {
            ImGui.textWrapped(Ui.e(WeMenu.statusLine()));
            return;
        }
        List<WeCommand> commands = WeSchema.commands();

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##axiom_we_filter", Tr.t("commands.filter"), FILTER);
        String query = FILTER.get().trim().toLowerCase(Locale.ROOT);

        int total = 0;
        int shown = 0;
        for (WeCommand command : commands) {
            total += command.leafCount();
            shown += command.matchingLeafCount(query);
        }
        ImGui.textDisabled(Ui.e(Tr.t("commands.count", shown, total)));
        ImGui.separator();

        if (ImGui.beginChild("##axiom_we_command_list")) {
            if (shown == 0) {
                ImGui.textDisabled(Ui.e(Tr.t("commands.none")));
            }
            for (WeCommand command : commands) {
                if (command.matches(query)) {
                    renderCommand(command, query);
                }
            }
        }
        ImGui.endChild();
    }

    private static void renderCommand(WeCommand command, String query) {
        boolean group = !command.children().isEmpty();
        String label = group
            ? command.path() + "  (" + command.leafCount() + ")"
            : command.path();
        if (!ImGui.treeNode(command.path(), Ui.e(label))) {
            if (ImGui.isItemHovered() && !command.description().isEmpty()) {
                ImGui.setTooltip(Ui.e(command.description()));
            }
            return;
        }
        try {
            if (!command.aliases().isEmpty()) {
                ImGui.textDisabled(Ui.e(Tr.t("commands.aliases", String.join(", ", command.aliases()))));
            }
            if (group) {
                if (!command.description().isEmpty()) {
                    ImGui.textWrapped(Ui.e(command.description()));
                }
                if (!command.params().isEmpty()) {
                    ImGui.textDisabled(Ui.e(usage(command)));
                    for (WeParam param : command.params()) {
                        renderParam(param);
                    }
                }
            } else {
                ImGui.textDisabled(Ui.e(usage(command)));
                FORMS.get(command, Presets.forCommand(command.path())).render();
                ImGui.separator();
            }
            // When the group itself matches, show all of its sub-commands; otherwise only the matching ones.
            String childQuery = command.matchesSelf(query) ? "" : query;
            for (WeCommand child : command.children()) {
                if (child.matches(childQuery)) {
                    renderCommand(child, childQuery);
                }
            }
        } finally {
            ImGui.treePop();
        }
    }

    private static void renderParam(WeParam param) {
        StringBuilder line = new StringBuilder("  ").append(param.name());
        if (param.kind() != WeParam.Kind.SWITCH) {
            line.append(" : ").append(param.type());
            if (!param.qualifier().isEmpty()) {
                line.append(" @").append(param.qualifier());
            }
        }
        if (!param.defaults().isEmpty()) {
            line.append(" = ").append(String.join(",", param.defaults()));
        }
        if (param.required()) {
            line.append("  ").append(Tr.t("param.required"));
        }
        ImGui.bulletText(Ui.e(line.toString()));
        if (!param.description().isEmpty() && ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(param.description()));
        }
    }

    /** One-line usage like {@code //line <pattern> [thickness] -h}. */
    private static String usage(WeCommand command) {
        StringBuilder sb = new StringBuilder(command.path());
        for (WeParam p : command.params()) {
            sb.append(' ');
            switch (p.kind()) {
                case ARGUMENT -> sb.append(p.required() ? "<" : "[").append(p.name()).append(p.required() ? ">" : "]");
                case SWITCH -> sb.append('[').append(p.name()).append(']');
                case VALUE_FLAG -> sb.append('[').append(p.name()).append(" <").append(p.type()).append(">]");
            }
        }
        return sb.toString();
    }
}
