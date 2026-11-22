package dev.axiomwegui.journal;

import com.moulberry.axiom.world_modification.BlockOrBiomeBuffer;
import com.moulberry.axiom.world_modification.Dispatcher;
import com.moulberry.axiom.world_modification.HistoryBuffer;
import com.moulberry.axiom.world_modification.HistoryEntry;
import com.moulberry.axiom.world_modification.HistoryIO;
import dev.axiomwegui.AxiomWorldEditGui;
import dev.axiomwegui.mixin.DispatcherAccessor;

/**
 * Adds an entry to Axiom's block history without applying it again: the blocks are already in the world,
 * WorldEdit put them there. This is what {@code Dispatcher.push} does, minus the {@code apply} call.
 * Client thread only.
 */
public final class AxiomHistory {
    private static boolean available = true;

    private AxiomHistory() {
    }

    /** False if the Axiom internals the journal relies on were not found; the journal then turns itself off. */
    public static boolean isAvailable() {
        return available;
    }

    public static void push(HistoryEntry<BlockOrBiomeBuffer> entry) {
        try {
            DispatcherAccessor.axiomwegui$applyPendingHistory();
            HistoryBuffer<BlockOrBiomeBuffer> history = DispatcherAccessor.axiomwegui$getHistory();
            int oldSize = history.getSize();
            history.push(entry);
            if (Dispatcher.historyIdentifier != null) {
                HistoryIO.pushEntry(DispatcherAccessor.axiomwegui$getHistoryPath(), entry, history.getPosition(), oldSize);
            }
            Dispatcher.clearActiveSelectionHistory();
        } catch (Throwable t) {
            available = false;
            AxiomWorldEditGui.LOGGER.error("Could not add a WorldEdit edit to the Axiom history; turning the shared history off", t);
        }
    }
}
