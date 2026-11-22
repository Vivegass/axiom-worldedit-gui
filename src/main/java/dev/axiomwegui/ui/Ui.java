package dev.axiomwegui.ui;

/** ImGui's text functions treat their argument as a printf format, so a '%' in WorldEdit text must be doubled. */
final class Ui {
    private Ui() {
    }

    static String e(String text) {
        return text == null ? "" : text.replace("%", "%%");
    }
}
