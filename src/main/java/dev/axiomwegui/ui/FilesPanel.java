package dev.axiomwegui.ui;

import com.sk89q.worldedit.WorldEdit;
import dev.axiomwegui.Tr;
import dev.axiomwegui.we.ClipboardBridge;
import dev.axiomwegui.we.DirectBackend;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.type.ImString;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * WorldEdit's schematic folder and CraftScripts folder: load a schematic into the WorldEdit clipboard (and on
 * into Axiom's), save the clipboard, run a script. Lists are re-read every few seconds.
 */
final class FilesPanel {
    private static final long RESCAN_MS = 3000;
    private static final ImString SAVE_NAME = new ImString(128);
    private static final ImString SCRIPT_ARGS = new ImString(256);
    private static List<String> schematics = List.of();
    private static List<String> scripts = List.of();
    private static long scannedAt = 0;
    private static String message = null;

    private FilesPanel() {
    }

    /** //schem load finishes on a worker thread; its answer arrives later as extra result lines. */
    private static dev.axiomwegui.we.WeResult pendingLoad = null;
    private static long pendingSince = 0;
    /** Lines the command printed before returning (e.g. "please wait"); the answer is any line after them. */
    private static int pendingInitialLines = 0;
    private static final long LOAD_TIMEOUT_MS = 15000;

    private static void tickPendingLoad() {
        if (pendingLoad == null) {
            return;
        }
        boolean answered = pendingLoad.hasError() || pendingLoad.lines().size() > pendingInitialLines;
        if (!answered && System.currentTimeMillis() - pendingSince < LOAD_TIMEOUT_MS) {
            return;
        }
        dev.axiomwegui.we.WeResult r = pendingLoad;
        pendingLoad = null;
        if (r.hasError() || !answered) {
            message = answered ? summary(r) : Tr.t("files.load_timeout");
            return;
        }
        ClipboardBridge.worldEditToAxiom().thenAccept(m -> message = m);
    }

    static void renderSchematics() {
        rescan();
        tickPendingLoad();
        Path dir = dir(true);
        ImGui.textDisabled(Ui.e(Tr.t("files.folder", dir == null ? "?" : dir.toString())));
        if (schematics.isEmpty()) {
            ImGui.textDisabled(Ui.e(Tr.t("files.no_schematics")));
        }
        for (String name : schematics) {
            ImGui.pushID("schem_" + name);
            if (ImGui.smallButton(Tr.t("files.load"))) {
                message = Tr.t("form.running");
                DirectBackend.run("//schem load \"" + name + "\"").thenAccept(r -> message = summary(r));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(Tr.t("files.load.tooltip")));
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Tr.t("files.load_axiom"))) {
                message = Tr.t("form.running");
                DirectBackend.run("//schem load \"" + name + "\"").thenAccept(r -> {
                    pendingLoad = r;
                    pendingInitialLines = r.lines().size();
                    pendingSince = System.currentTimeMillis();
                });
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Ui.e(Tr.t("files.load_axiom.tooltip")));
            }
            ImGui.sameLine();
            ImGui.text(Ui.e(name));
            ImGui.popID();
        }
        ImGui.setNextItemWidth(Math.max(80, ImGui.getContentRegionAvailX() - ImGui.calcTextSizeX(Tr.t("files.save")) - 24));
        ImGui.inputTextWithHint("##schem_save_name", Tr.t("files.save_hint"), SAVE_NAME);
        ImGui.sameLine();
        if (ImGui.button(Tr.t("files.save") + "##schem_save")) {
            String name = SAVE_NAME.get().trim();
            if (!name.isEmpty()) {
                message = Tr.t("form.running");
                DirectBackend.run("//schem save \"" + name + "\"").thenAccept(r -> {
                    message = summary(r);
                    scannedAt = 0;
                });
            }
        }
        if (message != null) {
            ImGui.pushTextWrapPos(0);
            ImGui.textDisabled(Ui.e(message));
            ImGui.popTextWrapPos();
        }
    }

    static void renderScripts() {
        rescan();
        Path dir = dir(false);
        ImGui.textDisabled(Ui.e(Tr.t("files.folder", dir == null ? "?" : dir.toString())));
        if (scripts.isEmpty()) {
            ImGui.textDisabled(Ui.e(Tr.t("files.no_scripts")));
            return;
        }
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##script_args", Tr.t("files.script_args"), SCRIPT_ARGS);
        for (String name : scripts) {
            ImGui.pushID("script_" + name);
            if (ImGui.smallButton(Tr.t("form.run"))) {
                String args = SCRIPT_ARGS.get().trim();
                DirectBackend.run("/cs " + name + (args.isEmpty() ? "" : " " + args));
            }
            ImGui.sameLine();
            ImGui.text(Ui.e(name));
            ImGui.popID();
        }
    }

    private static String summary(dev.axiomwegui.we.WeResult r) {
        return r.lines().isEmpty() ? r.command() : r.lines().get(r.lines().size() - 1).text();
    }

    private static Path dir(boolean schematicsDir) {
        try {
            WorldEdit we = WorldEdit.getInstance();
            String sub = schematicsDir ? we.getConfiguration().saveDir : we.getConfiguration().scriptsDir;
            return we.getWorkingDirectoryPath(sub);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void rescan() {
        long now = System.currentTimeMillis();
        if (now - scannedAt < RESCAN_MS) {
            return;
        }
        scannedAt = now;
        schematics = list(dir(true), ".schem", ".schematic", ".nbt");
        scripts = list(dir(false), ".js");
    }

    private static List<String> list(Path dir, String... extensions) {
        List<String> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                for (String ext : extensions) {
                    if (lower.endsWith(ext)) {
                        out.add(name);
                        return;
                    }
                }
            });
        } catch (IOException ignored) {
            // the folder may be gone; show nothing
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }
}
