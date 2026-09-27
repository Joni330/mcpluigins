package de.casino;

import java.util.*;
import java.util.function.Predicate;

/** Capacity-aware matching: a flexible ingredient must not steal an exact ingredient's item. */
final class RecipeAllocation {
    static <T> int[] match(VirtualStorage<T> storage, List<Predicate<T>> ingredients) {
        List<Integer> units = new ArrayList<>();
        boolean[][] bySlot = new boolean[ingredients.size()][storage.size()];
        for (int slot = 0; slot < storage.size(); slot++) {
            var entry = storage.get(slot);
            if (entry == null) continue;
            boolean useful = false;
            for (int cell = 0; cell < ingredients.size(); cell++) if (ingredients.get(cell) != null) {
                bySlot[cell][slot] = ingredients.get(cell).test(entry.item());
                useful |= bySlot[cell][slot];
            }
            if (useful) for (int n = 0; n < Math.min(ingredients.size(), entry.count()); n++) units.add(slot);
        }
        int[] owners = new int[units.size()]; Arrays.fill(owners, -1);
        boolean[][] accepts = new boolean[ingredients.size()][units.size()];
        for (int cell = 0; cell < ingredients.size(); cell++) if (ingredients.get(cell) != null)
            for (int unit = 0; unit < units.size(); unit++)
                accepts[cell][unit] = bySlot[cell][units.get(unit)];
        for (int cell = 0; cell < ingredients.size(); cell++) if (ingredients.get(cell) != null)
            assign(cell, accepts, owners, new boolean[units.size()]);
        int[] slots = new int[ingredients.size()]; Arrays.fill(slots, -1);
        for (int unit = 0; unit < owners.length; unit++) if (owners[unit] >= 0) slots[owners[unit]] = units.get(unit);
        return slots;
    }
    private static boolean assign(int cell, boolean[][] accepts, int[] owners, boolean[] visited) {
        for (int unit = 0; unit < owners.length; unit++) {
            if (visited[unit] || !accepts[cell][unit]) continue;
            visited[unit] = true;
            if (owners[unit] < 0 || assign(owners[unit], accepts, owners, visited)) {
                owners[unit] = cell; return true;
            }
        }
        return false;
    }
}
