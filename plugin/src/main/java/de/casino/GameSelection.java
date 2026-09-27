package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

final class GameSelection implements InventoryHolder {
    final UUID machine;
    private final Inventory inventory;
    GameSelection(UUID machine) {
        this.machine = machine;
        inventory = Bukkit.createInventory(this, 54, Component.text("Casino · Spiel auswählen"));
        for (int slot = 0; slot < 54; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8)
                inventory.setItem(slot, ExchangeMenu.icon(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        inventory.setItem(10, ExchangeMenu.icon(Material.PAPER, "Spielautomat · Drei gleiche Symbole"));
        inventory.setItem(11, ExchangeMenu.icon(Material.PAPER, "Roulette · Rot, Schwarz oder Grün"));
        inventory.setItem(12, ExchangeMenu.icon(Material.PAPER, "Fünf Walzen"));
        inventory.setItem(53, ExchangeMenu.icon(Material.RED_STAINED_GLASS_PANE, "Schließen"));
    }
    @Override public Inventory getInventory() { return inventory; }
}


