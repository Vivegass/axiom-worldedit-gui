package dev.axiomwegui.ui;

import com.moulberry.axiom.editor.EditorUI;
import com.moulberry.axiom.editor.ImGuiHelper;
import com.moulberry.axiom.tools.Tool;
import com.moulberry.axiom.tools.ToolManager;
import dev.axiomwegui.Tr;
import dev.axiomwegui.brush.WeBrushTool;
import dev.axiomwegui.we.WeCommand;
import dev.axiomwegui.we.WeSchema;
import imgui.moulberry92.ImDrawList;
import imgui.moulberry92.ImFont;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.ImVec2;
import imgui.moulberry92.flag.ImGuiCond;
import imgui.moulberry92.type.ImBoolean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The WorldEdit brushes as a grid of tool buttons, like Axiom's Tools window, grouped by what they do.
 * Every button selects the one WorldEdit brush tool with that brush; its settings are in Tool Options.
 *
 * <p>Brushes that do what an Axiom tool does show that tool's icon with a "WE" mark in the corner. The
 * others show a short abbreviation until they get icons of their own.
 */
public final class BrushesWindow {
    private static final ImBoolean OPEN = new ImBoolean(true);

    /** Group key (window.brushes.group.*) to the brush families in it, in display order. */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("shapes", List.of("sphere", "cylinder", "set", "splatter", "clipboard"));
        GROUPS.put("terrain", List.of("smooth", "snowsmooth", "raise", "lower", "heightmap", "morph", "erode",
            "dilate", "deform", "gravity"));
        GROUPS.put("nature", List.of("forest", "feature", "snow", "biome"));
        GROUPS.put("other", List.of("extinguish", "butcher", "item"));
    }

    /** Brush family to the Axiom tool (class simple name) whose icon it borrows. */
    private static final Map<String, String> AXIOM_ICON = Map.of(
        "sphere", "FreehandDraw",
        "set", "PainterTool",
        "clipboard", "StampTool",
        "smooth", "SmoothTool",
        "raise", "ElevationTool",
        "deform", "DistortTool",
        "biome", "BiomePainterTool");

    /** Placeholder text for brushes that have no icon yet. */
    private static final Map<String, String> SHORT = Map.ofEntries(
        Map.entry("cylinder", "Cyl"), Map.entry("splatter", "Spl"), Map.entry("snowsmooth", "SnS"),
        Map.entry("lower", "Low"), Map.entry("heightmap", "Hm"), Map.entry("morph", "Mor"),
        Map.entry("erode", "Ero"), Map.entry("dilate", "Dil"), Map.entry("gravity", "Gra"),
        Map.entry("forest", "For"), Map.entry("feature", "Fea"), Map.entry("snow", "Snw"),
        Map.entry("extinguish", "Ext"), Map.entry("butcher", "But"), Map.entry("item", "Itm"));

    private BrushesWindow() {
    }

    static void render() {
        if (!WeWindows.isOpen(WeWindows.BRUSHES)) {
            return;
        }
        OPEN.set(true);
        ImGui.setNextWindowSize(200, 320, ImGuiCond.FirstUseEver);
        if (ImGui.begin(Tr.t("window.brushes") + "###axiom_we_brushes", OPEN)) {
            renderContents();
        }
        ImGui.end();
        if (!OPEN.get()) {
            WeWindows.setOpen(WeWindows.BRUSHES, false);
        }
    }

    private static void renderContents() {
        if (WeSchema.status() != WeSchema.Status.READY) {
            ImGui.textWrapped(Ui.e(WeMenu.statusLine()));
            return;
        }
        List<String> families = BrushOptions.families();
        List<String> placed = new ArrayList<>();
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            List<String> present = new ArrayList<>();
            for (String family : group.getValue()) {
                if (families.contains(family)) {
                    present.add(family);
                }
            }
            placed.addAll(present);
            renderGroup(Tr.t("window.brushes.group." + group.getKey()), present);
        }
        // Brushes a newer WorldEdit adds.
        List<String> rest = new ArrayList<>(families);
        rest.removeAll(placed);
        renderGroup(Tr.t("window.brushes.group.more"), rest);
    }

    private static void renderGroup(String title, List<String> families) {
        if (families.isEmpty()) {
            return;
        }
        ImGuiHelper.separatorWithText(title);
        float scale = EditorUI.getUiScale();
        float sizeX = (32 + ImGui.getStyle().getFramePaddingX() * 2) * scale;
        float sizeY = (32 + ImGui.getStyle().getFramePaddingY() * 2) * scale;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float available = ImGui.getContentRegionAvailX();
        int perRow = Math.max(1, (int) ((available + spacing) / (sizeX + spacing)));
        boolean toolActive = ToolManager.isToolActive() && ToolManager.getCurrentTool() == WeBrushTool.INSTANCE;
        String current = BrushOptions.currentFamily();
        int activeColor = ImGui.getColorU32(23);
        for (int i = 0; i < families.size(); i++) {
            String family = families.get(i);
            if (i % perRow != 0) {
                ImGui.sameLine();
            }
            boolean active = toolActive && family.equals(current);
            if (active) {
                ImGuiHelper.pushStyleColor(21, activeColor);
            }
            ImGui.pushID("we_brush_" + family);
            boolean clicked = iconButton(family, sizeX, sizeY);
            ImGui.popID();
            if (active) {
                ImGuiHelper.popStyleColor();
            }
            if (clicked) {
                select(family);
            }
            if (ImGui.isItemHovered()) {
                WeCommand described = BrushOptions.describe(family);
                ImGui.beginTooltip();
                ImGui.textUnformatted(family);
                if (described != null && !described.description().isEmpty()) {
                    ImGui.textDisabled(Ui.e(described.description()));
                }
                ImGui.endTooltip();
            }
        }
    }

    /** One square button: a borrowed Axiom icon with a WE mark, or the placeholder text. */
    private static boolean iconButton(String family, float sizeX, float sizeY) {
        VectorIcon vector = VectorIcon.get(family);
        if (vector != null) {
            boolean clicked = ImGui.button("##icon", sizeX, sizeY);
            ImVec2 min = ImGui.getItemRectMin();
            float padX = ImGui.getStyle().getFramePaddingX() * EditorUI.getUiScale();
            float padY = ImGui.getStyle().getFramePaddingY() * EditorUI.getUiScale();
            float side = Math.min(sizeX - padX * 2, sizeY - padY * 2);
            vector.draw(ImGui.getWindowDrawList(), min.x + (sizeX - side) / 2, min.y + (sizeY - side) / 2, side,
                ImGui.getColorU32(0));
            return clicked;
        }
        Character glyph = axiomGlyph(family);
        ImFont icons = EditorUI.icons;
        if (glyph != null && icons != null) {
            ImGui.pushFont(icons, icons.getLegacySize());
            boolean clicked = ImGui.button(String.valueOf(glyph), sizeX, sizeY);
            ImGui.popFont();
            drawWeMark();
            return clicked;
        }
        String text = SHORT.getOrDefault(family, family.length() > 3 ? family.substring(0, 3) : family);
        return ImGui.button(text, sizeX, sizeY);
    }

    /** A small "WE" in the bottom right corner of the last button. */
    private static void drawWeMark() {
        ImVec2 max = ImGui.getItemRectMax();
        ImDrawList draw = ImGui.getWindowDrawList();
        int size = Math.max(8, Math.round(ImGui.getFontSize() * 0.6f));
        float w = size * 1.3f;
        float h = size;
        float x = max.x - w - 2;
        float y = max.y - h - 2;
        draw.addRectFilled(x - 1, y, x + w + 1, y + h, 0xC0000000, 2);
        draw.addText(ImGui.getFont(), size, new ImVec2(x, y), 0xFFFFFFFF, "WE");
    }

    private static final Map<String, Character> GLYPHS = new LinkedHashMap<>();

    private static Character axiomGlyph(String family) {
        String toolClass = AXIOM_ICON.get(family);
        if (toolClass == null) {
            return null;
        }
        if (!GLYPHS.containsKey(toolClass)) {
            Character found = null;
            for (Tool tool : ToolManager.getTools()) {
                if (tool.getClass().getSimpleName().equals(toolClass)) {
                    found = tool.iconChar();
                }
            }
            GLYPHS.put(toolClass, found);
        }
        return GLYPHS.get(toolClass);
    }

    /** Picks the WorldEdit brush tool with this brush. */
    private static void select(String family) {
        BrushOptions.selectFamily(family);
        List<Tool> tools = ToolManager.getTools();
        int index = tools.indexOf(WeBrushTool.INSTANCE);
        if (index >= 0) {
            ToolManager.setCurrentToolIndex(index);
            ToolManager.setToolSelected(true);
        }
    }
}
