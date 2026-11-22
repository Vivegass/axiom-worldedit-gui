package dev.axiomwegui.brush;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.coremc.internal.CoreMcWorld;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.Tr;
import dev.axiomwegui.journal.WorldCapture;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.List;
import dev.axiomwegui.we.CommandContext;
import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeResult;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WorldEdit brushes for the Axiom brush tool. A brush is set up by running WorldEdit's own
 * {@code //brush ...} command; instead of binding it to the item in hand, the resulting brush is kept here
 * (see the {@code forceBrush} hook in LocalSessionMixin). Painting then calls the brush directly at the block
 * under the Axiom cursor. One stroke (mouse down to mouse up) is one WorldEdit edit, so it is one entry in
 * the undo history.
 */
public final class BrushBackend {
    /** Server thread: true while a //brush command from the addon runs, so forceBrush hands the brush to us. */
    private static final ThreadLocal<Boolean> CAPTURING = ThreadLocal.withInitial(() -> false);

    // Server thread only.
    private static BrushTool tool = null;
    /** Whether the //brush command now running produced a new brush (it may fail before that). */
    private static boolean capturedThisRun = false;
    /** The brush uses an item on the world (//brush apply|paint ... item): its changes are captured. */
    private static boolean usesItems = false;
    private static String toolDescription = null;
    private static EditSession stroke = null;
    private static LocalSession strokeSession = null;

    /** Client-visible summary of the configured brush, or null. */
    private static volatile String summary = null;

    private BrushBackend() {
    }

    public static boolean isCapturing() {
        return CAPTURING.get();
    }

    /** Called from the forceBrush hook on the server thread. */
    public static void captured(BrushTool brushTool) {
        tool = brushTool;
        capturedThisRun = true;
    }

    public static String summary() {
        return summary;
    }

    /**
     * Runs a //brush command and keeps the brush it builds. {@code maskInput} may be empty; otherwise only
     * blocks matching it are changed.
     */
    public static CompletableFuture<WeResult> configure(String commandLine, String maskInput) {
        return DirectBackend.run(commandLine, new DirectBackend.ServerHook() {
            @Override
            public void before(Player actor, LocalSession session) {
                endStroke(session);
                capturedThisRun = false;
                CAPTURING.set(true);
            }

            @Override
            public void after(Player actor, LocalSession session, WeResult result) {
                CAPTURING.set(false);
                // WorldEdit's hint about unbinding a held item does not apply to the Axiom tool.
                result.lines().removeIf(l -> l.text().contains("unbind"));
                if (tool == null || !capturedThisRun) {
                    // The command failed; the previous brush stays as it was.
                    return;
                }
                toolDescription = commandLine;
                usesItems = List.of(commandLine.trim().split("\\s+")).contains("item");
                // Some brushes come with a mask of their own (extinguish only touches fire); the player's mask
                // narrows it further instead of replacing it.
                Mask own = tool.getMask();
                if (maskInput != null && !maskInput.isBlank()) {
                    try {
                        ParserContext context = new ParserContext();
                        context.setActor(actor);
                        context.setWorld(actor.getWorld());
                        context.setExtent(actor.getWorld());
                        context.setSession(session);
                        context.setRestricted(true);
                        Mask player = WorldEdit.getInstance().getMaskFactory().parseFromInput(maskInput, context);
                        tool.setMask(own == null ? player : new MaskIntersection(own, player));
                    } catch (InputParseException e) {
                        result.lines().add(new WeResult.Line(
                            Tr.t("brush.bad_mask", WorldEditText.reduceToText(e.getRichMessage(), actor.getLocale())), true));
                    }
                }
                String text = commandLine + (maskInput == null || maskInput.isBlank() ? "" : "  mask " + maskInput);
                summary = text;
            }
        });
    }

    /** Client thread: paint once at a block. Starts a stroke if none is open. */
    public static void dab(BlockPos target) {
        onServer((actor, session) -> {
            if (tool == null || tool.getBrush() == null) {
                return;
            }
            if (stroke == null) {
                stroke = session.createEditSession(actor);
                strokeSession = session;
                Mask mask = tool.getMask();
                if (mask != null) {
                    Mask existing = stroke.getMask();
                    stroke.setMask(existing == null ? mask : new MaskIntersection(existing, mask));
                }
            }
            ServerLevel level = usesItems && actor.getWorld() instanceof CoreMcWorld world ? world.getWorld() : null;
            if (level != null) {
                WorldCapture.begin(level);
            }
            try {
                tool.getBrush().build(stroke, BlockVector3.at(target.getX(), target.getY(), target.getZ()),
                    tool.getMaterial(), tool.getSize());
                stroke.flushSession();
            } catch (MaxChangedBlocksException e) {
                AxiomWorldEditGui.LOGGER.info("Brush stroke hit WorldEdit's block change limit");
            } finally {
                if (level != null) {
                    addToEdit(stroke, level, WorldCapture.end());
                }
            }
        });
    }

    /**
     * The item brush (and apply/paint with an item) uses the item on the world itself, so whatever grows
     * from it is not in WorldEdit's edit. The blocks that changed are added to the stroke's edit here, so
     * they become part of the undo entry.
     */
    private static void addToEdit(EditSession edit, ServerLevel level, Long2ObjectLinkedOpenHashMap<BlockState> before) {
        FabricAdapter adapter = FabricAdapter.get();
        for (Long2ObjectLinkedOpenHashMap.Entry<BlockState> entry : before.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            BlockState old = entry.getValue();
            BlockState now = level.getBlockState(pos);
            if (!old.equals(now)) {
                edit.getChangeSet().add(new BlockChange(BlockVector3.at(pos.getX(), pos.getY(), pos.getZ()),
                    adapter.fromNativeBlockState(old).toBaseBlock(), adapter.fromNativeBlockState(now).toBaseBlock()));
            }
        }
    }

    /** Client thread: the mouse was released; the stroke becomes one undo entry. */
    public static void finishStroke() {
        onServer((actor, session) -> endStroke(session));
    }

    private static void endStroke(LocalSession session) {
        if (stroke == null) {
            return;
        }
        EditSession done = stroke;
        LocalSession owner = strokeSession != null ? strokeSession : session;
        stroke = null;
        strokeSession = null;
        CommandContext.begin("brush " + (toolDescription == null ? "" : toolDescription), false);
        try {
            owner.remember(done);
        } finally {
            CommandContext.end();
            CommandContext.takeLast();
            done.close();
        }
    }

    private interface ServerAction {
        void run(Player actor, LocalSession session);
    }

    private static void onServer(ServerAction action) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID uuid = mc.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
                if (serverPlayer == null) {
                    return;
                }
                Player actor = FabricAdapter.get().fromNativePlayer(serverPlayer);
                action.run(actor, WorldEdit.getInstance().getSessionManager().get(actor));
            } catch (Throwable t) {
                AxiomWorldEditGui.LOGGER.error("WorldEdit brush failed", t);
            }
        });
    }
}
