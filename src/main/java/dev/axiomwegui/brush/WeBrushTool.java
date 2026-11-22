package dev.axiomwegui.brush;

import com.moulberry.axiom.RayCaster;
import com.moulberry.axiom.UserAction;
import com.moulberry.axiom.render.AxiomWorldRenderContext;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.tools.Tool;
import dev.axiomwegui.Tr;
import dev.axiomwegui.ui.BrushOptions;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * An Axiom tool that paints with any WorldEdit brush. Right mouse paints (like Axiom's own brushes);
 * the brush and its settings are chosen in Tool Options.
 */
public final class WeBrushTool implements Tool {
    public static final WeBrushTool INSTANCE = new WeBrushTool();
    private static final long DAB_INTERVAL_MS = 60;

    /** Pixels the mouse must move before the next dab of a stroke. */
    private static final double MIN_MOUSE_MOVE = 3;

    private boolean painting = false;
    private BlockPos lastDab = null;
    private long lastDabAt = 0;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    private WeBrushTool() {
    }

    @Override
    public UserAction.ActionResult callAction(UserAction action, Object object) {
        if (action == UserAction.RIGHT_MOUSE) {
            if (painting) {
                // Axiom may report the held button again; the stroke is already running.
                return UserAction.ActionResult.USED_STOP;
            }
            if (BrushBackend.summary() == null) {
                BrushOptions.flashNotConfigured();
                return UserAction.ActionResult.USED_STOP;
            }
            painting = true;
            lastDab = null;
            dabAtCursor();
            return UserAction.ActionResult.USED_STOP;
        }
        if (action == UserAction.ESCAPE && painting) {
            stopPainting();
            return UserAction.ActionResult.USED_STOP;
        }
        return UserAction.ActionResult.NOT_HANDLED;
    }

    /**
     * Paints at the block under the cursor. Within a stroke the next dab waits until the mouse moves:
     * otherwise each dab would hit the blocks the previous one just placed and the stroke would grow
     * towards the camera.
     */
    private void dabAtCursor() {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos();
        double my = mc.mouseHandler.ypos();
        boolean first = lastDab == null;
        if (!first && Math.hypot(mx - lastMouseX, my - lastMouseY) < MIN_MOUSE_MOVE) {
            return;
        }
        RayCaster.RaycastResult hit = Tool.raycastBlock();
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.blockPos();
        long now = System.currentTimeMillis();
        if (!first && (pos.equals(lastDab) || now - lastDabAt < DAB_INTERVAL_MS)) {
            return;
        }
        lastDab = pos;
        lastDabAt = now;
        lastMouseX = mx;
        lastMouseY = my;
        BrushBackend.dab(pos);
    }

    private void stopPainting() {
        painting = false;
        lastDab = null;
        BrushBackend.finishStroke();
    }

    @Override
    public void render(AxiomWorldRenderContext rc) {
        if (painting) {
            if (Tool.cancelUsing() || !Tool.isMouseDown(1)) {
                stopPainting();
            } else {
                dabAtCursor();
            }
        }
        RayCaster.RaycastResult hit = Tool.raycastBlock();
        if (hit != null) {
            Tool.renderRaycastOverlay(rc, hit.blockPos());
        }
    }

    @Override
    public void displayImguiOptions() {
        BrushOptions.render();
    }

    @Override
    public void reset() {
        if (painting) {
            stopPainting();
        }
    }

    @Override
    public void toolDeselected() {
        reset();
    }

    @Override
    public String name() {
        return Tr.t("brush.tool_name");
    }

    @Override
    public void writeSettings(CompoundTag tag) {
        tag.putString("brush", BrushOptions.selectedBrush());
    }

    @Override
    public void loadSettings(CompoundTag tag) {
        tag.getString("brush").ifPresent(BrushOptions::selectBrush);
    }

    @Override
    public char iconChar() {
        return '';
    }

    @Override
    public String keybindId() {
        return null;
    }

    @Override
    public EnumSet<AxiomPermission> requiredPermissions() {
        return EnumSet.of(AxiomPermission.TOOL);
    }
}
