package dev.axiomwegui.ui;

import com.moulberry.axiom.clipboard.Selection;
import com.moulberry.axiom.clipboard.SelectionBuffer;
import dev.axiomwegui.Tr;
import imgui.moulberry92.ImGui;
import net.minecraft.core.BlockPos;

/** One line about the Axiom selection that WorldEdit commands will use. */
final class SelectionInfo {
    private SelectionInfo() {
    }

    static void render() {
        SelectionBuffer buffer = Selection.getSelectionBuffer();
        if (buffer == null || buffer.isEmpty()) {
            ImGui.textDisabled(Ui.e(Tr.t("selection.none")));
            return;
        }
        BlockPos min = buffer.min();
        BlockPos max = buffer.max();
        String size = (max.getX() - min.getX() + 1) + "x" + (max.getY() - min.getY() + 1) + "x" + (max.getZ() - min.getZ() + 1);
        String key = buffer instanceof SelectionBuffer.AABB ? "selection.box" : "selection.shape";
        ImGui.textDisabled(Ui.e(Tr.t(key, size, buffer.size())));
    }
}
