package dev.axiomwegui.mixin;

import com.moulberry.axiom.world_modification.BlockOrBiomeBuffer;
import com.moulberry.axiom.world_modification.Dispatcher;
import com.moulberry.axiom.world_modification.HistoryBuffer;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches Axiom's private block history, used by the shared undo history. */
@Mixin(value = Dispatcher.class, remap = false)
public interface DispatcherAccessor {
    @Accessor(value = "history", remap = false)
    static HistoryBuffer<BlockOrBiomeBuffer> axiomwegui$getHistory() {
        throw new AssertionError();
    }

    @Invoker(value = "applyPendingHistory", remap = false)
    static void axiomwegui$applyPendingHistory() {
        throw new AssertionError();
    }

    @Invoker(value = "getHistoryPath", remap = false)
    static Path axiomwegui$getHistoryPath() {
        throw new AssertionError();
    }
}
