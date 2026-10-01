package de.casino;

import java.util.*;
import java.util.function.BiPredicate;

/** Immutable entries keep quantities separate from Minecraft's physical stack count. */
final class VirtualStorage<T> {
    static final int LIMIT = 1024;
    record Entry<T>(T item, int count) {
        Entry { Objects.requireNonNull(item); if (count < 1 || count > LIMIT) throw new IllegalArgumentException("Ungültige Lagermenge"); }
    }
    private final List<Entry<T>> slots;
    VirtualStorage(int size) { slots = new ArrayList<>(Collections.nCopies(size, null)); }
    private VirtualStorage(List<Entry<T>> slots) { this.slots = new ArrayList<>(slots); }
    VirtualStorage<T> copy() { return new VirtualStorage<>(slots); }
    VirtualStorage<T> expanded(int size) {
        if (size < size()) throw new IllegalArgumentException("Lager darf nicht verkleinert werden");
        VirtualStorage<T> result = copy();
        result.slots.addAll(Collections.nCopies(size - size(), null));
        return result;
    }
    int size() { return slots.size(); }
    Entry<T> get(int slot) { return slots.get(slot); }
    void set(int slot, T item, int count) { slots.set(slot, count == 0 ? null : new Entry<>(item, count)); }
    int insert(T item, int count, BiPredicate<T,T> similar) {
        if (count < 0) throw new IllegalArgumentException();
        int left = count;
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < size() && left > 0; i++) {
            Entry<T> e = get(i);
            if (pass == 0 && e != null && similar.test(e.item(), item)) {
                int moved = Math.min(left, LIMIT - e.count()); set(i, e.item(), e.count() + moved); left -= moved;
            } else if (pass == 1 && e == null) {
                int moved = Math.min(left, LIMIT); set(i, item, moved); left -= moved;
            }
        }
        return count - left;
    }
    int remove(int slot, int requested) {
        if (requested < 0) throw new IllegalArgumentException();
        Entry<T> e = get(slot); if (e == null) return 0;
        int removed = Math.min(requested, e.count()); set(slot, e.item(), e.count() - removed); return removed;
    }
    void sort(Comparator<Entry<T>> comparator) { slots.sort(Comparator.nullsLast(comparator)); }
}
