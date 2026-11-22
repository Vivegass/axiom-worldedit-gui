package dev.axiomwegui.ui;

import dev.axiomwegui.Tr;
import dev.axiomwegui.we.ClipboardBridge;
import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeCommand;
import dev.axiomwegui.we.WeSchema;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.flag.ImGuiCond;
import imgui.moulberry92.type.ImBoolean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main addon window: the WorldEdit operations that Axiom does not have, grouped by task, each as a
 * form. Every other command is in the command reference.
 */
public final class OperationsWindow {
    private static final ImBoolean OPEN = new ImBoolean(true);
    private static final CommandForm.Cache FORMS = new CommandForm.Cache();

    /** Group key in the lang file, then the command paths in it. */
    private record Group(String key, List<String> paths) {
    }

    private static final List<Group> GROUPS = List.of(
        new Group("terrain", List.of("//naturalize", "//overlay", "//forest", "//flora", "/forestgen", "/pumpkins",
            "/snow", "/thaw", "/green", "/fixwater", "/fixlava", "//smooth", "//snowsmooth")),
        new Group("shapes", List.of("//generate", "//deform", "//line", "//curve", "//center", "//revolve",
            "//hollow", "//walls", "//faces")),
        new Group("world", List.of("//regen", "//feature", "//structure", "//generatebiome", "//setbiome",
            "//replacebiome")),
        new Group("selection", List.of("//trim", "//chunk", "//expand", "//contract", "//shift", "//outset",
            "//inset", "//size", "//count", "//distr")),
        new Group("entities", List.of("/butcher", "/remove", "/ex")),
        new Group("session", List.of("//gmask", "//perf", "//limit", "//timeout", "//reorder"))
    );

    private OperationsWindow() {
    }

    static void render() {
        if (!WeWindows.isOpen(WeWindows.OPERATIONS)) {
            return;
        }
        OPEN.set(true);
        ImGui.setNextWindowSize(460, 680, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(380, 200, 100000, 100000);
        if (ImGui.begin(Tr.t("window.operations") + "###axiom_we_operations", OPEN)) {
            renderContents();
        }
        ImGui.end();
        if (!OPEN.get()) {
            WeWindows.setOpen(WeWindows.OPERATIONS, false);
        }
    }

    private static void renderContents() {
        if (WeSchema.status() != WeSchema.Status.READY) {
            ImGui.textWrapped(Ui.e(WeMenu.statusLine()));
            return;
        }
        SelectionInfo.render();
        ImGui.separator();
        Map<String, WeCommand> byPath = index(WeSchema.commands());
        renderClipboard();
        for (Group group : GROUPS) {
            if (!ImGui.collapsingHeader(Tr.t("group." + group.key()) + "##grp_" + group.key())) {
                continue;
            }
            ImGui.indent();
            if (group.key().equals("selection")) {
                renderShapeButtons();
            }
            if (group.key().equals("shapes") && ImGui.treeNode("op_expr_help", Ui.e(Tr.t("expr.help_title")))) {
                ImGui.pushTextWrapPos(0);
                ImGui.textDisabled(Ui.e(Tr.t("expr.help")));
                ImGui.popTextWrapPos();
                ImGui.treePop();
            }
            for (String path : group.paths()) {
                WeCommand command = byPath.get(path);
                if (command == null) {
                    continue;
                }
                if (ImGui.treeNode("op" + path, Ui.e(path + "  " + shortDescription(command)))) {
                    FORMS.get(command, Presets.forCommand(path)).render();
                    ImGui.treePop();
                }
            }
            ImGui.unindent();
        }
        if (ImGui.collapsingHeader(Tr.t("group.schematics") + "##grp_schematics")) {
            ImGui.indent();
            FilesPanel.renderSchematics();
            ImGui.unindent();
        }
        if (ImGui.collapsingHeader(Tr.t("group.scripts") + "##grp_scripts")) {
            ImGui.indent();
            FilesPanel.renderScripts();
            ImGui.unindent();
        }
    }

    private static String clipboardMessage = null;
    private static String shapeMessage = null;

    /** Moving the clipboard between Axiom and WorldEdit. */
    private static void renderClipboard() {
        if (!ImGui.collapsingHeader(Tr.t("group.clipboard") + "##grp_clipboard")) {
            return;
        }
        ImGui.indent();
        if (ImGui.button(Tr.t("clipboard.to_worldedit") + "##clip_to_we")) {
            clipboardMessage = Tr.t("form.running");
            ClipboardBridge.axiomToWorldEdit().thenAccept(m -> clipboardMessage = m);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(Tr.t("clipboard.to_worldedit.tooltip")));
        }
        ImGui.sameLine();
        if (ImGui.button(Tr.t("clipboard.to_axiom") + "##clip_to_axiom")) {
            clipboardMessage = Tr.t("form.running");
            ClipboardBridge.worldEditToAxiom().thenAccept(m -> clipboardMessage = m);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(Tr.t("clipboard.to_axiom.tooltip")));
        }
        if (clipboardMessage != null) {
            ImGui.textDisabled(Ui.e(clipboardMessage));
        }
        ImGui.unindent();
    }

    /** WorldEdit turns the current box into another shape with //sel; the shape comes back as the Axiom selection. */
    private static void renderShapeButtons() {
        ImGui.textDisabled(Ui.e(Tr.t("shape.title")));
        String[][] shapes = {{"ellipsoid", "shape.ellipsoid"}, {"cyl", "shape.cylinder"}, {"sphere", "shape.sphere"},
            {"cuboid", "shape.box"}};
        for (int i = 0; i < shapes.length; i++) {
            if (i > 0) {
                ImGui.sameLine();
            }
            if (ImGui.button(Tr.t(shapes[i][1]) + "##shape_" + shapes[i][0])) {
                String type = shapes[i][0];
                DirectBackend.run("//sel " + type).thenAccept(r -> shapeMessage = r.hasError()
                    ? r.lines().get(0).text() : Tr.t("shape.done"));
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(Tr.t("shape.box.tooltip")));
        }
        if (shapeMessage != null) {
            ImGui.textDisabled(Ui.e(shapeMessage));
        }
        ImGui.separator();
    }

    private static String shortDescription(WeCommand command) {
        String d = command.description();
        return d.length() <= 48 ? d : d.substring(0, 45) + "...";
    }

    private static List<WeCommand> lastIndexed = null;
    private static Map<String, WeCommand> lastIndex = Map.of();

    /** Path and alias lookup, including sub-commands. */
    static Map<String, WeCommand> index(List<WeCommand> commands) {
        if (commands == lastIndexed) {
            return lastIndex;
        }
        Map<String, WeCommand> map = new HashMap<>();
        for (WeCommand command : commands) {
            add(map, command);
        }
        lastIndexed = commands;
        lastIndex = map;
        return map;
    }

    private static void add(Map<String, WeCommand> map, WeCommand command) {
        map.putIfAbsent(command.path(), command);
        String parent = command.path().substring(0, command.path().length() - lastSegment(command.path()).length());
        for (String alias : command.aliases()) {
            map.putIfAbsent(parent.isEmpty() ? "/" + alias : parent + alias, command);
        }
        for (WeCommand child : command.children()) {
            add(map, child);
        }
    }

    private static String lastSegment(String path) {
        int space = path.lastIndexOf(' ');
        if (space >= 0) {
            return path.substring(space + 1);
        }
        return path.substring(1);
    }
}
