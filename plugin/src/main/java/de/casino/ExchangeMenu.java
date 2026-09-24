package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

final class ExchangeMenu implements InventoryHolder {
    final UUID machine;
    final boolean selection;
    static final int EXCHANGE_OPTION = 15;
    static final int CHIPS_OPTION = 11;
    private final Inventory inventory;
    private boolean returned;

    ExchangeMenu(UUID machine) {
        this(machine, true);
    }

    ExchangeMenu(UUID machine, boolean selection) {
        this.machine = machine;
        this.selection = selection;
        inventory = Bukkit.createInventory(this, 27, Component.text(selection ? "Wechselautomat · Auswahl" : "Erze zu Geld"));
        for (int slot = 0; slot < 27; slot++)
            inventory.setItem(slot, icon(slot % 9 == 0 || slot % 9 == 8 ? Material.IRON_BARS : Material.GRAY_STAINED_GLASS_PANE, " "));
        if (selection) {
            inventory.setItem(EXCHANGE_OPTION, icon(Material.GOLD_INGOT, "Erze zu Geld wechseln"));
            inventory.setItem(CHIPS_OPTION, icon(Material.PAPER, "Geld zu Chips wechseln"));
            return;
        }
        inventory.setItem(ExchangeRules.INPUT, null);
        inventory.setItem(ExchangeRules.OUTPUT, null);
        inventory.setItem(ExchangeRules.CONFIRM, icon(Material.LIME_STAINED_GLASS_PANE, "Bestätigen"));
        inventory.setItem(ExchangeRules.CLOSE, icon(Material.RED_STAINED_GLASS_PANE, "Abbrechen"));
    }

    static ItemStack icon(Material material, String name) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    void refresh() {
        if (returned || selection) return;
        ItemStack input = inventory.getItem(ExchangeRules.INPUT);
        boolean valid = input != null && ExchangeRules.accepts(input.getType());
        int payout = valid ? ExchangeRules.payout(input.getType(), input.getAmount()) : 0;
        ItemStack paper = valid ? icon(Material.PAPER, payout > 0 ? Money.format(payout) + " deinem Konto hinzufügen" : "Noch kein Wechselkurs") : null;
        inventory.setItem(ExchangeRules.OUTPUT, paper);
    }

    void exchange(Player player, Accounts accounts) throws java.io.IOException {
        if (returned || selection) return;
        ItemStack input = inventory.getItem(ExchangeRules.INPUT);
        int payout = input == null ? 0 : ExchangeRules.payout(input.getType(), input.getAmount());
        if (payout == 0) return;
        // Auf dem Serverthread synchron: Slot zuerst leeren, bei Speicherfehler zurücksetzen.
        inventory.setItem(ExchangeRules.INPUT, null);
        try {
            accounts.credit(player, payout);
        } catch (java.io.IOException | ArithmeticException error) {
            inventory.setItem(ExchangeRules.INPUT, input);
            refresh();
            throw error;
        }
        refresh();
        player.sendMessage(Component.text("+" + Money.format(payout) + " Casino-Guthaben"));
    }

    void returnInput(Player player) {
        if (returned) return;
        returned = true;
        if (selection) return;
        ItemStack input = inventory.getItem(ExchangeRules.INPUT);
        inventory.setItem(ExchangeRules.INPUT, null);
        inventory.setItem(ExchangeRules.OUTPUT, null);
        if (input != null) player.getInventory().addItem(input).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    @Override public Inventory getInventory() { return inventory; }
}
