package de.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.io.IOException;
import java.util.*;

final class ItemShop implements InventoryHolder {
    record Offer(int slot, Material material, String name, long cents) {}
    static final List<Offer> OFFERS = List.of(
        new Offer(10, Material.COPPER_INGOT, "Kupferbarren", 63),
        new Offer(11, Material.IRON_INGOT, "Eisenbarren", 250),
        new Offer(12, Material.GOLD_INGOT, "Goldbarren", 375),
        new Offer(13, Material.REDSTONE, "Redstone", 500),
        new Offer(14, Material.LAPIS_LAZULI, "Lapislazuli", 500),
        new Offer(15, Material.DIAMOND, "Diamant", 625),
        new Offer(16, Material.NETHERITE_INGOT, "Netheritbarren", 12500),
        new Offer(29, Material.GOLDEN_APPLE, "Goldener Apfel", 15000),
        new Offer(31, Material.ENCHANTED_GOLDEN_APPLE, "Verzauberter goldener Apfel", 1000000),
        new Offer(33, Material.BEACON, "Leuchtfeuer", 15000000),
        new Offer(48, Material.SPAWNER, "Spawner", 35000000),
        new Offer(49, Material.TOTEM_OF_UNDYING, "Totem der Unsterblichkeit", 50000000),
        new Offer(50, Material.SHULKER_SHELL, "Shulkerschale", 50000000));
    final UUID machine;
    private final Inventory inventory;

    ItemShop(UUID machine) {
        this.machine = machine;
        inventory = Bukkit.createInventory(this, 54, Component.text("Casino · Itemshop"));
        for (int i = 0; i < 54; i++) inventory.setItem(i, ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        for (Offer offer : OFFERS) {
            ItemStack icon = ExchangeMenu.icon(offer.material(), offer.name());
            var meta = icon.getItemMeta();
            meta.lore(List.of(Component.text("Preis: " + Money.format(offer.cents()), NamedTextColor.GOLD),
                    Component.text("Linksklick: 1 Stück kaufen", NamedTextColor.GRAY),
                    Component.text("Shift-Linksklick: " + icon.getMaxStackSize() + " Stück für " + Money.format(offer.cents() * icon.getMaxStackSize()), NamedTextColor.GRAY)));
            icon.setItemMeta(meta); inventory.setItem(offer.slot(), icon);
        }
        inventory.setItem(53, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "Zurück zu Erze zu Geld"));
    }
    void buy(int slot, Player player, Accounts accounts, boolean stack) throws IOException {

        Offer offer = OFFERS.stream().filter(o -> o.slot() == slot).findFirst().orElse(null);
        if (offer == null) return;
        ItemStack product = new ItemStack(offer.material());
        ItemStack[] next = Arrays.stream(player.getInventory().getStorageContents())
                .map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new);
        int amount = stack ? product.getMaxStackSize() : 1;
        long price = Math.multiplyExact(offer.cents(), amount);
        int remaining = amount;
        for (int i = 0; i < next.length && remaining > 0; i++) {
            if (next[i] != null && next[i].isSimilar(product)) {
                int moved = Math.min(remaining, Math.max(0, next[i].getMaxStackSize() - next[i].getAmount()));
                next[i].setAmount(next[i].getAmount() + moved);
                remaining -= moved;
            }
        }
        for (int i = 0; i < next.length && remaining > 0; i++) {
            if (next[i] == null || next[i].getType().isAir()) {
                int moved = Math.min(remaining, product.getMaxStackSize());
                next[i] = product.clone(); next[i].setAmount(moved);
                remaining -= moved;
            }
        }
        if (remaining > 0) { player.sendMessage("Nicht genug Platz für den gesamten Kauf. Es wurde nichts abgebucht."); return; }
        accounts.balance(player);
        accounts.debit(player.getUniqueId(), price, "Itemshop: " + amount + " × " + offer.name());

        player.getInventory().setStorageContents(next);

        player.sendMessage(Component.text(amount + " × " + offer.name() + " für " + Money.format(price) + " gekauft.", NamedTextColor.GREEN));
    }
    @Override public Inventory getInventory() { return inventory; }
}

