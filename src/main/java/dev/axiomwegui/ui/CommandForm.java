package dev.axiomwegui.ui;

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

    /** One input of the form. Exactly one of {@code pattern}, {@code text}, {@code checked} is used. */
    private static final class Field {
        final WeParam param;
        final PatternField pattern;
        final ImString text;
        final ImBoolean checked;

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
        } else {
            ImGui.setNextItemWidth(-1);
            ImGui.inputTextWithHint("##" + fieldId, hint, field.text);
        }
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
        ImGui.beginDisabled(busy);
        if (ImGui.button((runLabel != null ? runLabel : Tr.t("form.run")) + "##run")) {
            if (line != null) {
                running = runner.apply(line);
            }
        }
        ImGui.endDisabled();
        ImGui.sameLine();
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
