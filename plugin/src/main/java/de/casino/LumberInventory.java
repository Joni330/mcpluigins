package de.casino;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/** All planning uses copies. World drops are consumed only after the inventory was saved. */
final class LumberInventory {
    static final int SAPLINGS = 3, SAPLINGS_END = 21, CARGO = 30, END = 246;
    record Move(int index, int amount) {}
    record Pickup(ItemStack[] items, List<Move> moves, boolean full, boolean changed) {
        boolean commit(Predicate<ItemStack[]> save, BiConsumer<Integer,Integer> consume) {
            if (!changed) return true;
            if (!save.test(items)) return false;
            for (Move move : moves) consume.accept(move.index(), move.amount());
            return true;
        }
    }

    static int insert(ItemStack[] items, ItemStack offered) {
        int reserved = LumberRules.SAPLINGS.contains(offered.getType().name())
                ? BotInventory.insert(items, SAPLINGS, SAPLINGS_END, offered) : 0;
        if (reserved == offered.getAmount()) return reserved;
        ItemStack remainder = offered.clone(); remainder.setAmount(offered.getAmount() - reserved);
        return reserved + BotInventory.insert(items, CARGO, END, remainder);
    }

    static int refill(ItemStack[] items) {
        int total = 0;
        for (int slot = CARGO; slot < END; slot++) {
            ItemStack item = items[slot];
            if (BotInventory.empty(item) || !LumberRules.SAPLINGS.contains(item.getType().name())) continue;
            int moved = BotInventory.insert(items, SAPLINGS, SAPLINGS_END, item);
            if (moved == 0) continue;
            total += moved; item.setAmount(item.getAmount() - moved);
            if (BotInventory.empty(item)) items[slot] = null;
        }
        return total;
    }

    static Pickup plan(ItemStack[] current, List<ItemStack> drops) {
        ItemStack[] next = BotInventory.copy(current); boolean changed = refill(next) > 0, full = false;
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i); int moved = insert(next, drop);
            if (moved > 0) { moves.add(new Move(i, moved)); changed = true; }
            if (moved < drop.getAmount()) full = true;
        }
        return new Pickup(next, List.copyOf(moves), full, changed);
    }
}
