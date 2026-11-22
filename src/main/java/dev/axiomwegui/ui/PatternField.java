package dev.axiomwegui.ui;

import com.moulberry.axiom.custom_blocks.CustomBlockState;
import com.moulberry.axiom.editor.widgets.SelectBlockWidget;
import com.moulberry.axiom.tools.Tool;
import com.moulberry.axiom.utils.BlockWithFloat;
import dev.axiomwegui.Tr;
import dev.axiomwegui.we.DirectBackend;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Field for a WorldEdit pattern or mask. The WorldEdit string is what is stored and sent; the field checks
 * it with WorldEdit's own parser while the player types.
 *
 * <p>A pattern that is just a mix of blocks (stone, 30%stone,70%andesite, oak_log[axis=y]) is also shown
 * with Axiom's own block list, the one from the Noise Painter: a block icon and a percentage per row, and
 * "+" to add one. Blocks can be dragged onto it from the hotbar, Inventory, Active Block and the Palette,
 * like everywhere in Axiom. Anything else (tags, #clipboard, random states) stays text only.
 *
 * <p>A mask can be taken from Axiom's Tool Masks window.
 */
public final class PatternField {
    private static final long CHECK_DELAY_MS = 300;
    private static final Pattern WEIGHTED = Pattern.compile("^(\\d+(?:\\.\\d+)?)%(.+)$");

    private final DirectBackend.ParseKind kind;
    private final ImString text = new ImString(1024);
    private String checkedText = "";
    private String error = null;
    private long changedAt = 0;
    private CompletableFuture<String> pending = null;
    /** True once the player (or code on their behalf) has put anything into the field. */
    private boolean touched = false;

    // Block list (patterns only). It mirrors the text: re-read when the text changes, written back when it is edited.
    private final List<BlockWithFloat> blocks = new ArrayList<>();
    private final SelectBlockWidget selectBlock = new SelectBlockWidget(false);
    private String blocksFrom = null;
    private boolean simple = true;

    private String importMessage = null;

    public PatternField(DirectBackend.ParseKind kind, String initial) {
        this.kind = kind;
        if (initial != null) {
            text.set(initial);
            checkedText = null;
            touched = true;
        }
    }

    public String value() {
        return text.get().trim();
    }

    public void set(String value) {
        text.set(value);
        changedAt = System.currentTimeMillis();
        touched = true;
    }

    public boolean isPattern() {
        return kind == DirectBackend.ParseKind.PATTERN;
    }

    /** True if nothing was ever put into this field. */
    public boolean untouched() {
        return !touched && value().isEmpty();
    }

    /** True if WorldEdit rejected the current text. */
    public boolean hasError() {
        return error != null && !error.isEmpty() && value().equals(checkedText);
    }

    public void render(String id, String hint) {
        String extraLabel = isPattern() ? Tr.t("pattern.active_block") : Tr.t("mask.from_axiom");
        float buttons = ImGui.calcTextSizeX(extraLabel) + 16;
        ImGui.setNextItemWidth(Math.max(80, ImGui.getContentRegionAvailX() - buttons - 8));
        if (ImGui.inputTextWithHint("##" + id, hint, text)) {
            changedAt = System.currentTimeMillis();
            touched = true;
        }
        ImGui.sameLine();
        if (isPattern()) {
            if (ImGui.button(extraLabel + "##ab_" + id)) {
                addActiveBlock();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(Tr.t("pattern.active_block.tooltip")));
            }
        } else {
            if (ImGui.button(extraLabel + "##ax_" + id)) {
                MaskImport.Result imported = MaskImport.fromAxiom();
                if (imported.mask() != null) {
                    set(imported.mask());
                }
                importMessage = imported.message();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(Tr.t("mask.from_axiom.tooltip")));
            }
        }
        tickValidation();
        if (hasError()) {
            Colors.error(error);
        }
        if (importMessage != null) {
            Colors.warn(importMessage);
        }
        if (isPattern()) {
            renderBlocks(id);
        }
    }

    // ---------------------------------------------------------------- block list

    private void renderBlocks(String id) {
        String current = value();
        if (!current.equals(blocksFrom)) {
            simple = parse(current, blocks);
            blocksFrom = current;
        }
        if (!simple) {
            ImGui.textDisabled(Ui.e(Tr.t("pattern.complex")));
            return;
        }
        if (blocks.size() == 1) {
            // A single block is always 100% in WorldEdit.
            blocks.get(0).percentage()[0] = 100f;
        }
        String before = build(blocks);
        ImGui.pushID("blocks_" + id);
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
        try {
            // Its return value misses blocks dropped onto a row, so changes are found by comparing below.
            BlockWithFloat.renderList(blocks, selectBlock, BlockWithFloat.ExtraRenderType.PERCENTAGE, 0, false);
        } finally {
            ImGui.popItemWidth();
            ImGui.popID();
        }
        if (blocks.size() == 1) {
            blocks.get(0).percentage()[0] = 100f;
        }
        String rebuilt = build(blocks);
        if (!rebuilt.equals(before)) {
            text.set(rebuilt);
            blocksFrom = rebuilt;
            changedAt = System.currentTimeMillis();
            touched = true;
        }
    }

    /**
     * Reads a WorldEdit pattern into block rows. Returns false if it is anything but a list of plain blocks
     * with optional weights; the rows are then left empty.
     */
    private static boolean parse(String pattern, List<BlockWithFloat> out) {
        out.clear();
        if (pattern.isEmpty()) {
            return true;
        }
        List<BlockState> states = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (String token : splitTopLevel(pattern)) {
            String block = token.trim();
            double weight = 1;
            Matcher m = WEIGHTED.matcher(block);
            if (m.matches()) {
                weight = Double.parseDouble(m.group(1));
                block = m.group(2).trim();
            }
            if (block.isEmpty() || !Character.isLetter(block.charAt(0))) {
                return false;
            }
            try {
                states.add(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, block, false).blockState());
            } catch (Exception e) {
                return false;
            }
            weights.add(weight);
        }
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        for (int i = 0; i < states.size(); i++) {
            float percent = total > 0 ? (float) (weights.get(i) / total * 100) : 100f / states.size();
            out.add(new BlockWithFloat((CustomBlockState) (Object) states.get(i), new float[] {percent}, null));
        }
        return true;
    }

    /** Writes block rows as a WorldEdit pattern: "stone" for one block, "30%stone,70%andesite" for a mix. */
    private static String build(List<BlockWithFloat> rows) {
        if (rows.size() == 1) {
            return blockString(rows.get(0).blockState().getVanillaState());
        }
        List<String> parts = new ArrayList<>();
        for (BlockWithFloat row : rows) {
            parts.add(percent(row.percentage()[0]) + "%" + blockString(row.blockState().getVanillaState()));
        }
        return String.join(",", parts);
    }

    private static String percent(float value) {
        float v = Math.max(0, value);
        if (Math.abs(v - Math.round(v)) < 0.05f) {
            return Integer.toString(Math.round(v));
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** A block state as WorldEdit reads it: no minecraft: prefix, only the properties that differ from the default. */
    public static String blockString(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        BlockState defaults = state.getBlock().defaultBlockState();
        List<String> props = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            if (!state.getValue(property).equals(defaults.getValue(property))) {
                props.add(property.getName() + "=" + valueName(state, property));
            }
        }
        return props.isEmpty() ? id : id + "[" + String.join(",", props) + "]";
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** Splits on commas that are not inside [...] block states. */
    static List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    // ---------------------------------------------------------------- helpers

    /** Axiom's active block as a WorldEdit block string. */
    public static String activeBlock() {
        BlockState state = Tool.getActiveBlock();
        return state == null ? "stone" : blockString(state);
    }

    /** Adds the active block: as a new row of the mix if the pattern is a block list, else appended as text. */
    private void addActiveBlock() {
        BlockState state = Tool.getActiveBlock();
        String current = value();
        if (state != null && parse(current, blocks)) {
            BlockWithFloat.addNew(blocks, (CustomBlockState) (Object) state);
            String rebuilt = build(blocks);
            set(rebuilt);
            blocksFrom = rebuilt;
            simple = true;
            return;
        }
        String block = activeBlock();
        set(current.isEmpty() ? block : current + "," + block);
    }

    private void tickValidation() {
        String current = value();
        if (pending != null) {
            if (pending.isDone()) {
                error = pending.getNow(null);
                pending = null;
            }
            return;
        }
        if (!current.equals(checkedText) && System.currentTimeMillis() - changedAt >= CHECK_DELAY_MS) {
            checkedText = current;
            if (current.isEmpty()) {
                error = null;
                return;
            }
            pending = DirectBackend.validate(kind, current);
        }
    }
}
