package dev.axiomwegui.ui;

import com.moulberry.axiom.tools.Tool;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Text field for a WorldEdit pattern or mask. The WorldEdit string is what is stored and sent; the field
 * checks it with WorldEdit's own parser while the player types. A pattern can also be edited as a list of
 * blocks with weights (30%stone,70%andesite); a mask can be taken from Axiom's Tool Masks window.
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

    private boolean editorOpen = false;
    private final List<Entry> entries = new ArrayList<>();
    private String parsedFrom = null;
    private String importMessage = null;

    private static final class Entry {
        final ImString weight = new ImString(16);
        final ImString block = new ImString(256);
    }

    public PatternField(DirectBackend.ParseKind kind, String initial) {
        this.kind = kind;
        if (initial != null) {
            text.set(initial);
            checkedText = null;
        }
    }

    public String value() {
        return text.get().trim();
    }

    public void set(String value) {
        text.set(value);
        changedAt = System.currentTimeMillis();
    }

    /** True if WorldEdit rejected the current text. */
    public boolean hasError() {
        return error != null && !error.isEmpty() && value().equals(checkedText);
    }

    public void render(String id, String hint) {
        String extraLabel = kind == DirectBackend.ParseKind.PATTERN ? Tr.t("pattern.active_block") : Tr.t("mask.from_axiom");
        String toggleLabel = editorOpen ? Tr.t("pattern.editor_close") : Tr.t("pattern.editor_open");
        float buttons = ImGui.calcTextSizeX(extraLabel) + 16;
        if (kind == DirectBackend.ParseKind.PATTERN) {
            buttons += ImGui.calcTextSizeX(toggleLabel) + 24;
        }
        ImGui.setNextItemWidth(Math.max(80, ImGui.getContentRegionAvailX() - buttons - 8));
        if (ImGui.inputTextWithHint("##" + id, hint, text)) {
            changedAt = System.currentTimeMillis();
        }
        ImGui.sameLine();
        if (kind == DirectBackend.ParseKind.PATTERN) {
            if (ImGui.button(extraLabel + "##ab_" + id)) {
                append(activeBlock());
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(Tr.t("pattern.active_block.tooltip")));
            }
            ImGui.sameLine();
            if (ImGui.button(toggleLabel + "##ed_" + id)) {
                editorOpen = !editorOpen;
                parsedFrom = null;
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
        if (editorOpen && kind == DirectBackend.ParseKind.PATTERN) {
            renderEditor(id);
        }
    }

    // ---------------------------------------------------------------- list editor

    private void renderEditor(String id) {
        String current = value();
        if (!current.equals(parsedFrom)) {
            parse(current);
            parsedFrom = current;
        }
        double total = 0;
        for (Entry e : entries) {
            total += weightOf(e);
        }
        boolean changed = false;
        int remove = -1;
        ImGui.indent();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            ImGui.setNextItemWidth(48);
            changed |= ImGui.inputTextWithHint("##w" + id + i, "1", e.weight);
            ImGui.sameLine();
            double share = total > 0 ? weightOf(e) / total * 100 : 0;
            ImGui.textDisabled(Ui.e(String.format(Locale.ROOT, "%3.0f%%", share)));
            ImGui.sameLine();
            ImGui.setNextItemWidth(Math.max(80, ImGui.getContentRegionAvailX() - 60));
            changed |= ImGui.inputTextWithHint("##b" + id + i, Tr.t("pattern.block_hint"), e.block);
            ImGui.sameLine();
            if (ImGui.smallButton("x##rm" + id + i)) {
                remove = i;
            }
        }
        if (remove >= 0) {
            entries.remove(remove);
            changed = true;
        }
        if (ImGui.smallButton(Tr.t("pattern.add_active") + "##add_ab" + id)) {
            Entry e = new Entry();
            e.block.set(activeBlock());
            entries.add(e);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton(Tr.t("pattern.add_row") + "##add_row" + id)) {
            entries.add(new Entry());
        }
        ImGui.pushTextWrapPos(0);
        ImGui.textDisabled(Ui.e(Tr.t("pattern.editor_help")));
        ImGui.popTextWrapPos();
        ImGui.unindent();
        if (changed) {
            String rebuilt = build();
            text.set(rebuilt);
            parsedFrom = rebuilt;
            changedAt = System.currentTimeMillis();
        }
    }

    private static double weightOf(Entry e) {
        if (e.block.get().isBlank()) {
            return 0;
        }
        try {
            String w = e.weight.get().trim();
            return w.isEmpty() ? 1 : Math.max(0, Double.parseDouble(w));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private void parse(String pattern) {
        entries.clear();
        if (pattern.isEmpty()) {
            return;
        }
        for (String token : splitTopLevel(pattern)) {
            Entry e = new Entry();
            Matcher m = WEIGHTED.matcher(token.trim());
            if (m.matches()) {
                e.weight.set(m.group(1));
                e.block.set(m.group(2));
            } else {
                e.block.set(token.trim());
            }
            entries.add(e);
        }
    }

    private String build() {
        List<String> parts = new ArrayList<>();
        for (Entry e : entries) {
            String block = e.block.get().trim();
            if (block.isEmpty()) {
                continue;
            }
            String weight = e.weight.get().trim();
            parts.add(weight.isEmpty() ? block : weight + "%" + block);
        }
        return String.join(",", parts);
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

    private static String activeBlock() {
        BlockState state = Tool.getActiveBlock();
        if (state == null) {
            return "stone";
        }
        String id = BlockStateParser.serialize(state);
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private void append(String block) {
        String current = value();
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
