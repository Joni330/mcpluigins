package de.casino;

import de.casino.XpTankData.Position;
import de.casino.XpTankData.Tank;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;

/** Vanilla cauldrons and inventory icons: no resource pack dependency. */
final class XpTanks implements Listener {
    private final JavaPlugin plugin;
    private final StorageTeams teams;
    private final XpTankData data;
    private final NamespacedKey itemKey, recipe;
    private final Set<Position> pending = new HashSet<>();
    private BukkitTask task;
    private boolean failed;
    private static final class Menu implements InventoryHolder {
        final UUID viewer;
        final Tank original;
        final Inventory inventory;
        Tank rendered;
        int playerPoints = -1, level = -1;
        Menu(Player player, Tank tank) {
            viewer = player.getUniqueId(); original = tank;
            inventory = Bukkit.createInventory(this, 27, Component.text("XP-Tank"));
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    XpTanks(JavaPlugin plugin, StorageTeams teams) throws Exception {
        this.plugin = plugin; this.teams = teams; data = new XpTankData(plugin.getDataFolder().toPath());
        itemKey = new NamespacedKey(plugin, "xp_tank"); recipe = new NamespacedKey(plugin, "xp_tank_recipe");
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var crafting = new ShapedRecipe(recipe, item()); crafting.shape("LGL", "GKG", "LGL");
        crafting.setIngredient('L', Material.LAPIS_BLOCK); crafting.setIngredient('G', Material.GLASS); crafting.setIngredient('K', Material.CAULDRON);
        Bukkit.removeRecipe(recipe); Bukkit.addRecipe(crafting);
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(recipe));
        Objects.requireNonNull(plugin.getCommand("xptank")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("Nur im Spiel möglich."); return true; }
            if (args.length != 0) return false;
            if (player.getInventory().firstEmpty() < 0) { player.sendMessage("Bitte einen Inventarplatz freimachen."); return true; }
            player.getInventory().addItem(item()); return true;
        });
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }
    void disable() { if (task != null) task.cancel(); closeMenus(); Bukkit.removeRecipe(recipe); }
    private ItemStack item() {
        ItemStack item = icon(Material.CAULDRON, "XP-Tank", "Rechtsklick: XP einzahlen und entnehmen", "Persönlich · Lagerteam optional freigeben");
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta); return item;
    }
    private static ItemStack icon(Material material, String name, String... lines) {
        ItemStack item = ExchangeMenu.icon(material, name); var meta = item.getItemMeta();
        meta.lore(Arrays.stream(lines).map(Component::text).toList()); item.setItemMeta(meta); return item;
    }
    private static String number(long points) { return String.format(Locale.GERMANY, "%,d", points); }
    private static Position position(Block block) { return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    private NamespacedKey marker(Position p) { return new NamespacedKey(plugin, "xp_tank_" + (p.x() & 15) + "_" + p.y() + "_" + (p.z() & 15)); }
    private boolean marked(Tank tank, World world) {
        Position p = tank.position();
        return world.getBlockAt(p.x(), p.y(), p.z()).getType() == Material.CAULDRON
                && tank.id().toString().equals(world.getChunkAt(p.x() >> 4, p.z() >> 4).getPersistentDataContainer().get(marker(p), PersistentDataType.STRING));
    }
    private void clearMarker(Tank tank, Block block) {
        var pdc = block.getChunk().getPersistentDataContainer(); NamespacedKey key = marker(tank.position());
        if (tank.id().toString().equals(pdc.get(key, PersistentDataType.STRING))) pdc.remove(key);
    }
    private boolean registered(Block block) { return data.get(position(block)) != null; }
    private boolean owner(Player player, Tank tank) { return tank.owner().equals(player.getUniqueId()) || player.hasPermission("casino.admin"); }
    private boolean access(Player player, Tank tank) { return owner(player, tank) || tank.shared() && teams.shares(tank.owner(), player.getUniqueId()); }
    private boolean usable(Player player, Tank expected) {
        Tank current = data.get(expected.position()); Position p = expected.position();
        return !failed && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && current != null && current.id().equals(expected.id()) && !pending.contains(p) && access(player, current)
                && player.getWorld().getUID().equals(p.world()) && player.getWorld().isChunkLoaded(p.x() >> 4, p.z() >> 4)
                && player.getLocation().distanceSquared(new Location(player.getWorld(), p.x() + .5, p.y() + .5, p.z() + .5)) <= 64
                && marked(current, player.getWorld());
    }
    private void failure(Exception error) {
        failed = true; closeMenus();
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "XP-Tanks gesperrt: Speichern/Verarbeiten fehlgeschlagen. Nach Beheben des Fehlers neu starten.", error);
    }
    private void closeMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
    }
    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) {
            if (!usable(player, menu.original)) player.closeInventory();
            else {
                Tank current = data.get(menu.original.position());
                if (!current.equals(menu.rendered) || player.calculateTotalExperiencePoints() != menu.playerPoints || player.getLevel() != menu.level)
                    render(menu, player, current);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced(); Position p = position(block);
        if (registered(block)) { event.setCancelled(true); return; }
        var meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        if (failed || !event.canBuild() || block.getType() != Material.CAULDRON) { event.setCancelled(true); return; }
        Tank tank = new Tank(UUID.randomUUID(), event.getPlayer().getUniqueId(), p, 0, false, true);
        try {
            data.create(tank); pending.add(p);
            block.getChunk().getPersistentDataContainer().set(marker(p), PersistentDataType.STRING, tank.id().toString());
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (event.isCancelled() || !event.canBuild() || !marked(tank, block.getWorld())) { data.remove(tank); clearMarker(tank, block); }
                    else event.getPlayer().sendMessage("XP-Tank platziert · Rechtsklick zum Einzahlen und Entnehmen.");
                } catch (Exception error) { failure(error); }
                finally { pending.remove(p); }
            });
        } catch (Exception error) { event.setCancelled(true); pending.remove(p); failure(error); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.useInteractedBlock() == Event.Result.DENY) return;
        Block block = event.getClickedBlock(); if (block == null) return;
        Tank tank = data.get(position(block)); if (tank == null) return;
        Player player = event.getPlayer();
        // Leave sneaking block placement to Vanilla, but never permit cauldron filling.
        if (player.isSneaking() && (!player.getInventory().getItemInMainHand().isEmpty() && player.getInventory().getItemInMainHand().getType().isBlock()
                || !player.getInventory().getItemInOffHand().isEmpty() && player.getInventory().getItemInOffHand().getType().isBlock())) return;
        event.setCancelled(true);
        if (failed) { player.sendMessage("XP-Tank wegen eines Speicherfehlers gesperrt. Bitte den Admin informieren."); return; }
        if (!access(player, tank)) { player.sendMessage("Dieser XP-Tank ist privat oder gehört einem anderen Team."); return; }
        InventoryView previous = player.getOpenInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory() == previous && usable(player, tank)) {
                Menu menu = new Menu(player, tank); render(menu, player, data.get(tank.position())); player.openInventory(menu.inventory);
            }
        });
    }
    private void render(Menu menu, Player player, Tank tank) {
        menu.rendered = tank; menu.playerPoints = player.calculateTotalExperiencePoints(); menu.level = player.getLevel();
        ItemStack filler = ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < menu.inventory.getSize(); i++) menu.inventory.setItem(i, filler);
        menu.inventory.setItem(4, icon(Material.EXPERIENCE_BOTTLE, "Gespeichert: " + number(tank.points()) + " XP-Punkte",
                "Deine XP: " + number(menu.playerPoints) + " Punkte · Level " + menu.level,
                "Echte XP-Punkte · keine Umwandlungsverluste", "XP bleiben beim Tod sicher im Tank."));
        int[] amounts = {1, 5, 0};
        for (int i = 0; i < amounts.length; i++) {
            int levels = amounts[i];
            long deposit = XpAmounts.deposit(menu.playerPoints, menu.level, levels, tank.points());
            long withdraw = XpAmounts.withdraw(menu.playerPoints, menu.level, levels, tank.points());
            menu.inventory.setItem(10 + i, icon(Material.LIME_DYE, (levels == 0 ? "Alle XP" : levels + " Level") + " einzahlen",
                    number(deposit) + " XP-Punkte", levels == 0 ? "Deine gesamte verfügbare Erfahrung" : "Bis zum Beginn von Level " + Math.max(0, menu.level - levels), "Linksklick"));
            menu.inventory.setItem(14 + i, icon(Material.ORANGE_DYE, (levels == 0 ? "Alle XP" : "Bis zu " + levels + " Level") + " entnehmen",
                    number(withdraw) + " XP-Punkte", levels == 0 ? "So viel, wie dein XP-Konto aufnehmen kann" : "Bis zum Beginn von Level " + ((long) menu.level + levels), "Linksklick"));
        }
        menu.inventory.setItem(22, icon(tank.shared() ? Material.PLAYER_HEAD : Material.IRON_DOOR,
                tank.shared() ? "Lagerteam: freigegeben" : "Privat: nur Besitzer",
                owner(player, tank) ? "Klicken: Teamfreigabe umschalten" : "Nur der Besitzer kann dies ändern.",
                "Freigabe erlaubt Einzahlen UND Entnehmen."));
        menu.inventory.setItem(18, icon(Material.BOOK, "Bedienung", "1/5 Level: zur angezeigten Levelgrenze", "Bei zu wenig XP wird der verfügbare Rest entnommen.", "Zum Abbauen zuerst vollständig leeren.", "Keine automatische Aufnahme von XP-Kugeln."));
        menu.inventory.setItem(26, ExchangeMenu.icon(Material.BARRIER, "Schließen"));
    }
    @EventHandler(priority = EventPriority.HIGH) public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean cancelled = event.isCancelled(); event.setCancelled(true);
        if (cancelled || event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
            if (slot == 26 || !usable(player, menu.original)) { player.closeInventory(); return; }
            Tank tank = data.get(menu.original.position());
            try {
                if (slot == 22) {
                    if (!owner(player, tank)) { player.sendMessage("Nur der Besitzer darf die Teamfreigabe ändern."); return; }
                    data.sharing(tank, !tank.shared());
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .5f, 1f);
                } else if (slot >= 10 && slot <= 12 || slot >= 14 && slot <= 16) {
                    boolean deposit = slot <= 12; int index = slot - (deposit ? 10 : 14); int levels = index == 0 ? 1 : index == 1 ? 5 : 0;
                    int before = player.calculateTotalExperiencePoints();
                    long amount = deposit ? XpAmounts.deposit(before, player.getLevel(), levels, tank.points()) : XpAmounts.withdraw(before, player.getLevel(), levels, tank.points());
                    if (amount == 0) { player.sendMessage(deposit ? "Keine XP zum Einzahlen verfügbar oder der Tank ist voll." : "Der Tank ist leer oder du hast das XP-Limit erreicht."); render(menu, player, tank); return; }
                    // Persist before changing player XP. Routine write failures leave both balances unchanged.
                    var transfer = data.transfer(tank, before, amount, deposit);
                    player.setExperienceLevelAndProgress(transfer.playerPoints());
                    player.setTotalExperience(transfer.playerPoints());
                    player.saveData();
                    player.sendMessage(number(amount) + " XP-Punkte " + (deposit ? "eingezahlt." : "entnommen."));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .6f, deposit ? .8f : 1.2f);
                } else return;
                tick();
            } catch (IllegalArgumentException error) { player.sendMessage(error.getMessage()); }
            catch (Exception error) { failure(error); player.sendMessage("XP-Tank gesperrt. Bitte den Admin informieren und neu starten."); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true); }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(recipe); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void breaking(BlockBreakEvent event) {
        Tank tank = data.get(position(event.getBlock())); if (tank == null) return;
        if (failed || pending.contains(tank.position()) || !owner(event.getPlayer(), tank) || tank.points() != 0) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(failed ? "XP-Tank wegen eines Speicherfehlers gesperrt." : !owner(event.getPlayer(), tank)
                    ? "Nur der Besitzer darf diesen XP-Tank abbauen." : "Bitte den XP-Tank zuerst vollständig leeren.");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void broken(BlockBreakEvent event) {
        Tank tank = data.get(position(event.getBlock())); if (tank == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            try { data.remove(tank); clearMarker(tank, event.getBlock()); tick(); }
            catch (Exception error) { failure(error); }
        });
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void drop(BlockDropItemEvent event) {
        if (!registered(event.getBlock()) || event.getBlockState().getType() != Material.CAULDRON || event.getItems().isEmpty()) return;
        event.getItems().getFirst().setItemStack(item());
        for (int i = event.getItems().size() - 1; i > 0; i--) event.getItems().remove(i).remove();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void fill(CauldronLevelChangeEvent event) { if (registered(event.getBlock())) event.setCancelled(true); }
    // Bots also fire this event while their owner is offline; protect the tank independently of player events.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void entityChange(EntityChangeBlockEvent event) { if (registered(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::registered)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::registered)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::registered); }
    @EventHandler(ignoreCancelled = true) public void explode(BlockExplodeEvent event) { event.blockList().removeIf(this::registered); }
}
