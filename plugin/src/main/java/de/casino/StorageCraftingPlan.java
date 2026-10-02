package de.casino;

import java.util.List;
import java.util.function.BiPredicate;

/** Reserve one ingredient per occupied recipe cell without modifying live storage. */
final class StorageCraftingPlan {
    record Reservation<T>(VirtualStorage<T> storage, VirtualStorage<T> inventory) {}
    /** Keep physical slots separate so results can be applied back to both sources. */
    static <T> VirtualStorage<T> available(VirtualStorage<T> storage, VirtualStorage<T> inventory) {
        VirtualStorage<T> combined = storage.expanded(storage.size() + inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            var entry = inventory.get(i);
            if (entry != null) combined.set(storage.size() + i, entry.item(), entry.count());
        }
        return combined;
    }
    static <T> Reservation<T> reserve(VirtualStorage<T> storage, VirtualStorage<T> inventory,
                                      List<T> cells, BiPredicate<T, T> similar) {
        VirtualStorage<T> combined = reserve(available(storage, inventory), cells, similar);
        if (combined == null) return null;
        VirtualStorage<T> nextStorage = new VirtualStorage<>(storage.size());
        VirtualStorage<T> nextInventory = new VirtualStorage<>(inventory.size());
        for (int i = 0; i < combined.size(); i++) {
            var entry = combined.get(i);
            if (entry == null) continue;
            if (i < storage.size()) nextStorage.set(i, entry.item(), entry.count());
            else nextInventory.set(i - storage.size(), entry.item(), entry.count());
        }
        return new Reservation<>(nextStorage, nextInventory);
    }
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
