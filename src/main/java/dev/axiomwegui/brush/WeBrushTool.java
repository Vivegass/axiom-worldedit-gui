package dev.axiomwegui.brush;

import com.moulberry.axiom.RayCaster;
import com.moulberry.axiom.UserAction;
import com.moulberry.axiom.brush_shapes.BrushShape;
import com.moulberry.axiom.editor.EditorUI;
import com.moulberry.axiom.render.AxiomWorldRenderContext;
import com.moulberry.axiom.render.SharedBrushPreviewRenderer;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.tools.SimpleRadiusAdjustment;
import com.moulberry.axiom.tools.Tool;
import dev.axiomwegui.Tr;
import dev.axiomwegui.ui.BrushOptions;
import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * An Axiom tool that paints with any WorldEdit brush, used like Axiom's own brushes: hold the right mouse
 * button to paint, Ctrl + middle mouse drag to change the radius; the brush shape is previewed under the
 * cursor. The brush and its settings are chosen in Tool Options.
 */
public final class WeBrushTool implements Tool {
    public static final WeBrushTool INSTANCE = new WeBrushTool();
    private static final long DAB_INTERVAL_MS = 20;
    /** Most extra dabs put between two cursor positions when the mouse jumps. */
    private static final int MAX_FILL_DABS = 48;
    /** Screen pixels the mouse must move before the next dab of a stroke. */
    private static final float MIN_MOUSE_MOVE = 2;
    /** Same value as BrushOptions' raise-limit button; Ctrl+MMB can go this far so the limit hint shows. */
    private static final int ADJUST_MAX = 64;

    private boolean painting = false;
    private BlockPos lastDab = null;
    private long lastDabAt = 0;
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;
    /** Distance from the camera to the last surface the stroke really hit. */
    private double strokeDistance = -1;

    private final int[] adjustRadius = {0};
    private final float[] adjustBase = {0};

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
            strokeDistance = -1;
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
     * Paints at the block under the cursor. In the editor the cursor belongs to ImGui, so mouse movement is
     * read from ImGui. A stroke keeps its depth: if the cursor suddenly hits a block much closer to the
     * camera right next to the last dab, that is a block this stroke just placed, and the dab goes to the
     * stroke's depth instead; otherwise strokes would pile up towards the camera.
     */
    private void dabAtCursor() {
        float mx = EditorUI.getIO().getMousePosX();
        float my = EditorUI.getIO().getMousePosY();
        boolean first = lastDab == null;
        long now = System.currentTimeMillis();
        if (!first) {
            if (Math.hypot(mx - lastMouseX, my - lastMouseY) < MIN_MOUSE_MOVE || now - lastDabAt < DAB_INTERVAL_MS) {
                return;
            }
        }
        RayCaster.RaycastResult hit = Tool.raycastBlock();
        Entity camera = Minecraft.getInstance().getCameraEntity();
        Vec3 look = Tool.getLookDirection();
        if (camera == null || look == null) {
            return;
        }
        Vec3 eye = camera.getEyePosition();
        BlockPos pos;
        if (hit != null) {
            double distance = hit.worldPos().distanceTo(eye);
            double radius = Math.max(1, BrushOptions.radius());
            boolean ownBlocks = !first && strokeDistance > 0
                && distance < strokeDistance - 0.5
                && hit.blockPos().distSqr(lastDab) <= (radius + 2) * (radius + 2);
            if (ownBlocks) {
                pos = BlockPos.containing(eye.add(look.scale(strokeDistance)));
            } else {
                pos = hit.blockPos();
                strokeDistance = distance;
            }
        } else if (!first && strokeDistance > 0) {
            pos = BlockPos.containing(eye.add(look.scale(strokeDistance)));
        } else {
            return;
        }
        if (!first && pos.equals(lastDab)) {
            return;
        }
        if (!first) {
            fillGap(lastDab, pos);
        }
        lastDab = pos;
        lastDabAt = now;
        lastMouseX = mx;
        lastMouseY = my;
        BrushBackend.dab(pos);
    }

    /**
     * A fast mouse leaves big jumps between two dabs; paint along the line between them so the stroke has no
     * holes. Dabs are half a radius apart, like overlapping stamps.
     */
    private static void fillGap(BlockPos from, BlockPos to) {
        double spacing = Math.max(1, BrushOptions.radius() * 0.5);
        double distance = Math.sqrt(from.distSqr(to));
        int steps = (int) Math.min(MAX_FILL_DABS, Math.ceil(distance / spacing));
        BlockPos previous = from;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            BlockPos mid = BlockPos.containing(
                from.getX() + (to.getX() - from.getX()) * t + 0.5,
                from.getY() + (to.getY() - from.getY()) * t + 0.5,
                from.getZ() + (to.getZ() - from.getZ()) * t + 0.5);
            if (!mid.equals(previous) && !mid.equals(to)) {
                BrushBackend.dab(mid);
                previous = mid;
            }
        }
    }

    private void stopPainting() {
        painting = false;
        lastDab = null;
        strokeDistance = -1;
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
        if (hit != null && !painting) {
            BrushShape shape = BrushOptions.previewShape();
            if (shape != null) {
                SharedBrushPreviewRenderer.render(rc, shape, Vec3.atLowerCornerOf(hit.blockPos()), 3);
            } else {
                Tool.renderRaycastOverlay(rc, hit.blockPos());
            }
        }
    }

    // Ctrl + middle mouse drag, the same gesture as Axiom's brushes.

    @Override
    public boolean initiateAdjustment() {
        double radius = BrushOptions.radius();
        if (radius < 0) {
            return false;
        }
        adjustRadius[0] = (int) Math.round(radius);
        return SimpleRadiusAdjustment.initiateAdjustment(adjustRadius, adjustBase);
    }

    @Override
    public Vec2 renderAdjustment(float mouseX, float mouseY, Vec2 mouseDelta) {
        int max = Math.max(BrushOptions.maxRadius(), ADJUST_MAX);
        Vec2 result = SimpleRadiusAdjustment.renderAdjustment(mouseX, mouseY, mouseDelta, max, adjustRadius, adjustBase);
        if (Math.round(BrushOptions.radius()) != adjustRadius[0]) {
            BrushOptions.setRadius(adjustRadius[0]);
        }
        return result;
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
        // The tool always starts with the sphere brush; the saved brush is not restored on purpose.
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
