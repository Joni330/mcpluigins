package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Loaded farm chunks only; persistent nine-slot vanilla buffer, no farm chunk tickets. */
final class StorageSenders implements Listener {
    private final JavaPlugin plugin;
    private final StorageTerminals terminals;
    private final StorageTeams teams;
    private final NamespacedKey marker, ownerKey, linkKey, recipe, modeKey, filterKey, overflowKey, idKey;
    private final Set<Location> loaded = new HashSet<>();
    private final Set<Location> failed = new HashSet<>();
    private BukkitTask task;
    private BukkitTask statusTask;
    private final Map<Location, String> statuses = new HashMap<>();
    private final Set<UUID> watching = new HashSet<>();
    StorageSenders(JavaPlugin plugin, StorageTerminals terminals, StorageTeams teams) {
        this.plugin = plugin; this.terminals = terminals; this.teams = teams;
        marker = new NamespacedKey(plugin, "storage_sender");
        ownerKey = new NamespacedKey(plugin, "sender_owner");
        linkKey = new NamespacedKey(plugin, "sender_link");
        recipe = new NamespacedKey(plugin, "storage_sender_recipe");
        modeKey = new NamespacedKey(plugin, "sender_filter_mode");
        filterKey = new NamespacedKey(plugin, "sender_filter_items");
        overflowKey = new NamespacedKey(plugin, "sender_overflow");
        idKey = new NamespacedKey(plugin, "sender_id");
    }
    ItemStack item() {
        ItemStack item = ExchangeMenu.icon(Material.DROPPER, "Lager-Sender");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        meta.lore(List.of(Component.text("Bis zu 16 Items alle 5 Sekunden · 9 Pufferplätze"),
                Component.text("Mit verbundenem Lager-Handy schleichend rechtsklicken.")));
        item.setItemMeta(meta); return item;
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ShapedRecipe craft = new ShapedRecipe(recipe, item());
        craft.shape("DED", "RHR", "DED");
        craft.setIngredient('D', Material.DIAMOND);
        craft.setIngredient('E', Material.ENDER_PEARL);
        craft.setIngredient('R', Material.REDSTONE);
        craft.setIngredient('H', Material.DROPPER);
        Bukkit.removeRecipe(recipe); Bukkit.addRecipe(craft);
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) scan(chunk);
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(recipe));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100, 100);
        statusTask = Bukkit.getScheduler().runTaskTimer(plugin, this::showStatus, 10, 10);
    }
    void disable() {
        if (task != null) task.cancel();
        if (statusTask != null) statusTask.cancel();
        for (UUID id : watching) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendActionBar(Component.empty()); }
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof FilterMenu) p.closeInventory();
        Bukkit.removeRecipe(recipe); loaded.clear();
    }
    private boolean sender(Block block) {
        return block != null && block.getState() instanceof Dropper d && d.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }
    private boolean allowed(Player player, Dropper d) {
        if (player.hasPermission("casino.admin")) return true;
        String owner = d.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try { return owner != null && teams.shares(UUID.fromString(owner), player.getUniqueId()); }
        catch (IllegalArgumentException error) { return false; }
    }
    private void scan(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities())
            if (state instanceof Dropper d && d.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) loaded.add(state.getLocation());
    }
    @EventHandler public void chunkLoad(ChunkLoadEvent e) { scan(e.getChunk()); }
    @EventHandler public void chunkUnload(ChunkUnloadEvent e) {
        loaded.removeIf(l -> l.getWorld().equals(e.getWorld()) && (l.getBlockX() >> 4) == e.getChunk().getX() && (l.getBlockZ() >> 4) == e.getChunk().getZ());
    }
    @EventHandler public void join(PlayerJoinEvent e) { e.getPlayer().discoverRecipe(recipe); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        var meta = e.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return;
        if (!(e.getBlockPlaced().getState() instanceof Dropper d)) { e.setCancelled(true); return; }
        d.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        d.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, e.getPlayer().getUniqueId().toString());
        d.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        d.customName(Component.text("Lager-Sender · Puffer"));
        if (!d.update(false, false)) { e.setCancelled(true); return; }
        failed.remove(d.getLocation()); loaded.add(d.getLocation());
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !sender(e.getClickedBlock()) || e.useInteractedBlock() == Event.Result.DENY) return;
        Dropper d = (Dropper) e.getClickedBlock().getState();
        if (!allowed(e.getPlayer(), d)) { e.setCancelled(true); e.getPlayer().sendMessage("Dieser Sender gehört nicht deinem Team."); return; }
        if (!e.getPlayer().isSneaking() || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getItem() == null || e.getItem().getType().isAir()) {
            e.setCancelled(true);
            if (!d.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                d.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
                if (!d.update(false, false)) { e.getPlayer().sendMessage("Sender konnte nicht gespeichert werden."); return; }
            }
            e.getPlayer().openInventory(new FilterMenu(d).inventory);
            String owner = d.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            String problem = owner == null ? "Sender-Besitzer fehlt." : terminals.senderTargetProblem(UUID.fromString(owner), d.getWorld(), d.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING));
            e.getPlayer().sendMessage("Sender: " + (failed.contains(d.getLocation()) ? "Speicherfehler – bitte Serverlog prüfen."
                    : problem != null ? problem : d.getInventory().isEmpty() ? "Verbunden, Puffer leer."
                    : "Verbunden. Übertragung alle 5 Sekunden; Filter und Überlauf kannst du im Menü einstellen."));
            return;
        }
        String link = terminals.senderLink(e.getPlayer(), e.getItem());
        if (link == null) return;
        e.setCancelled(true);
        if (!link.startsWith(d.getWorld().getUID() + ";")) { e.getPlayer().sendMessage("Sender und Lager müssen in derselben Welt sein."); return; }
        String owner = d.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        String problem = owner == null ? "Sender-Besitzer fehlt." : terminals.senderTargetProblem(UUID.fromString(owner), d.getWorld(), link);
        if (problem != null) { e.getPlayer().sendMessage("Sender nicht verbunden: " + problem); return; }
        // Binding never transfers admin privilege; delivery checks the placing owner's current team.
        d.getPersistentDataContainer().set(linkKey, PersistentDataType.STRING, link);
        e.getPlayer().sendMessage(d.update(false, false) ? "Sender mit dem Handy-Lager verbunden. Hopper können ihn jetzt befüllen." : "Verknüpfen fehlgeschlagen.");
    }
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof FilterMenu menu && menu.current(p) == null) p.closeInventory();
        for (Location location : List.copyOf(loaded)) {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) { loaded.remove(location); continue; }
            if (!sender(location.getBlock())) { loaded.remove(location); continue; }
            if (failed.contains(location)) continue;
            Dropper d = (Dropper) location.getBlock().getState();
            for (var viewer : List.copyOf(d.getInventory().getViewers()))
                if (viewer instanceof Player p && !allowed(p, d)) p.closeInventory();
            String link = d.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
            String owner = d.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
            if (link == null || owner == null) { statuses.put(location, "Verbindung fehlt"); continue; }
            try {
                String problem = terminals.senderTargetProblem(UUID.fromString(owner), d.getWorld(), link);
                if (problem != null) { statuses.put(location, problem); continue; }
                statuses.put(location, d.getInventory().isEmpty() ? "Bereit · Puffer leer" : "Läuft · bis zu 16 Items / 5 Sekunden");
                SenderFilter filter = filter(d);
                int budget = 16;
                Inventory buffer = d.getInventory();
                for (int slot = 0; slot < buffer.getSize() && budget > 0; slot++) {
                    ItemStack stack = buffer.getItem(slot);
                    if (stack == null || stack.getType().isAir()) continue;
                    ItemStack offer = stack.clone(); offer.setAmount(Math.min(budget, stack.getAmount()));
                    int moved = filter.accepts(stack.getType().name()) ? terminals.receive(UUID.fromString(owner), d.getWorld(), link, offer) : 0;
                    // Commit the source decrement before attempting a second destination.
                    if (moved > 0) {
                        ItemStack remainder = stack.clone(); remainder.setAmount(stack.getAmount() - moved);
                        buffer.setItem(slot, remainder.getAmount() == 0 ? null : remainder);
                        budget -= moved;
                    }
                    int extra = offer.getAmount() - moved;
                    if (extra > 0) statuses.put(location, filter.accepts(stack.getType().name()) ? "Lager voll für diese Items" : "Items vom Filter ausgeschlossen");
                    if (extra > 0 && overflowEnabled(d)) {
                        Inventory overflow = overflow(d);
                        if (overflow == null) statuses.put(location, "Überlaufkiste fehlt oder ist nicht geladen");
                        if (overflow != null) {
                            ItemStack surplus = stack.clone(); surplus.setAmount(extra);
                            int left = overflow.addItem(surplus).values().stream().mapToInt(ItemStack::getAmount).sum();
                            int delivered = extra - left;
                            statuses.put(location, left > 0 ? "Überlauf voll für diese Items" : "Läuft · Überlauf aktiv");
                            if (delivered > 0) {
                                ItemStack remainder = stack.clone(); remainder.setAmount(stack.getAmount() - moved - delivered);
                                buffer.setItem(slot, remainder.getAmount() == 0 ? null : remainder);
                                budget -= delivered;
                            }
                        }
                    }
                }
            } catch (RuntimeException error) {
                failed.add(location);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Lager-Sender gestoppt bei " + location, error);
            }
        }
    }
    private void showStatus() {
        Set<UUID> now = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Block target = p.getTargetBlockExact(6);
            if (target == null || !sender(target)) continue;
            Dropper d = (Dropper) target.getState();
            String status = !allowed(p, d) ? "Kein Teamzugriff" : failed.contains(d.getLocation()) ? "Speicherfehler – Admin informieren"
                    : statuses.getOrDefault(d.getLocation(), "Status wird geprüft …");
            p.sendActionBar(Component.text("Lager-Sender · " + status)); now.add(p.getUniqueId());
        }
        for (UUID id : watching) if (!now.contains(id)) { Player p = Bukkit.getPlayer(id); if (p != null) p.sendActionBar(Component.empty()); }
        watching.clear(); watching.addAll(now);
        statuses.keySet().retainAll(loaded);
    }
    private Dropper buffer(Inventory inventory) {
        return inventory.getHolder() instanceof Dropper d && sender(d.getBlock()) ? d : null;
    }
    private SenderFilter filter(Dropper d) {
        var data = d.getPersistentDataContainer();
        String mode = data.getOrDefault(modeKey, PersistentDataType.STRING, "ALL");
        String items = data.getOrDefault(filterKey, PersistentDataType.STRING, "");
        return new SenderFilter(SenderFilter.Mode.valueOf(mode), items.isEmpty() ? List.of() : List.of(items.split(",")));
    }
    private boolean overflowEnabled(Dropper d) {
        return d.getPersistentDataContainer().getOrDefault(overflowKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }
    private Inventory overflow(Dropper d) {
        BlockFace face = ((org.bukkit.block.data.Directional) d.getBlockData()).getFacing();
        Block target = d.getBlock().getRelative(face);
        if (!target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) return null;
        return target.getState() instanceof Chest chest ? chest.getInventory() : null;
    }
    private final class FilterMenu implements InventoryHolder {
        final Location location;
        final String id;
        final Inventory inventory;
        FilterMenu(Dropper d) {
            location = d.getLocation(); id = d.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
            inventory = Bukkit.createInventory(this, 27, Component.text("Sender · Filter & Überlauf"));
            render(d);
        }
        Dropper current(Player p) {
            if (!p.getWorld().equals(location.getWorld()) || p.getLocation().distanceSquared(location.clone().add(.5, .5, .5)) > 64
                    || p.getGameMode() == GameMode.SPECTATOR || !sender(location.getBlock())) return null;
            Dropper d = (Dropper) location.getBlock().getState();
            return id.equals(d.getPersistentDataContainer().get(idKey, PersistentDataType.STRING)) && allowed(p, d) ? d : null;
        }
        void render(Dropper d) {
            inventory.clear();
            for (int i = 9; i < 27; i++) inventory.setItem(i, ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
            SenderFilter filter = filter(d);
            for (int i = 0; i < filter.materials().size(); i++) {
                ItemStack sample = new ItemStack(Material.valueOf(filter.materials().get(i)));
                var meta = sample.getItemMeta(); meta.lore(List.of(Component.text("Filtermuster · Linksklick zum Entfernen")));
                sample.setItemMeta(meta); inventory.setItem(i, sample);
            }
            String mode = switch (filter.mode()) {
                case ALL -> "Alles senden"; case ALLOW -> "Nur diese senden"; case DENY -> "Diese ausschließen";
            };
            inventory.setItem(18, ExchangeMenu.icon(Material.HOPPER, mode + " · Klicken zum Wechseln"));
            ItemStack overflowIcon = ExchangeMenu.icon(Material.CHEST, "Überlauf: " + (overflowEnabled(d) ? "AN" : "AUS") + " · Klicken zum Umschalten");
            var meta = overflowIcon.getItemMeta();
            meta.lore(List.of(Component.text("Kiste direkt vor die Ausgabeseite stellen."),
                    Component.text(overflow(d) == null ? "Keine geladene Kiste an der Ausgabeseite erkannt." : "Überlaufkiste erkannt."),
                    Component.text("Fehlt Platz, bleiben Items im Sender.")));
            overflowIcon.setItemMeta(meta); inventory.setItem(22, overflowIcon);
            inventory.setItem(26, ExchangeMenu.icon(Material.PAPER, "Inventar-Item linksklicken: Muster hinzufügen (ohne Verbrauch)"));
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof FilterMenu menu) {
            boolean cancelled = e.isCancelled(); e.setCancelled(true);
            if (cancelled || !(e.getWhoClicked() instanceof Player p) || e.getClick() != ClickType.LEFT) return;
            Dropper current = menu.current(p);
            if (current == null) return;
            SenderFilter filter = filter(current);
            int slot = e.getRawSlot();
            if (slot >= 27 && slot < e.getView().countSlots()) {
                ItemStack clicked = e.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) return;
                if (filter.materials().size() == 9 && !filter.materials().contains(clicked.getType().name())) { p.sendMessage("Alle neun Filterplätze sind belegt."); return; }
                filter = filter.add(clicked.getType().name());
            } else if (slot >= 0 && slot < 9) filter = filter.remove(slot);
            else if (slot == 18) filter = filter.cycle();
            else if (slot == 22) current.getPersistentDataContainer().set(overflowKey, PersistentDataType.BYTE, (byte) (overflowEnabled(current) ? 0 : 1));
            else return;
            current.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, filter.mode().name());
            current.getPersistentDataContainer().set(filterKey, PersistentDataType.STRING, String.join(",", filter.materials()));
            if (!current.update(false, false)) { p.sendMessage("Filter konnte nicht gespeichert werden."); return; }
            for (Player viewer : Bukkit.getOnlinePlayers()) if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof FilterMenu other && other.location.equals(menu.location)) other.render(current);
            return;
        }
        Dropper d = buffer(e.getView().getTopInventory());
        if (d != null && (!(e.getWhoClicked() instanceof Player p) || !allowed(p, d))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof FilterMenu) { e.setCancelled(true); return; }
        Dropper d = buffer(e.getView().getTopInventory());
        if (d != null && (!(e.getWhoClicked() instanceof Player p) || !allowed(p, d))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void open(InventoryOpenEvent e) {
        Dropper d = buffer(e.getInventory());
        if (d != null && (!(e.getPlayer() instanceof Player p) || !allowed(p, d))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (!sender(e.getBlock())) return;
        e.setCancelled(true);
        Dropper d = (Dropper) e.getBlock().getState();
        if (!e.getPlayer().hasPermission("casino.admin") && !e.getPlayer().getUniqueId().toString().equals(d.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
            e.getPlayer().sendMessage("Nur der Besitzer oder ein Admin darf den Sender abbauen."); return;
        }
        if (!d.getInventory().isEmpty() || !d.getInventory().getViewers().isEmpty()) {
            e.getPlayer().sendMessage("Sender erst leeren und alle Fenster schließen."); return;
        }
        loaded.remove(d.getLocation()); failed.remove(d.getLocation());
        e.getBlock().setType(Material.AIR);
        d.getWorld().dropItemNaturally(d.getLocation().add(.5, .5, .5), item());
    }
    @EventHandler(ignoreCancelled = true) public void dispense(BlockDispenseEvent e) { if (sender(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void dispenseIntoContainer(InventoryMoveItemEvent e) {
        if (buffer(e.getInitiator()) != null) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void explode(EntityExplodeEvent e) { e.blockList().removeIf(this::sender); }
    @EventHandler(ignoreCancelled = true) public void explodeBlock(BlockExplodeEvent e) { e.blockList().removeIf(this::sender); }
    @EventHandler(ignoreCancelled = true) public void piston(BlockPistonExtendEvent e) { if (e.getBlocks().stream().anyMatch(this::sender)) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent e) { if (e.getBlocks().stream().anyMatch(this::sender)) e.setCancelled(true); }
}
