package dev.axiomwegui.ui;

import dev.axiomwegui.we.DirectBackend;
import dev.axiomwegui.we.WeParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.enginehub.piston.inject.Key;

/**
 * The values WorldEdit accepts for an argument type (tree types, shapes, biomes, items, directions...),
 * fetched once per type from WorldEdit's converters and kept for the session.
 */
final class ValueLists {
    /** Types with no fixed list of values; never asked for. */
    private static final Set<String> FREE_TYPES = Set.of(
        "int", "Integer", "double", "Double", "long", "Long", "float", "Float",
        "String", "List<String>", "List<Double>", "List<Integer>",
        "BlockVector3", "List<BlockVector3>", "Vector3", "Path", "ZonedDateTime",
        "Pattern", "Mask", "boolean");

    private static final Map<Key<?>, List<String>> CACHE = new HashMap<>();
    private static final Map<Key<?>, CompletableFuture<List<String>>> LOADING = new HashMap<>();

    private static List<String> items = null;

    private ValueLists() {
    }

    /** The values for this argument, an empty list if it has none, or null while they are being fetched. */
    static List<String> of(WeParam param) {
        if (param.type().equals("BaseItem")) {
            // WorldEdit offers no list for items; take Minecraft's item registry.
            if (items == null) {
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (var id : net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet()) {
                    String text = id.toString();
                    if (!text.equals("minecraft:air")) {
                        ids.add(text);
                    }
                }
                java.util.Collections.sort(ids);
                items = List.copyOf(ids);
            }
            return items;
        }
        Key<?> key = param.key();
        if (key == null || FREE_TYPES.contains(param.type())) {
            return List.of();
        }
        List<String> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<List<String>> loading = LOADING.get(key);
        if (loading == null) {
            LOADING.put(key, DirectBackend.values(key));
            return null;
        }
        if (loading.isDone()) {
            List<String> values = loading.getNow(List.of());
            LOADING.remove(key);
            CACHE.put(key, values == null ? List.of() : values);
            return CACHE.get(key);
        }
        return null;
    }
}
