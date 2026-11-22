package dev.axiomwegui.ui;

import com.moulberry.axiom.brush_shapes.BrushShape;
import com.moulberry.axiom.brush_shapes.CubeBrushShape;
import com.moulberry.axiom.brush_shapes.CylinderBrushShape;
import com.moulberry.axiom.brush_shapes.SphereBrushShape;
import com.sk89q.worldedit.WorldEdit;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.GuiConfig;
import dev.axiomwegui.Tr;
import dev.axiomwegui.brush.BrushBackend;
import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeCommand;
import dev.axiomwegui.we.WeResult;
import dev.axiomwegui.we.WeSchema;
import imgui.moulberry92.ImGui;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool Options of the WorldEdit brush tool: which brush, its settings, and an optional mask.
 * Settings apply by themselves a moment after they change; there is no apply button.
 */
public final class BrushOptions {
    private static final CommandForm.Cache FORMS = new CommandForm.Cache();
    /** Forms that already got the active block as their default pattern. */
    private static final Set<CommandForm> SEEDED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final PatternField MASK = new PatternField(DirectBackend.ParseKind.MASK, null);
    private static final long APPLY_DELAY_MS = 250;
    /** Radius the "raise limit" button writes into WorldEdit's configuration. */
    private static final int RAISED_LIMIT = 64;

    private static String selected = "/brush sphere";
    private static long flashUntil = 0;

    private static String appliedKey = null;
    private static String pendingKey = null;
    private static long pendingSince = 0;
    private static CompletableFuture<WeResult> applying = null;
    private static WeResult lastResult = null;
    private static String limitMessage = null;

    /** The form of the brush that is selected right now; null before the first draw. */
    private static CommandForm currentForm = null;
    private static WeCommand currentBrush = null;

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

    // ------------------------------------------------------------------ brush families
    // A family is one brush as the player sees it: "forest" covers /brush forest, /brush apply ... forest
    // and /brush paint ... forest.

    private static final String DEFAULT_BRUSH = "/brush sphere";
    /** The variant last used per family, so switching back keeps the mode. */
    private static final Map<String, String> LAST_VARIANT = new java.util.HashMap<>();

    private static WeCommand find(List<WeCommand> brushes, String path) {
        for (WeCommand brush : brushes) {
            if (brush.path().equals(path)) {
                return brush;
            }
        }
        return null;
    }

    /** "" for a plain brush, "apply" or "paint" for the variants that act through a shape. */
    static String modeOf(WeCommand brush) {
        String rest = label(brush);
        if (rest.startsWith("apply ")) {
            return "apply";
        }
        if (rest.startsWith("paint ")) {
            return "paint";
        }
        return "";
    }

    static String familyOf(WeCommand brush) {
        String rest = label(brush);
        return modeOf(brush).isEmpty() ? rest : rest.substring(6);
    }

    /** Every brush family, in WorldEdit's order, empty until WorldEdit's commands are read. */
    public static List<String> families() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (WeCommand brush : brushCommands()) {
            out.add(familyOf(brush));
        }
        return List.copyOf(out);
    }

    /** The variants of a family: plain first, then apply, then paint. */
    static List<WeCommand> variants(String family) {
        List<WeCommand> out = new ArrayList<>();
        for (String mode : new String[] {"", "apply", "paint"}) {
            for (WeCommand brush : brushCommands()) {
                if (familyOf(brush).equals(family) && modeOf(brush).equals(mode)) {
                    out.add(brush);
                }
            }
        }
        return out;
    }

    /** The command whose description stands for the family. */
    public static WeCommand describe(String family) {
        List<WeCommand> variants = variants(family);
        return variants.isEmpty() ? null : variants.get(0);
    }

    public static String currentFamily() {
        WeCommand brush = find(brushCommands(), selected);
        return brush == null ? "sphere" : familyOf(brush);
    }

    /** Selects a family in the mode it was last used in, or its plain variant. */
    public static void selectFamily(String family) {
        List<WeCommand> variants = variants(family);
        if (variants.isEmpty()) {
            return;
        }
        String last = LAST_VARIANT.get(family);
        selected = last != null && find(variants, last) != null ? last : variants.get(0).path();
    }

    /** Called when the player paints before a brush is set up. */
    public static void flashNotConfigured() {
        flashUntil = System.currentTimeMillis() + 3000;
    }

    // ------------------------------------------------------------------ radius and shape for the tool

    private static String radiusParam() {
        return currentForm == null ? null : radiusParam(currentForm);
    }

    private static String radiusParam(CommandForm form) {
        for (String name : new String[] {"radius", "brushSize", "size"}) {
            if (form.has(name)) {
                return name;
            }
        }
        return null;
    }

    /** Switching brushes keeps the shape and radius the player already set. */
    private static void carryOver(CommandForm from, CommandForm to) {
        String fromRadius = radiusParam(from);
        String toRadius = radiusParam(to);
        if (fromRadius != null && toRadius != null) {
            String value = from.valueOrDefault(fromRadius).trim();
            if (!value.isEmpty()) {
                to.setValue(toRadius, value);
            }
        }
        if (to.has("shape")) {
            String value = from.has("shape") ? from.valueOrDefault("shape").trim() : impliedShape(from);
            if (value != null && !value.isEmpty()) {
                to.setValue("shape", value);
            }
        }
    }

    /** The sphere and cylinder brushes have no shape setting; their shape is in their name. */
    private static String impliedShape(CommandForm form) {
        return switch (form.command().name()) {
            case "sphere" -> "sphere";
            case "cylinder", "cyl" -> "cyl";
            default -> null;
        };
    }

    /** The brush radius from the form, or -1 if this brush has none (e.g. the clipboard brush). */
    public static double radius() {
        String name = radiusParam();
        if (name == null) {
            return -1;
        }
        try {
            return Double.parseDouble(currentForm.valueOrDefault(name).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Sets the radius field; the change applies to the brush by itself. */
    public static void setRadius(int radius) {
        String name = radiusParam();
        if (name != null) {
            currentForm.setValue(name, Integer.toString(radius));
        }
    }

    /** WorldEdit's max-brush-radius setting (unlimited counts as 256). */
    public static int maxRadius() {
        try {
            int max = WorldEdit.getInstance().getConfiguration().maxBrushRadius;
            return max <= 0 ? 256 : max;
        } catch (RuntimeException e) {
            return 256;
        }
    }

    private static BrushShape cachedShape = null;
    private static String cachedShapeKey = null;

    /** The shape the brush covers at its current radius, for the preview; null if it has no radius. */
    public static BrushShape previewShape() {
        double r = radius();
        if (r < 0 || currentForm == null || currentBrush == null) {
            return null;
        }
        int radius = Math.max(0, (int) Math.round(r));
        String type;
        int height = 1;
        if (currentForm.has("shape")) {
            String shape = currentForm.valueOrDefault("shape").toLowerCase(Locale.ROOT);
            type = shape.startsWith("cub") ? "cube" : shape.startsWith("cyl") ? "cyl" : "sphere";
        } else if (currentBrush.name().equals("cylinder") || currentBrush.name().equals("cyl")) {
            type = "cyl";
            try {
                height = Math.max(1, Integer.parseInt(currentForm.valueOrDefault("height").trim()));
            } catch (NumberFormatException e) {
                height = 1;
            }
        } else {
            type = "sphere";
        }
        String key = type + ":" + radius + ":" + height;
        if (!key.equals(cachedShapeKey)) {
            cachedShapeKey = key;
            cachedShape = switch (type) {
                case "cube" -> CubeBrushShape.create(radius);
                case "cyl" -> CylinderBrushShape.create(radius, height);
                default -> SphereBrushShape.create(radius);
            };
        }
        return cachedShape;
    }

    // ------------------------------------------------------------------ window

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
        WeCommand current = find(brushes, selected);
        if (current == null) {
            current = find(brushes, DEFAULT_BRUSH);
        }
        if (current == null) {
            current = brushes.get(0);
        }
        selected = current.path();
        String family = familyOf(current);
        LAST_VARIANT.put(family, selected);

        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##we_brush_type", family, 0)) {
            for (String name : families()) {
                if (ImGui.selectable(name + "##fam_" + name, name.equals(family))) {
                    selectFamily(name);
                }
                WeCommand described = describe(name);
                if (ImGui.isItemHovered() && described != null && !described.description().isEmpty()) {
                    ImGui.setTooltip(Ui.e(described.description()));
                }
            }
            ImGui.endCombo();
        }
        List<WeCommand> variants = variants(family);
        if (variants.size() > 1) {
            // forest, set and item come plain, as "apply" (every block of the shape) and as "paint" (scattered).
            for (int i = 0; i < variants.size(); i++) {
                WeCommand variant = variants.get(i);
                String mode = modeOf(variant);
                if (i > 0) {
                    ImGui.sameLine();
                }
                if (ImGui.radioButton(Tr.t("brush.mode." + (mode.isEmpty() ? "plain" : mode)) + "##mode_" + i, variant == current)) {
                    selected = variant.path();
                    LAST_VARIANT.put(family, selected);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(Ui.e(Tr.t("brush.mode." + (mode.isEmpty() ? "plain" : mode) + ".tooltip")));
                }
            }
            current = find(brushes, selected);
        }

        CommandForm form = FORMS.get(current, Map.of()).withoutRunButton();
        if (SEEDED.add(form)) {
            // A brush nobody has set up yet paints with Axiom's active block.
            form.fillUntouchedPatterns(PatternField.activeBlock());
        }
        if (currentForm != null && currentForm != form && GuiConfig.isBrushCarryOver()) {
            carryOver(currentForm, form);
        }
        currentForm = form;
        currentBrush = current;
        form.render();
        renderRadiusLimit();

        ImGui.separator();
        ImGui.text(Ui.e(Tr.t("brush.mask")));
        MASK.render("brush_mask", Tr.t("brush.mask_hint"));

        autoApply(form);

        boolean carry = GuiConfig.isBrushCarryOver();
        if (ImGui.checkbox(Tr.t("menu.brush_carry") + "##brush_carry", carry)) {
            GuiConfig.setBrushCarryOver(!carry);
        }

        ImGui.separator();
        if (lastResult != null && lastResult.hasError()) {
            ResultView.render(lastResult, false);
        }
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

    /** Sends the brush settings to WorldEdit once they have stopped changing for a moment. */
    private static void autoApply(CommandForm form) {
        if (applying != null && applying.isDone()) {
            lastResult = applying.getNow(null);
            applying = null;
        }
        String line = form.currentLine();
        if (line == null || MASK.hasError()) {
            return;
        }
        String mask = MASK.value();
        String key = line + "\n" + mask;
        if (key.equals(appliedKey)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!key.equals(pendingKey)) {
            pendingKey = key;
            pendingSince = now;
            return;
        }
        if (now - pendingSince >= APPLY_DELAY_MS && applying == null) {
            appliedKey = key;
            applying = BrushBackend.configure(line, mask);
        }
    }

    /** WorldEdit refuses brushes larger than its max-brush-radius; offer to raise it. */
    private static void renderRadiusLimit() {
        double r = radius();
        int max = maxRadius();
        if (r <= max && limitMessage == null) {
            return;
        }
        if (r > max) {
            Colors.warn(Tr.t("brush.radius_limit", max));
            if (max < RAISED_LIMIT && ImGui.smallButton(Tr.t("brush.raise_limit", RAISED_LIMIT) + "##raise_limit")) {
                limitMessage = raiseLimit();
            }
        }
        if (limitMessage != null) {
            ImGui.textDisabled(Ui.e(limitMessage));
        }
    }

    /** Writes max-brush-radius into WorldEdit's worldedit.properties and reloads WorldEdit's configuration. */
    private static String raiseLimit() {
        try {
            Path file = WorldEdit.getInstance().getWorkingDirectoryPath("worldedit.properties");
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.ISO_8859_1));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("max-brush-radius=")) {
                    lines.set(i, "max-brush-radius=" + RAISED_LIMIT);
                    found = true;
                }
            }
            if (!found) {
                lines.add("max-brush-radius=" + RAISED_LIMIT);
            }
            Files.write(file, lines, StandardCharsets.ISO_8859_1);
            DirectBackend.run("/worldedit reload");
            appliedKey = null;
            return Tr.t("brush.limit_raised", RAISED_LIMIT, file.toString());
        } catch (Exception e) {
            AxiomWorldEditGui.LOGGER.warn("Could not change max-brush-radius", e);
            return Tr.t("brush.limit_failed", e.toString());
        }
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
