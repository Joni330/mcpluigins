package de.casino;

import java.util.List;
import java.util.function.BiPredicate;

/** Reserve one ingredient per occupied recipe cell without modifying live storage. */
final class StorageCraftingPlan {
    static <T> VirtualStorage<T> reserve(VirtualStorage<T> source, List<T> cells, BiPredicate<T, T> similar) {
        VirtualStorage<T> next = source.copy();
        for (T ingredient : cells) {
            if (ingredient == null) continue;
            boolean found = false;
            for (int i = 0; i < next.size(); i++) {
                var entry = next.get(i);
                if (entry != null && similar.test(entry.item(), ingredient)) {
                    next.remove(i, 1);
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }
        return next;
    }
}
