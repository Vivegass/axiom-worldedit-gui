package dev.axiomwegui.ui;

import dev.axiomwegui.Tr;
import dev.axiomwegui.brush.BrushBackend;
import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeCommand;
import dev.axiomwegui.we.WeSchema;
import imgui.moulberry92.ImGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tool Options of the WorldEdit brush tool: which brush, its settings, and an optional mask. */
public final class BrushOptions {
    private static final CommandForm.Cache FORMS = new CommandForm.Cache();
    private static final PatternField MASK = new PatternField(DirectBackend.ParseKind.MASK, null);
    private static String selected = "/brush sphere";
    private static long flashUntil = 0;

    private BrushOptions() {
    }

    public static String selectedBrush() {
        return selected;
    }

    public static void selectBrush(String path) {
        if (path != null && !path.isBlank()) {
            selected = path;
        }
    }

    /** Called when the player paints before setting a brush up. */
    public static void flashNotConfigured() {
        flashUntil = System.currentTimeMillis() + 3000;
    }

    public static void render() {
        if (WeSchema.status() != WeSchema.Status.READY) {
            ImGui.textWrapped(Ui.e(WeMenu.statusLine()));
            return;
        }
        List<WeCommand> brushes = brushCommands();
        if (brushes.isEmpty()) {
            ImGui.textDisabled(Ui.e(Tr.t("brush.none_found")));
            return;
        }
        WeCommand current = null;
        for (WeCommand brush : brushes) {
            if (brush.path().equals(selected)) {
                current = brush;
            }
        }
        if (current == null) {
            current = brushes.get(0);
            selected = current.path();
        }

        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##we_brush_type", label(current), 0)) {
            for (WeCommand brush : brushes) {
                if (ImGui.selectable(label(brush) + "##" + brush.path(), brush == current)) {
                    selected = brush.path();
                }
                if (ImGui.isItemHovered() && !brush.description().isEmpty()) {
                    ImGui.setTooltip(Ui.e(brush.description()));
                }
            }
            ImGui.endCombo();
        }

        CommandForm form = FORMS.get(current, Map.of());
        form.withRunner(line -> BrushBackend.configure(line, MASK.value()), Tr.t("brush.apply"));
        form.render();

        ImGui.separator();
        ImGui.text(Ui.e(Tr.t("brush.mask")));
        MASK.render("brush_mask", Tr.t("brush.mask_hint"));

        ImGui.separator();
        String summary = BrushBackend.summary();
        if (summary == null) {
            if (System.currentTimeMillis() < flashUntil) {
                Colors.error(Tr.t("brush.not_configured"));
            } else {
                ImGui.textDisabled(Ui.e(Tr.t("brush.not_configured")));
            }
        } else {
            Colors.ok(Tr.t("brush.active", summary));
        }
        ImGui.pushTextWrapPos(0);
        ImGui.textDisabled(Ui.e(Tr.t("brush.how_to")));
        ImGui.popTextWrapPos();
    }

    private static String label(WeCommand brush) {
        String path = brush.path();
        return path.startsWith("/brush ") ? path.substring("/brush ".length()) : path;
    }

    /** Every runnable //brush sub-command except unbinding. */
    private static List<WeCommand> brushCommands() {
        List<WeCommand> out = new ArrayList<>();
        for (WeCommand command : WeSchema.commands()) {
            if (command.path().equals("/brush")) {
                for (WeCommand child : command.children()) {
                    if (child.children().isEmpty()) {
                        if (!child.path().endsWith(" none")) {
                            out.add(child);
                        }
                    } else {
                        // //brush apply and //brush paint: their sub-commands carry the group's arguments.
                        out.addAll(child.children());
                    }
                }
            }
        }
        return out;
    }
}
