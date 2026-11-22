package dev.axiomwegui;

import net.minecraft.client.resources.language.I18n;

/** Translations from assets/axiom_worldedit_gui/lang. Axiom loads Cyrillic glyphs only when the game language is ru/uk/bg. */
public final class Tr {
    private Tr() {
    }

    public static String t(String key, Object... args) {
        return I18n.get(AxiomWorldEditGui.MOD_ID + "." + key, args);
    }
}
