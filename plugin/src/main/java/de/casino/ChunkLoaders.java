package de.casino;

import de.casino.ChunkLoaderData.Loader;
import de.casino.ChunkLoaderData.Position;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;

final class ChunkLoaders implements Listener {
    private final JavaPlugin plugin;
    private final StorageTeams teams;
    private final ChunkLoaderData data;
    private final NamespacedKey itemKey, recipe;
    private final Set<Position> pending = new HashSet<>();
    private BukkitTask task;
    private boolean failed;

    private static final class Menu implements InventoryHolder {
        final Loader loader;
        final UUID viewer;
        final Inventory inventory;
        Boolean renderedEnabled;
        Menu(Player player, Loader loader) {
            this.loader = loader; viewer = player.getUniqueId();
            inventory = Bukkit.createInventory(this, 27, Component.text("Chunkloader"));
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    ChunkLoaders(JavaPlugin plugin, StorageTeams teams) throws Exception {
        this.plugin = plugin; this.teams = teams;
        data = new ChunkLoaderData(plugin.getDataFolder().toPath());
        itemKey = new NamespacedKey(plugin, "chunk_loader"); recipe = new NamespacedKey(plugin, "chunk_loader_recipe");
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var crafting = new ShapedRecipe(recipe, item()); crafting.shape("DED", "ELE", "DED");
        crafting.setIngredient('D', Material.DIAMOND); crafting.setIngredient('E', Material.ENDER_PEARL); crafting.setIngredient('L', Material.LODESTONE);
        Bukkit.removeRecipe(recipe); Bukkit.addRecipe(crafting);
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(recipe));
        Objects.requireNonNull(plugin.getCommand("chunkloader")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("Nur im Spiel möglich."); return true; }
            if (args.length != 0) return false;
            if (player.getInventory().firstEmpty() < 0) { player.sendMessage("Bitte einen Inventarplatz freimachen."); return true; }
            player.getInventory().addItem(item()); return true;
        });
        // Delay until all worlds and other Casino services have completed their startup.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
    }
    void disable() {
        if (task != null) task.cancel();
        closeMenus(); PluginChunks.update(plugin, this, Set.of()); Bukkit.removeRecipe(recipe);
    }
    private ItemStack item() {
        ItemStack item = icon(Material.LODESTONE, "Chunkloader", "Hält seinen Chunk (16 × 16) geladen.", "Rechtsklick: An/Aus · auch offline aktiv");
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta); return item;
    }
    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = ExchangeMenu.icon(material, name); var meta = item.getItemMeta();
        meta.lore(Arrays.stream(lore).map(Component::text).toList()); item.setItemMeta(meta); return item;
    }
    private static Position position(Block block) { return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    private NamespacedKey marker(Position p) { return new NamespacedKey(plugin, "chunk_loader_" + (p.x() & 15) + "_" + p.y() + "_" + (p.z() & 15)); }
    private boolean marked(Loader loader, World world) {
        Position p = loader.position();
        if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()) return false;
        return world.getBlockAt(p.x(), p.y(), p.z()).getType() == Material.LODESTONE
                && loader.id().toString().equals(world.getChunkAt(p.x() >> 4, p.z() >> 4).getPersistentDataContainer().get(marker(p), PersistentDataType.STRING));
    }
    private void clearMarker(Loader loader) {
        Position p = loader.position(); World world = Bukkit.getWorld(p.world());
        if (world == null || !world.isChunkLoaded(p.x() >> 4, p.z() >> 4)) return;
        var pdc = world.getChunkAt(p.x() >> 4, p.z() >> 4).getPersistentDataContainer();
        if (loader.id().toString().equals(pdc.get(marker(p), PersistentDataType.STRING))) pdc.remove(marker(p));
    }
    private boolean registered(Block block) { return data.get(position(block)) != null; }
    private boolean access(Player player, Loader loader) {
        return player.hasPermission("casino.admin") || teams.shares(loader.owner(), player.getUniqueId());
    }
    private boolean usable(Player player, Loader expected) {
        Loader current = data.get(expected.position()); Position p = expected.position();
        return !failed && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && current != null && current.id().equals(expected.id()) && !pending.contains(p) && access(player, current)
                && player.getWorld().getUID().equals(p.world()) && player.getWorld().isChunkLoaded(p.x() >> 4, p.z() >> 4)
                && player.getLocation().distanceSquared(new Location(player.getWorld(), p.x() + .5, p.y() + .5, p.z() + .5)) <= 64
                && marked(current, player.getWorld());
    }
    private void failure(Exception error) {
        failed = true; PluginChunks.update(plugin, this, Set.of()); closeMenus();
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Chunkloader angehalten: Daten konnten nicht gespeichert/verarbeitet werden. Nach Beheben des Fehlers neu starten.", error);
    }
    private void closeMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
    }
    private void tick() {
        if (failed) return;
        try {
            PluginChunks.update(plugin, this, ChunkLoaderData.requested(data.all(), pending));
            Set<Position> stale = new HashSet<>(); List<Loader> removed = new ArrayList<>();
            for (Loader loader : data.all()) {
                Position p = loader.position(); World world = Bukkit.getWorld(p.world());
                if (world != null && !pending.contains(p) && world.isChunkLoaded(p.x() >> 4, p.z() >> 4) && !marked(loader, world)) {
                    stale.add(p); removed.add(loader);
                }
            }
            data.removeAll(stale); removed.forEach(this::clearMarker);
            PluginChunks.update(plugin, this, ChunkLoaderData.requested(data.all(), pending));
            for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) {
                if (!usable(player, menu.loader)) player.closeInventory();
                else if (!Objects.equals(menu.renderedEnabled, data.get(menu.loader.position()).enabled())) render(menu, data.get(menu.loader.position()));
            }
        } catch (Exception error) { failure(error); }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        Position p = position(event.getBlockPlaced());
        if (data.get(p) != null) { event.setCancelled(true); return; }
        var meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        if (failed || !event.canBuild() || event.getBlockPlaced().getType() != Material.LODESTONE) {
            event.setCancelled(true); event.getPlayer().sendMessage("Chunkloader kann gerade nicht platziert werden."); return;
        }
        Loader loader = new Loader(UUID.randomUUID(), event.getPlayer().getUniqueId(), p, true);
        try {
            data.put(loader); pending.add(p);
            event.getBlockPlaced().getChunk().getPersistentDataContainer().set(marker(p), PersistentDataType.STRING, loader.id().toString());
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Loader current = data.get(p);
                    if (current == null || !current.id().equals(loader.id())) return;
                    if (event.isCancelled() || !event.canBuild() || !marked(loader, event.getBlockPlaced().getWorld())) {
                        data.remove(p); clearMarker(loader);
                    } else event.getPlayer().sendMessage("Chunkloader aktiv · Chunk " + (p.x() >> 4) + ", " + (p.z() >> 4) + " · Rechtsklick zum Ausschalten.");
                } catch (Exception error) { failure(error); }
                finally { pending.remove(p); }
                tick();
            });
        } catch (Exception error) { event.setCancelled(true); pending.remove(p); failure(error); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.useInteractedBlock() == Event.Result.DENY) return;
        Block block = event.getClickedBlock(); if (block == null) return;
        Loader loader = data.get(position(block)); if (loader == null) return;
        Player player = event.getPlayer();
        if (player.isSneaking() && (!player.getInventory().getItemInMainHand().isEmpty() || !player.getInventory().getItemInOffHand().isEmpty())) return;
        event.setCancelled(true);
        if (failed) { player.sendMessage("Chunkloader wegen eines Speicherfehlers angehalten. Bitte den Admin informieren."); return; }
        if (!access(player, loader)) { player.sendMessage("Dieser Chunkloader gehört einem anderen Spieler/Team."); return; }
        InventoryView previous = player.getOpenInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory() == previous && usable(player, loader)) {
                Menu menu = new Menu(player, loader); render(menu, data.get(loader.position())); player.openInventory(menu.inventory);
            }
        });
    }
    private void render(Menu menu, Loader loader) {
        menu.renderedEnabled = loader.enabled();
        Inventory inventory = menu.inventory;
        ItemStack filler = ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        var p = loader.position();
        inventory.setItem(11, icon(Material.LODESTONE, "Ein Chunk · 16 × 16", "Chunk: " + (p.x() >> 4) + ", " + (p.z() >> 4), "Gesamte Welthöhe · auch offline", "F3 + G zeigt die Chunkgrenzen."));
        inventory.setItem(13, icon(loader.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                loader.enabled() ? "Aktiv · Klicken zum Ausschalten" : "Aus · Klicken zum Einschalten",
                "Zustand bleibt nach einem Neustart erhalten."));
        inventory.setItem(15, icon(Material.BOOK, "Was läuft weiter?", "Öfen, Trichter und Plugin-Maschinen", "Pflanzenwachstum und natürliche Mob-Spawns", "benötigen weiterhin die Vanilla-Bedingungen.", "Andere Loader/Spieler können den Chunk ebenfalls laden."));
        inventory.setItem(26, ExchangeMenu.icon(Material.BARRIER, "Schließen"));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void breaking(BlockBreakEvent event) {
        Loader loader = data.get(position(event.getBlock())); if (loader == null) return;
        if (!loader.owner().equals(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("casino.admin")) {
            event.setCancelled(true); event.getPlayer().sendMessage("Nur der Besitzer darf diesen Chunkloader abbauen.");
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void broken(BlockBreakEvent event) {
        Loader loader = data.get(position(event.getBlock())); if (loader == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            Loader current = data.get(loader.position());
            if (current == null || !current.id().equals(loader.id())) return;
            try { data.remove(loader.position()); clearMarker(loader); tick(); }
            catch (Exception error) { failure(error); }
        });
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void drop(BlockDropItemEvent event) {
        if (!registered(event.getBlock()) || event.getBlockState().getType() != Material.LODESTONE || event.getItems().isEmpty()) return;
        event.getItems().getFirst().setItemStack(item());
        for (int i = event.getItems().size() - 1; i > 0; i--) event.getItems().remove(i).remove();
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean cancelled = event.isCancelled(); event.setCancelled(true);
        if (cancelled || event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (slot == 26 || !usable(player, menu.loader)) { player.closeInventory(); return; }
            if (slot != 13) return;
            Loader current = data.get(menu.loader.position());
            try {
                data.put(current.enabled(!current.enabled())); tick();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .5f, 1f);
            } catch (IOException error) { failure(error); player.sendMessage("Speichern fehlgeschlagen. Chunkloader angehalten; bitte den Admin informieren."); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true); }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(recipe); }
    @EventHandler public void worldLoad(WorldLoadEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (failed) return;
            try {
                // A world can be unloaded/recreated while this plugin keeps running.
                for (var key : ChunkLoaderData.requested(data.all(), pending))
                    if (key.world().equals(event.getWorld().getUID())) PluginChunks.acquire(plugin, this, key);
                tick();
            } catch (Exception error) { failure(error); }
        });
    }
    @EventHandler(ignoreCancelled = true) public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::registered)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::registered)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::registered); }
    @EventHandler(ignoreCancelled = true) public void explode(BlockExplodeEvent event) { event.blockList().removeIf(this::registered); }
}
