package de.casino;

import org.bukkit.inventory.ItemStack;

/** Work only on copies; return the amount accepted into normal-size stacks. */
final class BotInventory {
    static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) result[i] = items[i] == null ? null : items[i].clone();
        return result;
    }
    static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
    static int insert(ItemStack[] items, int from, int to, ItemStack offered) {
        int limit = Math.min(64, offered.getMaxStackSize());
        int[] room = new int[to - from]; boolean[] matching = new boolean[room.length];
        for (int i = from; i < to; i++) {
            matching[i - from] = !empty(items[i]) && items[i].isSimilar(offered);
            room[i - from] = empty(items[i]) ? limit : matching[i - from] ? Math.max(0, limit - items[i].getAmount()) : 0;
        }
        int[] moves = plan(offered.getAmount(), room, matching);
        int total = 0;
        for (int i = from; i < to; i++) if (moves[i - from] > 0) {
            if (empty(items[i])) { items[i] = offered.clone(); items[i].setAmount(moves[i - from]); }
            else items[i].setAmount(items[i].getAmount() + moves[i - from]);
            total += moves[i - from];
        }
        return total;
    }
    static int[] plan(int amount, int[] room, boolean[] matching) {
        if (amount < 0 || room.length != matching.length) throw new IllegalArgumentException();
        int[] moves = new int[room.length]; int left = amount;
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < room.length && left > 0; i++) {
            if (matching[i] != (pass == 0)) continue;
            moves[i] = Math.min(left, Math.max(0, room[i])); left -= moves[i];
        }
        return moves;
    }
}
