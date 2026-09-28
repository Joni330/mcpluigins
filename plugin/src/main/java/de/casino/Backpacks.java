package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

final class Backpacks implements Listener {
    private final JavaPlugin plugin;
    private final Accounts accounts;
    private final Map<UUID, Bag> bags = new HashMap<>();
    private boolean stopping;
    private static final class Bag implements InventoryHolder {
        final UUID owner;
        Inventory inventory;
        boolean failed;
        Bag(UUID owner, int rows) { this.owner = owner; resize(rows); }
        void resize(int rows) {
            ItemStack[] old = inventory == null ? new ItemStack[0] : inventory.getContents();
            inventory = Bukkit.createInventory(this, rows * 9, Component.text("Rucksack · " + rows + " Reihen"));
            for (int i = 0; i < old.length; i++) inventory.setItem(i, old[i]);
        }
        public Inventory getInventory() { return inventory; }
    }
    Backpacks(JavaPlugin plugin, Accounts accounts) { this.plugin = plugin; this.accounts = accounts; }
    void enable() {
        long[] euros = {500, 1250, 2500, 5000, 12500};
        long[] previous = {1000, 2500, 5000, 10000, 25000};
        long[] recent = {100, 250, 500, 1000, 2500};
        if (plugin.getConfig().getInt("backpack.price-version", 0) < 3) {
            boolean oldDefaults = true;
            boolean recentDefaults = true;
            for (int i = 0; i < previous.length; i++)
                oldDefaults &= plugin.getConfig().getLong("backpack.upgrade-euros." + (i + 2), -1) == previous[i];
            for (int i = 0; i < recent.length; i++)
                recentDefaults &= plugin.getConfig().getLong("backpack.upgrade-euros." + (i + 2), -1) == recent[i];
            if (oldDefaults || recentDefaults) for (int i = 0; i < euros.length; i++)
                plugin.getConfig().set("backpack.upgrade-euros." + (i + 2), euros[i]);
            plugin.getConfig().set("backpack.price-version", 3);
        }
        for (int i = 0; i < euros.length; i++) plugin.getConfig().addDefault("backpack.upgrade-euros." + (i + 2), euros[i]);
        plugin.getConfig().options().copyDefaults(true); plugin.saveConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("bp")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) open(player); else sender.sendMessage("Bitte im Spiel ausführen.");
            return true;
        });
        plugin.getCommand("bp").setTabCompleter((sender, command, alias, args) -> List.of());
    }
    long price(int rows) {
        long euros = plugin.getConfig().getLong("backpack.upgrade-euros." + (rows + 1));
        if (euros <= 0) throw new IllegalArgumentException("Upgrade-Preis nicht gültig.");
        return Math.multiplyExact(euros, 100);
    }
    private Path path(UUID owner) { return plugin.getDataFolder().toPath().resolve("backpacks").resolve(owner + ".dat"); }
    void open(Player player) {
        if (stopping || player.getGameMode() == GameMode.SPECTATOR) return;
        try {
            int rows = accounts.backpackRows(player.getUniqueId());
            Bag bag = bags.get(player.getUniqueId());
            if (bag == null) {
                bag = new Bag(player.getUniqueId(), rows);
                Path file = path(bag.owner);
                if (Files.exists(file)) {
                    ItemStack[] items = ItemStack.deserializeItemsFromBytes(Files.readAllBytes(file));
                    if (items.length > rows * 9) throw new IOException("Gespeicherter Rucksack ist größer als freigeschaltet");
                    for (int i = 0; i < items.length; i++) bag.inventory.setItem(i, items[i]);
                }
                bags.put(bag.owner, bag);
            }
            if (bag.failed) { player.sendMessage("Rucksack wegen Speicherfehler gesperrt. Bitte einen Admin informieren."); return; }
            if (bag.inventory.getSize() < rows * 9) bag.resize(rows);
            player.openInventory(bag.inventory);
        } catch (Exception error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Rucksack konnte nicht geladen werden", error);
            player.sendMessage("Rucksack konnte nicht geladen werden. Deine gespeicherten Daten werden nicht überschrieben.");
        }
    }
    private void save(Bag bag) {
        try {
            Path target = path(bag.owner); Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "backpack-", ".tmp");
            try {
                Files.write(temp, ItemStack.serializeItemsAsBytes(bag.inventory.getContents()));
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
            bag.failed = false;
        } catch (Exception error) {
            bag.failed = true;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Rucksack speichern fehlgeschlagen: " + bag.owner, error);
            Player player = Bukkit.getPlayer(bag.owner);
            if (player != null) player.sendMessage("Rucksack-Speicherfehler. Bitte einen Admin informieren.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Bag bag)) return;
        if (stopping || bag.failed || !bag.owner.equals(event.getWhoClicked().getUniqueId())
                || event.getWhoClicked().getGameMode() == GameMode.SPECTATOR || event instanceof InventoryCreativeEvent) {
            event.setCancelled(true); return;
        }
        if (!event.isCancelled()) Bukkit.getScheduler().runTask(plugin, () -> save(bag));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Bag bag)) return;
        if (stopping || bag.failed || !bag.owner.equals(event.getWhoClicked().getUniqueId())
                || event.getWhoClicked().getGameMode() == GameMode.SPECTATOR) { event.setCancelled(true); return; }
        if (!event.isCancelled()) Bukkit.getScheduler().runTask(plugin, () -> save(bag));
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Bag bag) save(bag);
    }
    void disable() {
        stopping = true;
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Bag) player.closeInventory();
        bags.values().forEach(this::save);
    }
}
