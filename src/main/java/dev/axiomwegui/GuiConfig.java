package dev.axiomwegui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Addon settings in {@code config/axiom-worldedit-gui.json}.
 * Window positions and sizes are not stored here: Axiom's imgui.ini keeps them.
 */
public final class GuiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("axiom-worldedit-gui.json");

    private static Data data = new Data();

    private GuiConfig() {
    }

    /** What is written to disk. */
    private static final class Data {
        Set<String> openWindows = new LinkedHashSet<>();
        boolean journal = true;
        boolean hideCuiInEditor = true;
        int consoleLines = 200;
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            if (loaded != null) {
                if (loaded.openWindows == null) {
                    loaded.openWindows = new LinkedHashSet<>();
                }
                data = loaded;
            }
        } catch (Exception e) {
            AxiomWorldEditGui.LOGGER.warn("Could not read {}, using defaults", FILE, e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            AxiomWorldEditGui.LOGGER.warn("Could not write {}", FILE, e);
        }
    }

    /** Whether WorldEdit edits go to Axiom's undo history (Ctrl+Z) instead of WorldEdit's (//undo). */
    public static boolean isJournalEnabled() {
        return data.journal;
    }

    public static void setJournalEnabled(boolean enabled) {
        if (data.journal != enabled) {
            data.journal = enabled;
            save();
        }
    }

    /** Whether WorldEditCUI's selection lines are hidden while the Axiom editor is open. */
    public static boolean isHideCuiInEditor() {
        return data.hideCuiInEditor;
    }

    public static void setHideCuiInEditor(boolean hide) {
        if (data.hideCuiInEditor != hide) {
            data.hideCuiInEditor = hide;
            save();
        }
    }

    public static int consoleLines() {
        return Math.max(20, data.consoleLines);
    }

    public static boolean isWindowOpen(String id) {
        return data.openWindows.contains(id);
    }

    public static void setWindowOpen(String id, boolean open) {
        boolean changed = open ? data.openWindows.add(id) : data.openWindows.remove(id);
        if (changed) {
            save();
        }
    }
}
