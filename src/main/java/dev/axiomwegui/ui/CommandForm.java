package dev.axiomwegui.ui;

import com.moulberry.axiom.editor.widgets.SearchableCombo;
import dev.axiomwegui.Tr;
import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeCommand;
import dev.axiomwegui.we.WeParam;
import dev.axiomwegui.we.WeResult;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImBoolean;
import imgui.moulberry92.type.ImString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A form for one WorldEdit command, built from its parameters: a field per argument, a checkbox per
 * switch, and a field per flag that takes a value. "Run" assembles the command line the same way it
 * would be typed and sends it through {@link DirectBackend}.
 */
public final class CommandForm {
    /** Ready-made values for an argument, e.g. expressions for //generate. */
    public record Preset(String label, String value) {
    }

    private final WeCommand command;
    private final List<Field> fields = new ArrayList<>();
    /** Arguments of the group this sub-command belongs to, typed before its name; usually empty. */
    private final List<Field> groupFields = new ArrayList<>();
    private Function<String, CompletableFuture<WeResult>> runner = DirectBackend::run;
    private String runLabel = null;
    private final Map<String, List<Preset>> presets;
    private CompletableFuture<WeResult> running = null;
    private WeResult lastResult = null;
    private String buildError = null;

    public CommandForm(WeCommand command, Map<String, List<Preset>> presets) {
        this.command = command;
        this.presets = presets == null ? Map.of() : presets;
        for (WeParam param : command.groupParams()) {
            groupFields.add(new Field(param));
        }
        for (WeParam param : command.params()) {
            fields.add(new Field(param));
        }
    }

    public CommandForm(WeCommand command) {
        this(command, null);
    }

    public WeCommand command() {
        return command;
    }

    /** Sends the built command line somewhere else than {@link DirectBackend#run}, under another button label. */
    public CommandForm withRunner(Function<String, CompletableFuture<WeResult>> runner, String label) {
        this.runner = runner;
        this.runLabel = label;
        return this;
    }

    /** The command line for the current values, or null if a required value is missing. */
    public String currentLine() {
        return buildLine();
    }

    /** The last result of pressing the run button, or null. */
    public WeResult lastResult() {
        return lastResult;
    }

    /** Hides the run button; the owner sends {@link #currentLine()} itself (e.g. on every change). */
    public CommandForm withoutRunButton() {
        this.showRunButton = false;
        return this;
    }

    private boolean showRunButton = true;

    private Field find(String name) {
        for (Field field : groupFields) {
            if (field.param.name().equals(name)) {
                return field;
            }
        }
        for (Field field : fields) {
            if (field.param.name().equals(name)) {
                return field;
            }
        }
        return null;
    }

    /** True if the command (or its group) has an argument or flag with this name. */
    public boolean has(String name) {
        return find(name) != null;
    }

    /** The typed value of an argument, or its default if empty, or "" if neither. */
    public String valueOrDefault(String name) {
        Field field = find(name);
        if (field == null) {
            return "";
        }
        String value = field.value();
        if (value.isEmpty() && !field.param.defaults().isEmpty()) {
            return String.join(",", field.param.defaults());
        }
        return value;
    }

    /** Sets the text of an argument (used by Ctrl+middle-mouse radius adjustment). */
    public void setValue(String name, String value) {
        Field field = find(name);
        if (field != null) {
            field.set(value);
        }
    }

    /** Puts {@code value} into every required pattern argument nobody has filled in yet. Returns true if any changed. */
    public boolean fillUntouchedPatterns(String value) {
        boolean changed = false;
        for (List<Field> list : List.of(groupFields, fields)) {
            for (Field field : list) {
                if (field.pattern != null && field.pattern.isPattern() && field.pattern.untouched()
                        && field.param.kind() == WeParam.Kind.ARGUMENT && field.param.required()) {
                    field.pattern.set(value);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** One input of the form. Exactly one of {@code pattern}, {@code text}, {@code checked} is used. */
    private static final class Field {
        final WeParam param;
        final PatternField pattern;
        final ImString text;
        final ImBoolean checked;
        SearchableCombo combo = null;
        List<String> comboEntries = null;

        Field(WeParam param) {
            this.param = param;
            String type = param.type();
            if (param.kind() == WeParam.Kind.SWITCH) {
                pattern = null;
                text = null;
                checked = new ImBoolean(false);
            } else if (type.equals("Pattern")) {
                pattern = new PatternField(DirectBackend.ParseKind.PATTERN, null);
                text = null;
                checked = null;
            } else if (type.equals("Mask")) {
                pattern = new PatternField(DirectBackend.ParseKind.MASK, null);
                text = null;
                checked = null;
            } else {
                pattern = null;
                text = new ImString(1024);
                checked = null;
            }
        }

        String value() {
            if (pattern != null) {
                return pattern.value();
            }
            if (text != null) {
                return text.get().trim();
            }
            return "";
        }

        void set(String value) {
            if (pattern != null) {
                pattern.set(value);
            } else if (text != null) {
                text.set(value);
            }
        }
    }

    public void render() {
        String id = command.path();
        ImGui.pushID(id);
        try {
            if (!command.description().isEmpty()) {
                ImGui.pushTextWrapPos(0);
                ImGui.textDisabled(Ui.e(command.description()));
                ImGui.popTextWrapPos();
            }
            for (int i = 0; i < groupFields.size(); i++) {
                renderField(groupFields.get(i), 1000 + i);
            }
            for (int i = 0; i < fields.size(); i++) {
                renderField(fields.get(i), i);
            }
            renderRunRow();
        } finally {
            ImGui.popID();
        }
    }

    private void renderField(Field field, int index) {
        WeParam p = field.param;
        String fieldId = "f" + index;
        if (p.kind() == WeParam.Kind.SWITCH) {
            String label = p.name() + (p.description().isEmpty() ? "" : "  " + p.description());
            ImGui.checkbox(label + "##" + fieldId, field.checked);
            return;
        }
        StringBuilder label = new StringBuilder(p.name());
        if (p.required()) {
            label.append(" *");
        }
        ImGui.text(Ui.e(label.toString()));
        if (!p.description().isEmpty()) {
            ImGui.sameLine();
            ImGui.textDisabled(Ui.e("(?)"));
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(p.description()));
            }
        }
        List<Preset> options = presets.get(p.name());
        if (options != null && !options.isEmpty()) {
            ImGui.sameLine();
            if (ImGui.beginCombo("##presets_" + fieldId, Tr.t("form.presets"), 0)) {
                for (Preset preset : options) {
                    if (ImGui.selectable(preset.label() + "##" + preset.value())) {
                        field.set(preset.value());
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(Ui.e(preset.value()));
                    }
                }
                ImGui.endCombo();
            }
        }
        String hint = hint(p);
        if (field.pattern != null) {
            field.pattern.render(fieldId, hint);
            return;
        }
        Slider slider = Slider.of(p);
        if (slider != null) {
            renderSlider(field, fieldId, slider);
            return;
        }
        List<String> values = ValueLists.of(p);
        if (values != null && !values.isEmpty()) {
            renderValueList(field, fieldId, values, hint);
            return;
        }
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##" + fieldId, hint, field.text);
    }

    // ---------------------------------------------------------------- sliders

    /** Numbers that get a slider, like radius and density in Axiom's own tools. Ctrl+click types any value. */
    private record Slider(float min, float max, boolean integer) {
        static Slider of(WeParam p) {
            String type = p.type();
            boolean integer = type.equals("int") || type.equals("Integer");
            if (!integer && !type.equals("double") && !type.equals("Double")) {
                return null;
            }
            return switch (lower(p.name())) {
                case "radius", "brushsize", "size" -> new Slider(0, 64, true);
                case "density" -> new Slider(0, 100, integer);
                case "height" -> new Slider(1, 64, true);
                case "iterations" -> new Slider(1, 20, true);
                case "decay" -> new Slider(0, 10, true);
                case "intensity" -> new Slider(0, 20, integer);
                case "minerodefaces", "mindilatefaces" -> new Slider(1, 6, true);
                case "numerodeiterations", "numdilateiterations" -> new Slider(1, 10, true);
                // //brush gravity -h: how far up and down blocks are affected.
                case "-h" -> new Slider(1, 384, true);
                // //brush snowsmooth -l: snow blocks kept under the snow layers.
                case "-l" -> new Slider(0, 10, true);
                default -> null;
            };
        }
    }

    private void renderSlider(Field field, String fieldId, Slider slider) {
        String shown = field.value();
        if (shown.isEmpty() && !field.param.defaults().isEmpty()) {
            shown = field.param.defaults().get(0);
        }
        float value;
        // Empty and no number to show (e.g. gravity's "default-vertical-height"): the slider says "default".
        String format = null;
        try {
            value = Float.parseFloat(shown.trim());
        } catch (NumberFormatException e) {
            value = slider.min();
            format = Tr.t("form.slider_default").replace("%", "%%");
        }
        ImGui.setNextItemWidth(-1);
        if (slider.integer()) {
            int[] v = {Math.round(value)};
            if (ImGui.sliderInt("##" + fieldId, v, (int) slider.min(), (int) slider.max(), format == null ? "%d" : format)) {
                field.text.set(Integer.toString(v[0]));
            }
        } else {
            float[] v = {value};
            if (ImGui.sliderFloat("##" + fieldId, v, slider.min(), slider.max(), format == null ? "%.1f" : format)) {
                field.text.set(String.format(Locale.ROOT, "%.1f", v[0]).replaceAll("\\.0$", ""));
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(Ui.e(Tr.t("form.slider_tooltip")));
        }
        if (ImGui.isItemClicked(1)) {
            field.text.set("");
        }
    }

    // ---------------------------------------------------------------- value lists

    private static String shortName(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    /**
     * A dropdown of every value WorldEdit accepts for this argument. It is Axiom's own searchable combo, the
     * one its tools use: open it and type to filter.
     */
    private void renderValueList(Field field, String fieldId, List<String> values, String hint) {
        if (field.value().isEmpty() && field.param.required() && field.param.type().equals("RegionFactory")
            && values.contains("sphere")) {
            // A shape is required but WorldEdit has no default for it; start with a sphere.
            field.text.set("sphere");
        }
        String current = field.value();
        List<String> entries = new ArrayList<>();
        if (!field.param.required()) {
            entries.add(""); // WorldEdit's default
        }
        entries.addAll(values);
        int selected = entries.indexOf(current);
        if (selected < 0 && !current.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                if (shortName(entries.get(i)).equals(current)) {
                    selected = i;
                }
            }
            if (selected < 0) {
                // Typed by hand or set elsewhere: keep it selectable.
                entries.add(current);
                selected = entries.size() - 1;
            }
        }
        if (field.combo == null || !entries.equals(field.comboEntries)) {
            String[] labels = new String[entries.size()];
            for (int i = 0; i < labels.length; i++) {
                String entry = entries.get(i);
                labels[i] = entry.isEmpty() ? defaultLabel(field.param, hint) : shortName(entry);
            }
            field.combo = new SearchableCombo(labels);
            field.comboEntries = entries;
        }
        int[] index = {selected};
        ImGui.setNextItemWidth(-1);
        if (field.combo.render("##" + fieldId, index) && index[0] >= 0 && index[0] < entries.size()) {
            field.text.set(entries.get(index[0]));
        }
    }

    private static String defaultLabel(WeParam p, String hint) {
        List<String> defaults = p.defaults();
        boolean hasDefault = !defaults.isEmpty() && !(defaults.size() == 1 && defaults.get(0).isEmpty());
        return hasDefault ? Tr.t("form.default", String.join(",", defaults)) : hint;
    }

    private static String hint(WeParam p) {
        String type = p.qualifier().isEmpty() ? p.type() : p.type() + " @" + p.qualifier();
        List<String> defaults = p.defaults();
        if (!defaults.isEmpty() && !(defaults.size() == 1 && defaults.get(0).isEmpty())) {
            return type + ", " + Tr.t("form.default", String.join(",", defaults));
        }
        if (!p.required()) {
            return type + ", " + Tr.t("form.optional");
        }
        return type;
    }

    private void renderRunRow() {
        boolean busy = running != null && !running.isDone();
        if (running != null && running.isDone()) {
            lastResult = running.getNow(null);
            running = null;
        }
        String line = buildLine();
        if (showRunButton) {
            ImGui.beginDisabled(busy);
            if (ImGui.button((runLabel != null ? runLabel : Tr.t("form.run")) + "##run")) {
                if (line != null) {
                    running = runner.apply(line);
                }
            }
            ImGui.endDisabled();
            ImGui.sameLine();
        }
        if (ImGui.smallButton(Tr.t("form.copy") + "##copy") && line != null) {
            ImGui.setClipboardText(line);
        }
        ImGui.sameLine();
        if (line != null) {
            ImGui.textDisabled(Ui.e(line));
        } else if (buildError != null) {
            Colors.warn(buildError);
        }
        if (busy) {
            ImGui.textDisabled(Ui.e(Tr.t("form.running")));
        } else if (lastResult != null) {
            ResultView.render(lastResult, false);
        }
    }

    /** The command line for the current values, or null (and {@link #buildError} set) if it cannot be built yet. */
    private String buildLine() {
        buildError = null;
        StringBuilder sb;
        if (!groupFields.isEmpty() && command.groupPath() != null) {
            // e.g. "/brush apply sphere 5 forest oak": the group's arguments come before the sub-command word,
            // so all of them must be present.
            sb = new StringBuilder(command.groupPath());
            if (!appendParams(sb, groupFields, true)) {
                return null;
            }
            sb.append(' ').append(command.name());
        } else {
            sb = new StringBuilder(command.path());
        }
        if (!appendParams(sb, fields, false)) {
            return null;
        }
        return sb.toString();
    }

    /** Appends flags, then positional arguments. Returns false (with {@link #buildError} set) if a value is missing. */
    private boolean appendParams(StringBuilder sb, List<Field> list, boolean allArguments) {
        for (Field field : list) {
            WeParam p = field.param;
            if (p.kind() == WeParam.Kind.SWITCH && field.checked.get()) {
                sb.append(' ').append(p.name());
            } else if (p.kind() == WeParam.Kind.VALUE_FLAG && !field.value().isEmpty()) {
                sb.append(' ').append(p.name()).append(' ').append(quote(field.value()));
            }
        }
        // Positional arguments: trailing empty optional ones are left out; an empty one before a filled
        // one gets its default.
        List<Field> args = new ArrayList<>();
        for (Field field : list) {
            if (field.param.kind() == WeParam.Kind.ARGUMENT) {
                args.add(field);
            }
        }
        int last = allArguments ? args.size() - 1 : -1;
        for (int i = 0; i < args.size(); i++) {
            if (!args.get(i).value().isEmpty() || args.get(i).param.required()) {
                last = Math.max(last, i);
            }
        }
        for (int i = 0; i <= last; i++) {
            Field field = args.get(i);
            WeParam p = field.param;
            String value = field.value();
            if (value.isEmpty()) {
                List<String> defaults = p.defaults();
                boolean hasDefault = !defaults.isEmpty() && !(defaults.size() == 1 && defaults.get(0).isEmpty());
                if (p.required()) {
                    buildError = Tr.t("form.missing", p.name());
                    return false;
                }
                if (!hasDefault) {
                    buildError = Tr.t("form.fill_or_clear", p.name());
                    return false;
                }
                value = String.join(",", defaults);
            }
            if (field.pattern != null && field.pattern.hasError()) {
                buildError = Tr.t("form.invalid", p.name());
                return false;
            }
            sb.append(' ').append(p.variable() ? value : quote(value));
        }
        return true;
    }

    private static String quote(String value) {
        if (value.indexOf(' ') < 0 || value.startsWith("\"")) {
            return value;
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    /** Forms keep what was typed while the window stays open. */
    public static final class Cache {
        private final Map<String, CommandForm> forms = new HashMap<>();

        public CommandForm get(WeCommand command, Map<String, List<Preset>> presets) {
            CommandForm form = forms.get(command.path());
            if (form == null || form.command != command) {
                form = new CommandForm(command, presets);
                forms.put(command.path(), form);
            }
            return form;
        }

        public void clear() {
            forms.clear();
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
