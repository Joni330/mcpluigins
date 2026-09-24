package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.io.IOException;
import java.util.*;

final class ChipShop implements InventoryHolder {
    final UUID machine;
    private final Inventory inventory;
    private final Chips chips;
    private final Accounts accounts;
    private final int[] counts = new int[4];
    private boolean purchased;
    ChipShop(UUID machine, Chips chips, Accounts accounts) {
        this.machine = machine; this.chips = chips; this.accounts = accounts;
        inventory = Bukkit.createInventory(this, 54, Component.text("Chips kaufen"));
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        inventory.setItem(28, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "Bestätigen"));
        inventory.setItem(29, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "Abbrechen"));
        refresh();
    }
    private long total() {
        long cents = 0;
        for (int i = 0; i < counts.length; i++) cents += counts[i] * Chips.Kind.values()[i].euros * 100L;
        return cents;
    }
    private void refresh() {
        inventory.setItem(19, ExchangeMenu.icon(Material.PAPER, Money.format(total()) + " in Chips tauschen"));
        for (int i = 0; i < 4; i++) {
            int slot = 15 + i * 9;
            Chips.Kind kind = Chips.Kind.values()[i];
            inventory.setItem(slot - 1, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "+1 Chip (" + kind.euros + " €)"));
            inventory.setItem(slot + 1, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "−1 Chip (" + kind.euros + " €)"));
            ItemStack icon = chips.item(kind);
            icon.setAmount(Math.max(1, counts[i]));
            var meta = icon.getItemMeta();
            meta.lore(List.of(Component.text("Ausgewählt: " + counts[i]), Component.text("Wert: " + Money.format(counts[i] * kind.euros * 100L))));
            icon.setItemMeta(meta); inventory.setItem(slot, icon);
        }
    }
    void click(int slot, Player player) throws IOException {
        if (purchased) return;
        if (slot == 29) { player.closeInventory(); return; }
        for (int i = 0; i < 4; i++) {
            if (slot == 14 + i * 9) { counts[i] = Math.min(64, counts[i] + 1); refresh(); return; }
            if (slot == 16 + i * 9) { counts[i] = Math.max(0, counts[i] - 1); refresh(); return; }
        }
        if (slot != 28) return;
        long cost = total();
        if (cost == 0) { player.sendMessage("Bitte zuerst Chips auswählen."); return; }
        // Simulate delivery first, including partial matching stacks; never drop bought chips on the floor.
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] next = Arrays.stream(contents).map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new);
        for (int i = 0; i < 4; i++) {
            ItemStack chip = chips.item(Chips.Kind.values()[i]);
            int remaining = counts[i];
            for (int j = 0; j < next.length && remaining > 0; j++) {
                if (next[j] != null && next[j].isSimilar(chip)) {
                    int moved = Math.min(remaining, next[j].getMaxStackSize() - next[j].getAmount());
                    next[j].setAmount(next[j].getAmount() + moved); remaining -= moved;
                }
            }
            for (int j = 0; j < next.length && remaining > 0; j++) {
                if (next[j] == null || next[j].getType().isAir()) {
                    int moved = Math.min(remaining, chip.getMaxStackSize());
                    next[j] = chip.clone(); next[j].setAmount(moved); remaining -= moved;
                }
            }
            if (remaining > 0) { player.sendMessage("Nicht genug Platz im Inventar. Es wurde nichts abgebucht."); return; }
        }
        accounts.balance(player);
        accounts.debit(player.getUniqueId(), cost);
        purchased = true;
        player.getInventory().setStorageContents(next);
        player.closeInventory();
        player.sendMessage(Component.text("Chips für " + Money.format(cost) + " gekauft."));
    }
    @Override public Inventory getInventory() { return inventory; }
}
