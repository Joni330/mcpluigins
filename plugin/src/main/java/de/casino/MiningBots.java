package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;

/** Persisted tunnel miner; visuals never own inventory or navigation state. */
final class MiningBots implements Listener {
    private static final int FUEL = 2, CARGO = 29, SIZE = 56;
    private final JavaPlugin plugin;
    private final StorageTeams teams;
    private final MiningBotStore store;
    private final NamespacedKey itemKey, partKey, recipeKey;
    private final Map<UUID, Bot> bots = new HashMap<>();
    private final Map<UUID, List<Entity>> visuals = new HashMap<>();
    private final Map<UUID, List<Entity>> terminals = new HashMap<>();
    private boolean stopping;
    private java.util.function.Predicate<Block> extraProtection = block -> false;
    private BotAlerts alerts;
    void extraProtection(java.util.function.Predicate<Block> protection) { extraProtection=protection; }
    void alerts(BotAlerts alerts) { this.alerts=alerts; }
    private static final class Bot {
        final MiningBotStore.Saved base;
        ItemStack[] items;
        boolean failed, unloading;
        MiningBotWork work;
        MiningBotLighting lighting;
        MiningBotVeins veins;
        boolean floorBuilder;
        boolean fluidShield;
        int workTicks;
        String status = "Basis bereit";
        Bot(MiningBotStore.Saved base, ItemStack[] items) { this.base = base; this.items = items; unloading = base.unloading(); work = base.work(); lighting = base.lighting(); veins = base.veins(); floorBuilder = base.floorBuilder(); fluidShield = base.fluidShield(); }
    }
    private static final class Page implements InventoryHolder {
        final UUID player;
        final Bot bot;
        final String mode;
        final Inventory inventory;
        Page(Player player, Bot bot, String mode) {
            this.player = player.getUniqueId(); this.bot = bot; this.mode = mode;
            inventory = Bukkit.createInventory(this, mode.equals("Bot") ? 27 : 36, Component.text("MiningBot · " + mode));
        }
        public Inventory getInventory() { return inventory; }
    }
    MiningBots(JavaPlugin plugin, StorageTeams teams) throws Exception {
        this.plugin = plugin; this.teams = teams; store = new MiningBotStore(plugin.getDataFolder().toPath());
        itemKey = new NamespacedKey(plugin, "miningbot_item"); partKey = new NamespacedKey(plugin, "miningbot_part");
        recipeKey = new NamespacedKey(plugin, "miningbot_recipe");
        Set<String> positions = new HashSet<>();
        for (var saved : store.load()) {
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(saved.items());
            if (items.length != SIZE || !positions.add(saved.world() + ":" + saved.x() + ":" + saved.y() + ":" + saved.z()))
                throw new IOException("Ungültige oder doppelte MiningBot-Basis: " + saved.id());
            bots.put(saved.id(), new Bot(saved, items));
        }
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, item());
        recipe.shape("SCP", " F ", " M ");
        recipe.setIngredient('S', Material.IRON_SHOVEL); recipe.setIngredient('C', Material.CHEST);
        recipe.setIngredient('P', Material.IRON_PICKAXE); recipe.setIngredient('F', Material.FURNACE); recipe.setIngredient('M', Material.MINECART);
        Bukkit.removeRecipe(recipeKey); Bukkit.addRecipe(recipe);
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(recipeKey));
        bots.values().stream().filter(this::loaded).forEach(this::spawn);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }
    ItemStack item() {
        ItemStack item = ExchangeMenu.icon(Material.FURNACE_MINECART, "MiningBot");
        var meta = item.getItemMeta(); meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        meta.lore(List.of(Component.text("Rechtsklick auf Boden: Bot und Basisschiene setzen"),
                Component.text("Terminal: Start / Stop und Rückruf"), Component.text("3×3-Tunnel · Spitzhacke und Schaufel")));
        item.setItemMeta(meta); return item;
    }
    void give(Player player) {
        player.getInventory().addItem(item()).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }
    boolean isPart(Entity entity) { return entity.getPersistentDataContainer().has(partKey, PersistentDataType.STRING); }
    private Bot bot(Entity entity) {
        String id = entity.getPersistentDataContainer().get(partKey, PersistentDataType.STRING);
        if (id == null) return null;
        try { return bots.get(UUID.fromString(id)); } catch (IllegalArgumentException error) { return null; }
    }
    private World world(Bot bot) { return Bukkit.getWorld(bot.base.world()); }
    private boolean loaded(Bot bot) { World world = world(bot); return world != null && world.isChunkLoaded(bot.base.x() >> 4, bot.base.z() >> 4); }
    private Location base(Bot bot) { return new Location(world(bot), bot.base.x(), bot.base.y(), bot.base.z()); }
    private Location position(Bot bot) { return base(bot).add(MiningBotWork.dx(bot.base.yaw()) * bot.work.distance() + .5,
            .12, MiningBotWork.dz(bot.base.yaw()) * bot.work.distance() + .5); }
    private boolean positionLoaded(Bot bot) {
        if (world(bot) == null) return false;
        int x = bot.base.x() + MiningBotWork.dx(bot.base.yaw()) * bot.work.distance();
        int z = bot.base.z() + MiningBotWork.dz(bot.base.yaw()) * bot.work.distance();
        return world(bot).isChunkLoaded(x >> 4, z >> 4);
    }
    private boolean allowed(Player player, Bot bot) { return player.hasPermission("casino.admin") || teams.shares(bot.base.owner(), player.getUniqueId()); }
    private boolean usable(Player player, Bot bot) { return !stopping && !bot.failed && bots.get(bot.base.id()) == bot && loaded(bot)
            && allowed(player, bot) && player.getGameMode() != GameMode.SPECTATOR && player.getWorld() == world(bot)
            && (player.getLocation().distanceSquared(base(bot).add(.5, .5, .5)) <= 64 || player.getLocation().distanceSquared(position(bot)) <= 64); }
    private boolean save(Bot bot, ItemStack[] items) {
        try {
            var b = bot.base;
            store.save(new MiningBotStore.Saved(b.id(), b.owner(), b.world(), b.x(), b.y(), b.z(), b.yaw(), bot.unloading, ItemStack.serializeItemsAsBytes(items), bot.work, bot.lighting, bot.veins, bot.floorBuilder, bot.fluidShield));
            bot.items = items; return true;
        } catch (Exception error) {
            bot.failed = true; bot.status = "Speicherfehler · gesperrt";
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "MiningBot speichern fehlgeschlagen: " + bot.base.id(), error);
            return false;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() != Event.Result.DENY) {
            Bot existing = at(event.getClickedBlock());
            if (existing != null) {
                event.setCancelled(true);
                if (event.getHand() == EquipmentSlot.HAND) open(event.getPlayer(), existing, "Bot");
                return;
            }
        }
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta() || !held.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY || event.useItemInHand() == Event.Result.DENY) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || player.getGameMode() == GameMode.SPECTATOR) return;
        Block clicked = event.getClickedBlock(), rail = clicked.getRelative(BlockFace.UP);
        if (event.getBlockFace() != BlockFace.UP || !clicked.getType().isOccluding() || !rail.getType().isAir()
                || !rail.getRelative(BlockFace.UP).getType().isAir() || at(rail) != null) {
            player.sendMessage("Auf einen vollen Bodenblock klicken; zwei Blöcke darüber müssen frei sein."); return;
        }
        BlockState previous = rail.getState();
        float yaw = Math.round(player.getLocation().getYaw() / 90f) * 90f;
        Rail data = (Rail) Bukkit.createBlockData(Material.POWERED_RAIL);
        data.setShape(Math.floorMod(Math.round(yaw / 90f), 2) == 0 ? Rail.Shape.NORTH_SOUTH : Rail.Shape.EAST_WEST);
        rail.setBlockData(data, false);
        BlockPlaceEvent protection = new BlockPlaceEvent(rail, previous, clicked, held.clone(), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(protection);
        if (protection.isCancelled() || !protection.canBuild() || rail.getType() != Material.POWERED_RAIL) {
            previous.update(true, false); return;
        }
        Bot bot = new Bot(new MiningBotStore.Saved(UUID.randomUUID(), player.getUniqueId(), player.getWorld().getUID(),
                rail.getX(), rail.getY(), rail.getZ(), yaw, false, new byte[0]), new ItemStack[SIZE]);
        try {
            spawn(bot);
            if (!save(bot, bot.items)) throw new IOException("Bot konnte nicht gespeichert werden");
            bots.put(bot.base.id(), bot);
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack remainder = held.clone(); remainder.setAmount(held.getAmount() - 1);
                player.getInventory().setItemInMainHand(remainder.getAmount() == 0 ? null : remainder);
            }
            player.sendMessage("MiningBot mit Basisschiene platziert. Kiste direkt seitlich an die Schiene stellen.");
        } catch (Exception error) {
            despawn(bot); removeTerminal(bot); previous.update(true, false);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "MiningBot platzieren fehlgeschlagen", error);
            player.sendMessage("Bot konnte nicht platziert werden; das Item wurde nicht verbraucht.");
        }
    }
    private void mark(Entity entity, Bot bot) {
        entity.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, bot.base.id().toString());
        entity.setPersistent(false); entity.setInvulnerable(true); entity.setGravity(false);
    }
    private void spawn(Bot bot) {
        if (!loaded(bot)) return;
        if (base(bot).getBlock().getType() != Material.POWERED_RAIL) { bot.status = "Basisschiene fehlt"; return; }
        spawnTerminal(bot);
        if (!positionLoaded(bot) || visuals.containsKey(bot.base.id())) return;
        Location location = position(bot); location.setYaw(bot.base.yaw());
        List<Entity> created = new ArrayList<>(); visuals.put(bot.base.id(), created);
        try {
            PoweredMinecart cart = world(bot).spawn(location, PoweredMinecart.class, e -> {
                mark(e, bot); e.setMaxSpeed(0); e.setSlowWhenEmpty(false); e.customName(Component.text("MiningBot"));
            });
            created.add(cart);
            for (int side = 0; side < 2; side++) {
                final int index = side;
                ItemDisplay display = world(bot).spawn(location, ItemDisplay.class, e -> {
                    mark(e, bot); e.setRotation(bot.base.yaw(), 0);
                    e.setItemStack(new ItemStack(index == 0 ? Material.IRON_PICKAXE : Material.IRON_SHOVEL));
                    e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    e.setTransformation(new Transformation(new Vector3f(index == 0 ? -.72f : .72f, .15f, 0),
                            new Quaternionf().rotateY((float) Math.PI / 2), new Vector3f(.8f), new Quaternionf()));
                });
                created.add(display);
                if (!cart.addPassenger(display)) throw new IllegalStateException("Werkzeuganzeige konnte nicht befestigt werden");
            }
            updateTools(bot);
        } catch (RuntimeException error) { despawn(bot); throw error; }
    }
    private void despawn(Bot bot) { List<Entity> parts = visuals.remove(bot.base.id()); if (parts != null) parts.forEach(Entity::remove); }
    private void removeTerminal(Bot bot) { List<Entity> parts = terminals.remove(bot.base.id()); if (parts != null) parts.forEach(Entity::remove); }
    private void spawnTerminal(Bot bot) {
        if (terminals.containsKey(bot.base.id())) return;
        Location location = base(bot).add(.5 - MiningBotWork.dx(bot.base.yaw()) * .35, 1.05, .5 - MiningBotWork.dz(bot.base.yaw()) * .35);
        location.setYaw(bot.base.yaw());
        List<Entity> parts = new ArrayList<>(); terminals.put(bot.base.id(), parts);
        try {
            parts.add(world(bot).spawn(location, BlockDisplay.class, e -> {
                mark(e, bot); e.addScoreboardTag("miningbot_terminal"); e.setBlock(Bukkit.createBlockData(Material.LECTERN));
                e.setTransformation(new Transformation(new Vector3f(-.25f, 0, -.25f), new Quaternionf(), new Vector3f(.5f), new Quaternionf()));
            }));
            parts.add(world(bot).spawn(location, Interaction.class, e -> {
                mark(e, bot); e.addScoreboardTag("miningbot_terminal"); e.setInteractionWidth(.65f); e.setInteractionHeight(.65f); e.setResponsive(true);
            }));
            parts.add(world(bot).spawn(location.clone().add(0, .8, 0), TextDisplay.class, e -> {
                mark(e, bot); e.addScoreboardTag("miningbot_terminal"); e.text(Component.text("MiningBot-Terminal"));
                e.setBillboard(Display.Billboard.CENTER); e.setViewRange(.3f);
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(.65f), new Quaternionf()));
            }));
        } catch (RuntimeException error) { removeTerminal(bot); throw error; }
    }
    private void moveVisual(Bot bot) {
        if (!positionLoaded(bot)) { despawn(bot); return; }
        List<Entity> parts = visuals.get(bot.base.id());
        if (parts == null) { spawn(bot); return; }
        Location target = position(bot); target.setYaw(bot.base.yaw());
        // Detach briefly because normal Bukkit teleports reject vehicles with passengers.
        Entity cart = parts.getFirst();
        cart.eject();
        if (!cart.teleport(target)) throw new IllegalStateException("Bot-Bewegung wurde verhindert");
        for (int i = 1; i < parts.size(); i++) {
            if (!parts.get(i).teleport(target)) throw new IllegalStateException("Werkzeug-Bewegung wurde verhindert");
            parts.get(i).setRotation(bot.base.yaw(), 0);
            if (!cart.addPassenger(parts.get(i))) throw new IllegalStateException("Werkzeug konnte nicht befestigt werden");
        }
        cart.setVelocity(new org.bukkit.util.Vector());
    }
    private void updateTools(Bot bot) {
        List<Entity> parts = visuals.get(bot.base.id()); if (parts == null || parts.size() != 3) return;
        for (int i = 0; i < 2; i++) ((ItemDisplay) parts.get(i + 1)).setItemStack(BotInventory.empty(bot.items[i])
                ? new ItemStack(i == 0 ? Material.IRON_PICKAXE : Material.IRON_SHOVEL) : bot.items[i].clone());
    }
    @EventHandler public void interact(PlayerInteractEntityEvent event) {
        Bot bot = bot(event.getRightClicked()); if (bot == null) return;
        event.setCancelled(true); if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!usable(player, bot)) { player.sendMessage("Kein Zugriff auf diesen Bot oder Bot gesperrt."); return; }
        if (player.isSneaking() && !event.getRightClicked().getScoreboardTags().contains("miningbot_terminal")) remove(player, event.getRightClicked()); else open(player, bot, "Bot");
    }
    void remove(Player player, Entity entity) {
        Bot bot = bot(entity); if (bot == null || !usable(player, bot)) { player.sendMessage("Dieser Bot ist nicht verfügbar."); return; }
        if (!player.getUniqueId().equals(bot.base.owner()) && !player.hasPermission("casino.admin")) { player.sendMessage("Nur der Besitzer kann den Bot abbauen."); return; }
        if (bot.work.phase() != MiningBotWork.Phase.IDLE || bot.work.distance() != 0 || bot.work.pending() != null) { player.sendMessage("Bot erst zurückrufen und an der Basis anhalten lassen."); return; }
        if (Arrays.stream(bot.items).anyMatch(i -> !BotInventory.empty(i))) { player.sendMessage("Erst Werkzeuge, Brennstoff und Bot-Lager vollständig leeren."); return; }
        try {
            store.remove(bot.base.id()); bots.remove(bot.base.id());
            closeViewers(bot); despawn(bot); removeTerminal(bot);
            Block rail = base(bot).getBlock(); if (rail.getType() == Material.POWERED_RAIL) rail.setType(Material.AIR);
            give(player); player.sendMessage("MiningBot abgebaut.");
        } catch (IOException error) { player.sendMessage("Speicherfehler: Bot bleibt stehen."); }
    }
    private void icon(Page page, int slot, Material material, String name, String... lore) {
        ItemStack item = ExchangeMenu.icon(material, name); var meta = item.getItemMeta();
        meta.lore(Arrays.stream(lore).map(Component::text).toList()); item.setItemMeta(meta); page.inventory.setItem(slot, item);
    }
    private void render(Page page) {
        page.inventory.clear();
        for (int i = 0; i < page.inventory.getSize(); i++) icon(page, i, Material.GRAY_STAINED_GLASS_PANE, " ");
        if (page.mode.equals("Bot")) {
            icon(page, 10, Material.FURNACE, "Brennstoff", "Klicken: Brennstofffach öffnen");
            icon(page, 12, Material.GOLDEN_PICKAXE, "Spitzhacke", "Werkzeug darunter einsetzen", "Im eigenen Inventar anklicken oder mit Cursor einsetzen");
            icon(page, 14, Material.GOLDEN_SHOVEL, "Schaufel", "Werkzeug darunter einsetzen");
            icon(page, 16, Material.CHEST, "Bot-Lager", "27 Plätze · Klicken zum Öffnen");
            page.inventory.setItem(21, cloneItem(botItem(page.bot, 0)));
            page.inventory.setItem(23, cloneItem(botItem(page.bot, 1)));
            icon(page, 2, Material.LIME_WOOL, "Start", "3×3-Tunnel in Platzierungsrichtung abbauen", "Volles Lager: zurück, entladen, weiterarbeiten");
            icon(page, 6, Material.RED_WOOL, "Stop / Zurückrufen", "Sofort zur Basis teleportieren und entladen", "Danach bleibt der Bot an der Basis.");
            icon(page, 18, Material.TORCH, "Fackeln platzieren: " + (page.bot.lighting.enabled() ? "AN" : "AUS"),
                    "Klicken: ein-/ausschalten", "Etwa alle 8 Tunnelblöcke eine Fackel",
                    "Erstellt Fackeln ohne Vorrat", "Vorhandene Fackeln in der Nähe werden übersprungen.");
            icon(page, 4, Material.PAPER, "Status: " + page.bot.status, "Entfernung: " + page.bot.work.distance() + " Blöcke",
                    "Restenergie: " + page.bot.work.energy() + " Abbauschritte");
            icon(page, 26, Material.BARRIER, "Schließen");
            icon(page, 22, Material.BUCKET, "Flüssigkeitsschutz: " + (page.bot.fluidShield ? "AN" : "AUS"),
                    "Klicken: ein-/ausschalten", "Dichtet offene Wände, Decke und Boden ab",
                    "Entfernt Wasser und Lava im Arbeitsbereich · kostenlos");
            icon(page, 24, Material.COBBLESTONE, "Blockerneuerer: " + (page.bot.floorBuilder ? "AN" : "AUS"),
                    "Klicken: ein-/ausschalten", "Füllt Luft in den drei Bodenblöcken unter dem Bot",
                    "Kostenlos · danach werden Fackeln geprüft");
            icon(page, 20, Material.DIAMOND_ORE, "Erzadern abbauen: " + (page.bot.veins.enabled() ? "AN" : "AUS"),
                    "Klicken: ein-/ausschalten", "Boden, Decke, links und rechts prüfen", "Bis 64 Erze pro Fund · maximal 8 Blöcke Entfernung");
        } else {
            int from = page.mode.equals("Brennstoff") ? FUEL : CARGO;
            for (int i = 0; i < 27; i++) page.inventory.setItem(i, cloneItem(page.bot.items[from + i]));
            icon(page, 27, Material.ARROW, "Zurück zum Bot");
            icon(page, 35, Material.BARRIER, "Schließen");
            if (from == CARGO) icon(page, 31, Material.HOPPER, "In Basiskiste entladen", "Auch automatisch bei vollem Bot-Lager.", "Kiste direkt neben der Schiene nötig.");
            else icon(page, 31, Material.COAL, "Brennstoff einlegen", "Brennstoff im eigenen Inventar anklicken.", "1 Kohle / Holzkohle: 64 Abbauschritte (3×3)");
        }
    }
    private ItemStack botItem(Bot bot, int slot) { return bot.items[slot]; }
    private ItemStack cloneItem(ItemStack item) { return BotInventory.empty(item) ? null : item.clone(); }
    private void open(Player player, Bot bot, String mode) {
        if (!usable(player, bot)) return; Page page = new Page(player, bot, mode); render(page); player.openInventory(page.inventory);
    }
    private void refresh(Bot bot) {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Page page && page.bot == bot) render(page);
        updateTools(bot);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        boolean cancelled = event.isCancelled(); event.setCancelled(true);
        if (cancelled || !(event.getWhoClicked() instanceof Player player) || !page.player.equals(player.getUniqueId()) || !usable(player, page.bot)
                || event instanceof InventoryCreativeEvent || !List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT).contains(event.getClick())) return;
        int raw = event.getRawSlot(), size = page.inventory.getSize();
        boolean right = event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT;
        if (raw >= size && event.getClickedInventory() == player.getInventory()) {
            deposit(player, page, player.getInventory().getItem(event.getSlot()), event.getSlot(), -1); return;
        }
        if (raw < 0 || raw >= size) return;
        if (raw == size - 1) { later(player, page, () -> player.closeInventory()); return; }
        if (page.mode.equals("Bot")) {
            if (raw == 2 || raw == 6) {
                if (page.bot.work.pending() != null) { player.sendMessage("Bot muss zuerst einen gespeicherten Abbau abschließen."); return; }
                if (raw == 6) { recallNow(page.bot); refresh(page.bot); return; }
                page.bot.work = page.bot.work.start(player.getUniqueId());
                page.bot.unloading = false;
                if (save(page.bot, page.bot.items)) page.bot.status = "Gestartet";
                refresh(page.bot);
            }
            else if (raw == 18) {
                page.bot.lighting = page.bot.lighting.toggle();
                save(page.bot, page.bot.items); refresh(page.bot);
            }
            else if (raw == 20) { page.bot.veins = page.bot.veins.toggle(); save(page.bot, page.bot.items); refresh(page.bot); }
            else if (raw == 24) { page.bot.floorBuilder = !page.bot.floorBuilder; save(page.bot, page.bot.items); refresh(page.bot); }
            else if (raw == 22) { page.bot.fluidShield = !page.bot.fluidShield; save(page.bot, page.bot.items); refresh(page.bot); }
            else if (raw == 10) later(player, page, () -> open(player, page.bot, "Brennstoff"));
            else if (raw == 16) later(player, page, () -> open(player, page.bot, "Lager"));
            else if (raw == 21 || raw == 23) transferSlot(player, page, raw == 21 ? 0 : 1, right);
        } else if (raw < 27) transferSlot(player, page, (page.mode.equals("Brennstoff") ? FUEL : CARGO) + raw, right);
        else if (raw == 27) later(player, page, () -> open(player, page.bot, "Bot"));
        else if (raw == 31 && page.mode.equals("Lager")) {
            if (page.bot.work.distance() > 0) {
                recallNow(page.bot);
                refresh(page.bot); return;
            }
            page.bot.unloading = true;
            if (save(page.bot, page.bot.items)) unload(page.bot);
            refresh(page.bot); player.sendMessage(page.bot.status);
        }
    }
    private void later(Player player, Page page, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline() && player.getOpenInventory().getTopInventory() == page.inventory && usable(player, page.bot)) task.run(); });
    }
    private void transferSlot(Player player, Page page, int slot, boolean right) {
        if (!BotInventory.empty(player.getItemOnCursor())) { deposit(player, page, player.getItemOnCursor(), -1, slot); return; }
        ItemStack source = page.bot.items[slot]; if (BotInventory.empty(source)) return;
        ItemStack offered = source.clone(); if (right) offered.setAmount(1);
        ItemStack[] destination = BotInventory.copy(player.getInventory().getStorageContents());
        int accepted = BotInventory.insert(destination, 0, destination.length, offered);
        if (accepted == 0) { player.sendMessage("Dein Inventar ist voll."); return; }
        ItemStack[] next = BotInventory.copy(page.bot.items); next[slot].setAmount(source.getAmount() - accepted);
        if (BotInventory.empty(next[slot])) next[slot] = null;
        if (save(page.bot, next)) { player.getInventory().setStorageContents(destination); refresh(page.bot); }
        else player.sendMessage("Speicherfehler: Keine Items entnommen.");
    }
    private void deposit(Player player, Page page, ItemStack source, int playerSlot, int target) {
        if (BotInventory.empty(source)) return;
        int from, to;
        if (page.mode.equals("Bot")) {
            int kind = source.getType().name().endsWith("_PICKAXE") ? 0 : source.getType().name().endsWith("_SHOVEL") ? 1 : -1;
            if (kind < 0 || target >= 0 && kind != target) { player.sendMessage("Hier nur eine Spitzhacke oder Schaufel einsetzen."); return; }
            if (!BotInventory.empty(page.bot.items[kind])) { player.sendMessage("Zuerst das bisherige Werkzeug entnehmen."); return; }
            from = kind; to = kind + 1;
        } else if (page.mode.equals("Brennstoff")) {
            if (!source.getType().isFuel()) { player.sendMessage("Dieses Item ist kein Ofenbrennstoff."); return; }
            from = FUEL; to = CARGO;
        } else { from = CARGO; to = SIZE; }
        ItemStack offered = source.clone(); if (from < FUEL) offered.setAmount(1);
        ItemStack[] next = BotInventory.copy(page.bot.items);
        int accepted = BotInventory.insert(next, from, to, offered);
        if (accepted == 0) { player.sendMessage("Dieses Bot-Fach ist voll."); return; }
        if (!save(page.bot, next)) { player.sendMessage("Speicherfehler: Keine Items eingelagert."); return; }
        ItemStack rest = source.clone(); rest.setAmount(source.getAmount() - accepted);
        if (playerSlot < 0) player.setItemOnCursor(BotInventory.empty(rest) ? null : rest);
        else player.getInventory().setItem(playerSlot, BotInventory.empty(rest) ? null : rest);
        refresh(page.bot);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true);
    }
    private void tick() {
        if (stopping) return;
        updateChunks();
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Page page && !usable(player, page.bot)) player.closeInventory();
        for (Bot bot : List.copyOf(bots.values())) {
            if (bot.failed || !loaded(bot)) continue;
            try {
            if (base(bot).getBlock().getType() != Material.POWERED_RAIL) { bot.status = "Basisschiene fehlt"; refresh(bot); continue; }
            if (bot.work.pending() != null && !recoverMining(bot)) { refresh(bot); continue; }
            List<Entity> existing = visuals.get(bot.base.id());
            if (existing != null && existing.stream().anyMatch(e -> !e.isValid())) despawn(bot);
            if (!visuals.containsKey(bot.base.id())) {
                try { spawn(bot); } catch (RuntimeException error) { bot.failed = true; plugin.getLogger().log(java.util.logging.Level.SEVERE, "Bot-Anzeige fehlgeschlagen", error); continue; }
            }
            List<Entity> parts = visuals.get(bot.base.id());
            if (parts != null && !parts.isEmpty()) {
                parts.getFirst().setVelocity(new org.bukkit.util.Vector()); parts.getFirst().setFireTicks(0);
                if (parts.getFirst().getLocation().distanceSquared(position(bot)) > .04) moveVisual(bot);
            }
            boolean full = true;
            for (int i = CARGO; i < SIZE; i++) full &= !BotInventory.empty(bot.items[i]) && bot.items[i].getAmount() >= Math.min(64, bot.items[i].getMaxStackSize());
            if (full && bot.work.phase() == MiningBotWork.Phase.MINING) returnForCargo(bot);
            else if (full && bot.work.phase() == MiningBotWork.Phase.IDLE) bot.unloading = true;
            switch (bot.work.phase()) {
                case RETURNING -> {
                    int remaining = Math.max(0, bot.work.distance() - 1);
                    int x = bot.base.x() + MiningBotWork.dx(bot.base.yaw()) * remaining;
                    int z = bot.base.z() + MiningBotWork.dz(bot.base.yaw()) * remaining;
                    if (!world(bot).isChunkLoaded(x >> 4, z >> 4)) { bot.status = "Rückfahrt pausiert · Chunk ungeladen"; break; }
                    bot.work = bot.work.homeStep(); bot.status = "Rückfahrt · " + bot.work.distance() + " Blöcke bis Basis";
                    if (save(bot, bot.items)) moveVisual(bot);
                }
                case UNLOADING -> { bot.unloading = true; unload(bot); }
                case MINING -> { if (--bot.workTicks <= 0) mineStep(bot); }
                case IDLE -> { if (bot.unloading) unload(bot); }
            }
            refresh(bot);
            if(alerts!=null) {
                if(BotAlerts.blocked(bot.status)) alerts.report(bot.base.id(),bot.base.owner(),"MiningBot",bot.status);
                else if(bot.status.startsWith("Baue")||bot.status.startsWith("3×3")||bot.status.startsWith("Fahre")) alerts.progressed(bot.base.id());
            }
            } catch (Exception error) {
                bot.failed = true; bot.status = "Bot-Fehler · sicher angehalten";
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "MiningBot angehalten: " + bot.base.id(), error);
            }
        }
        if(alerts!=null)for(Bot bot:bots.values())if(BotAlerts.blocked(bot.status))alerts.report(bot.base.id(),bot.base.owner(),"MiningBot",bot.status);
    }
    private void returnForCargo(Bot bot) {
        bot.work = bot.work.recall(true); bot.unloading = false; bot.status = "Lager voll · Rückfahrt zum Entladen"; save(bot, bot.items);
    }
    private void recallNow(Bot bot) {
        if (bot.work.pending() != null) { bot.status = "Gespeicherten Abbau zuerst abschließen"; return; }
        bot.work = bot.work.recallNow(); bot.unloading = true;
        if (!save(bot, bot.items)) return;
        // Recreate at the loaded base even when the remote bot chunk is no longer loaded.
        despawn(bot); spawn(bot);
        bot.status = "Zur Basis zurückgerufen · entlade";
        unload(bot);
    }
    private boolean recoverMining(Bot bot) {
        var pending = bot.work.pending();
        for (var part : pending.blocks()) if (!world(bot).isChunkLoaded(part.x() >> 4, part.z() >> 4)) {
            bot.status = "Warte auf Chunk für gespeicherten Abbau"; return false;
        }
        // Rewards and consumption were committed before changing the world. Replay without extra drops.
        for (var part : pending.blocks()) {
            Block block = world(bot).getBlockAt(part.x(), part.y(), part.z());
            if (block.getBlockData().getAsString().equals(part.blockData())) block.setBlockData(Bukkit.createBlockData(part.replacement()), true);
        }
        bot.work = bot.work.pending(null);
        return save(bot, bot.items);
    }
    private List<Block> face(Bot bot, int depth) {
        int dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
        List<Block> blocks = new ArrayList<>();
        // Check every chunk first; inspecting a mining face must not force-load chunks.
        for (int side = -1; side <= 1; side++) {
            int x = bot.base.x() + dx * depth + dz * side, z = bot.base.z() + dz * depth - dx * side;
            if (!world(bot).isChunkLoaded(x >> 4, z >> 4)) return null;
        }
        if (bot.base.y() < world(bot).getMinHeight() || bot.base.y() + 2 >= world(bot).getMaxHeight()) return null;
        for (int height = 2; height >= 0; height--) for (int side = -1; side <= 1; side++) {
            Block block = world(bot).getBlockAt(bot.base.x() + dx * depth + dz * side, bot.base.y() + height, bot.base.z() + dz * depth - dx * side);
            if (!world(bot).getWorldBorder().isInside(block.getLocation().add(.5, .5, .5))) return null;
            blocks.add(block);
        }
        return blocks;
    }
    private boolean openForBot(Block block) {
        return block.getType().isAir() || block.isLiquid() || isTorch(block.getType()) || block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE;
    }
    static boolean isTorch(Material material) {
        return switch (material) {
            case TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH, REDSTONE_TORCH, REDSTONE_WALL_TORCH -> true;
            default -> false;
        };
    }
    private void mineStep(Bot bot) {
        Player operator = bot.work.operator() == null ? null : Bukkit.getPlayer(bot.work.operator());
        if (bot.work.operator() == null || (operator == null ? !teams.shares(bot.base.owner(), bot.work.operator()) : !allowed(operator, bot))) {
            bot.status = "Pause · Starter nicht mehr zugriffsberechtigt"; return;
        }
        if (!sealFluids(bot, operator) || !renewFloor(bot, operator)) return;
        placeTorch(bot, operator);
        if (bot.failed) return;
        List<Block> ahead = face(bot, bot.work.distance() + 1);
        if (ahead == null) { bot.status = "Pause · nächster Chunk ungeladen oder Weltgrenze"; return; }
        // Re-check our current layer because gravel or sand can refill an already opened tunnel.
        List<Block> examine = ahead;
        if (bot.work.distance() > 0) {
            List<Block> current = face(bot, bot.work.distance());
            if (current == null) { bot.status = "Pause · Bot-Chunk ungeladen"; return; }
            if (current.stream().anyMatch(block -> !openForBot(block))) examine = current;
        }
        List<Block> solid = examine.stream().filter(block -> !openForBot(block)).toList();
        boolean vein = false;
        // Finish a persisted vein only after physically retracing the tunnel to its discovery layer.
        if (bot.veins.enabled() && bot.work.distance() > 0 && bot.work.distance() >= bot.veins.checkedDepth()) {
            if (bot.veins.remaining().isEmpty() && bot.veins.checkedDepth() < bot.work.distance()) {
                scanVeins(bot);
                if (!save(bot, bot.items)) return;
            }
            if (!bot.veins.remaining().isEmpty()) {
                var pos = bot.veins.remaining().getFirst();
                if (!world(bot).isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) { bot.status = "Erzader pausiert · Chunk ungeladen"; return; }
                Block ore = world(bot).getBlockAt(pos.x(), pos.y(), pos.z());
                if (MiningBotVeins.ore(ore.getType().name()).isEmpty()) { bot.veins = bot.veins.done(); save(bot, bot.items); return; }
                examine = List.of(ore); solid = examine; vein = true;
            }
        }
        if (!solid.isEmpty()) {
        // Index 4 is the geometric centre of the 3x3 face. If empty, clear the remaining rim.
        Block focus = vein ? solid.getFirst() : openForBot(examine.get(4)) ? solid.getFirst() : examine.get(4);
        examine = new ArrayList<>(examine);
        examine.remove(focus); examine.addFirst(focus);
        ItemStack focusTool = null;
        int focusSlot = -1;
        ItemStack[] next = BotInventory.copy(bot.items);
        List<MiningBotWork.Pending> removed = new ArrayList<>();
        for (Block block : examine) {
            if (openForBot(block)) continue;
            if (block.getState() instanceof TileState || protectedBlock(block) || block.getType().getHardness() < 0
                    || block.getType().name().endsWith("_BED") || block.getType() == Material.SPAWNER) {
                bot.status = "Pause · geschützter Block: " + block.getType().name(); return;
            }
            // Lichen is not in either mining tag, but must not obstruct the tunnel.
            int toolSlot = block.getType() == Material.GLOW_LICHEN ? 0
                    : Tag.MINEABLE_SHOVEL.isTagged(block.getType()) ? 1 : Tag.MINEABLE_PICKAXE.isTagged(block.getType()) ? 0 : -1;
            if (toolSlot < 0) { bot.status = "Pause · kein passendes Werkzeug für " + block.getType().name(); return; }
            ItemStack tool = bot.items[toolSlot];
            if (BotInventory.empty(tool)) { bot.status = "Pause · " + (toolSlot == 0 ? "Spitzhacke" : "Schaufel") + " fehlt"; return; }
            if (!correctTier(block.getType(), tool.getType())) { bot.status = "Pause · Werkzeug zu schwach für " + block.getType().name(); return; }
            String original = block.getBlockData().getAsString();
            if (!allowBreak(bot, block, operator)) { bot.status = "Pause · Abbau durch Schutzplugin gesperrt"; return; }
            if (!block.getBlockData().getAsString().equals(original)) { bot.status = "Block geändert · erneut prüfen"; return; }
            removed.add(new MiningBotWork.Pending(block.getX(), block.getY(), block.getZ(), original));
            if (block.equals(focus)) { focusTool = tool; focusSlot = toolSlot; }
            for (ItemStack drop : operator == null ? block.getDrops(tool) : block.getDrops(tool, operator)) {
                if (BotInventory.insert(next, CARGO, SIZE, drop) != drop.getAmount()) { returnForCargo(bot); return; }
            }
        }
            // Events for a later neighbour may have changed an earlier block. Commit only an unchanged face.
            for (var part : removed) if (!world(bot).getBlockAt(part.x(), part.y(), part.z()).getBlockData().getAsString().equals(part.blockData())) {
                bot.status = "Block geändert · gesamte Schicht erneut prüfen"; return;
            }
            int energy = bot.work.energy();
            if (energy == 0) {
                for (int slot = FUEL; slot < CARGO; slot++) {
                    if (BotInventory.empty(next[slot])) continue;
                    int charge = fuelEnergy(next[slot].getType()); if (charge <= 0) continue;
                    boolean lava = next[slot].getType() == Material.LAVA_BUCKET;
                    next[slot].setAmount(next[slot].getAmount() - 1); if (BotInventory.empty(next[slot])) next[slot] = null;
                    if (lava && BotInventory.insert(next, CARGO, SIZE, new ItemStack(Material.BUCKET)) != 1) { returnForCargo(bot); return; }
                    energy = charge; break;
                }
                if (energy == 0) { bot.status = "Pause · Brennstoff fehlt"; return; }
            }
            ItemStack worn = next[focusSlot];
            var meta = worn.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.Damageable damage && !meta.isUnbreakable()) {
                int unbreaking = worn.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1, unbreaking + 1)) == 0) {
                    int durability = damage.hasMaxDamage() ? damage.getMaxDamage() : worn.getType().getMaxDurability();
                    if (damage.getDamage() + 1 >= durability) next[focusSlot] = null;
                    else { damage.setDamage(damage.getDamage() + 1); worn.setItemMeta(damage); }
                }
            }
            var first = removed.getFirst();
            boolean fillFloor = vein && tunnelFloor(bot, focus);
            if (fillFloor) {
                BlockState previous = focus.getState(); focus.setType(Material.COBBLESTONE, false);
                boolean permitted = allowPlace(bot, focus, previous, focus.getRelative(BlockFace.DOWN), Material.COBBLESTONE, operator);
                previous.update(true, false);
                if (!permitted) { bot.status = "Pause · Ersatzboden durch Schutzplugin gesperrt"; return; }
            }
            if (vein) bot.veins = bot.veins.done();
            bot.work = bot.work.energy(energy - 1).pending(new MiningBotWork.Pending(first.x(), first.y(), first.z(), first.blockData(), removed.subList(1, removed.size()), fillFloor ? "minecraft:cobblestone" : "minecraft:air"));
            if (!save(bot, next)) return;
            var sound = focus.getSoundGroup().getBreakSound();
            var particles = focus.getBlockData();
            float hardness = focus.getType().getHardness();
            for (var part : bot.work.pending().blocks()) world(bot).getBlockAt(part.x(), part.y(), part.z()).setBlockData(Bukkit.createBlockData(part.replacement()), true);
            bot.work = bot.work.pending(null);
            if (!save(bot, bot.items)) return;
            world(bot).playSound(focus.getLocation(), sound, .45f, 1f);
            world(bot).spawnParticle(Particle.BLOCK, focus.getLocation().add(.5, .5, .5), 18, .25, .25, .25, particles);
            int efficiency = focusTool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY);
            bot.workTicks = Math.max(1, Math.min(20, (int) Math.ceil(hardness * 2 / (1 + efficiency))));
            bot.status = vein ? "Erzader abbauen · noch " + bot.veins.remaining().size() + " Erze" : "3×3-Schicht abgebaut · " + removed.size() + " Blöcke"; return;
        }
        // Falling entities have not become blocks yet: don't drive into a layer until they settle.
        Location front = base(bot).add(MiningBotWork.dx(bot.base.yaw()) * (bot.work.distance() + 1) + .5, 1.5,
                MiningBotWork.dz(bot.base.yaw()) * (bot.work.distance() + 1) + .5);
        if (!world(bot).getNearbyEntities(front, 1.5, 2, 1.5, e -> e instanceof FallingBlock).isEmpty()) { bot.status = "Warte auf nachfallenden Sand / Kies"; return; }
        bot.work = bot.work.moved(); bot.status = "Fahre vor · " + bot.work.distance() + " Blöcke";
        if (save(bot, bot.items)) { moveVisual(bot); if (renewFloor(bot, operator)) placeTorch(bot, operator); }
    }
    private Block tunnelBlock(Bot bot, int depth, int side, int height) {
        int dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
        int x = bot.base.x() + dx * depth + dz * side, z = bot.base.z() + dz * depth - dx * side;
        int y = bot.base.y() + height;
        if (y < world(bot).getMinHeight() || y >= world(bot).getMaxHeight() || !world(bot).isChunkLoaded(x >> 4, z >> 4)) return null;
        Block block = world(bot).getBlockAt(x,y,z);
        return world(bot).getWorldBorder().isInside(block.getLocation().add(.5,.5,.5)) ? block : null;
    }
    private boolean wet(Block block) {
        return block.isLiquid() || block.getType() == Material.BUBBLE_COLUMN
                || block.getBlockData() instanceof org.bukkit.block.data.Waterlogged data && data.isWaterlogged();
    }
    private boolean sealBlock(Bot bot, Block block, Player operator) {
        if (block == null) { bot.status = "Flüssigkeitsschutz · Bereich nicht verfügbar"; return false; }
        if (!wet(block) && !block.isPassable()) return true;
        if (block.getState() instanceof TileState || protectedBlock(block) || isTorch(block.getType())) {
            bot.status = "Flüssigkeitsschutz · Hindernis beim Abdichten"; return false;
        }
        BlockState previous = block.getState(); block.setType(Material.COBBLESTONE, false);
        if (!allowPlace(bot, block, previous, block.getRelative(BlockFace.UP), Material.COBBLESTONE, operator) || block.getType() != Material.COBBLESTONE) {
            previous.update(true, false); bot.status = "Flüssigkeitsschutz · Abdichten gesperrt"; return false;
        }
        return true;
    }
    private boolean sealFluids(Bot bot, Player operator) {
        if (!bot.fluidShield) return true;
        int first = Math.max(1, bot.work.distance()), last = bot.work.distance() + 1;
        // Close the sides before touching any fluid inside the tunnel.
        for (int depth=first; depth<=last; depth++) for (var offset : MiningBotFluids.shell())
            if (!sealBlock(bot, tunnelBlock(bot,depth,offset.side(),offset.height()), operator)) return false;
        // A temporary front cap moves forward as the normal miner opens the next face.
        for (int side=-1; side<=1; side++) for (int height=0; height<=2; height++) {
            Block cap = tunnelBlock(bot,last+1,side,height);
            if (cap == null) { bot.status = "Flüssigkeitsschutz · Front nicht verfügbar"; return false; }
            if (wet(cap) && !sealBlock(bot,cap,operator)) return false;
        }
        for (int depth=first; depth<=last; depth++) for (int side=-1; side<=1; side++) for (int height=0; height<=2; height++) {
            Block block = tunnelBlock(bot,depth,side,height);
            if (block == null) { bot.status = "Flüssigkeitsschutz · Innenraum nicht verfügbar"; return false; }
            if (!wet(block)) continue;
            if (block.getState() instanceof TileState || protectedBlock(block)) { bot.status = "Flüssigkeitsschutz · geschützter Block"; return false; }
            var dry = block.getBlockData().clone();
            if (dry instanceof org.bukkit.block.data.Waterlogged data) data.setWaterlogged(false);
            else dry = Bukkit.createBlockData(Material.AIR);
            if (!allowMachineChange(bot, block, dry)) { bot.status = "Flüssigkeitsschutz · Trockenlegen gesperrt"; return false; }
            if (operator != null) {
                BlockBreakEvent event = new BlockBreakEvent(block, operator); event.setDropItems(false); event.setExpToDrop(0);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) { bot.status = "Flüssigkeitsschutz · Trockenlegen gesperrt"; return false; }
            }
            block.setBlockData(dry, true);
        }
        return true;
    }
    private boolean renewFloor(Bot bot, Player operator) {
        if (!bot.floorBuilder || bot.work.distance() == 0) return true;
        int dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
        int y = bot.base.y() - 1;
        if (y < world(bot).getMinHeight()) { bot.status = "Blockerneuerer · untere Weltgrenze"; return false; }
        for (int side = -1; side <= 1; side++) {
            int x = bot.base.x() + dx * bot.work.distance() + dz * side;
            int z = bot.base.z() + dz * bot.work.distance() - dx * side;
            if (!world(bot).isChunkLoaded(x >> 4, z >> 4)) { bot.status = "Blockerneuerer · Chunk ungeladen"; return false; }
            Block target = world(bot).getBlockAt(x, y, z);
            if (!target.getType().isAir()) continue;
            if (protectedBlock(target) || !world(bot).getWorldBorder().isInside(target.getLocation().add(.5,.5,.5))) {
                bot.status = "Blockerneuerer · geschützter Boden"; return false;
            }
            BlockState previous = target.getState(); target.setType(Material.COBBLESTONE, false);
            if (!allowPlace(bot, target, previous, target.getRelative(BlockFace.UP), Material.COBBLESTONE, operator) || target.getType() != Material.COBBLESTONE) {
                previous.update(true, false); bot.status = "Blockerneuerer · Platzierung gesperrt"; return false;
            }
        }
        return true;
    }
    private boolean tunnelFloor(Bot bot, Block block) {
        int dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
        int x = block.getX() - bot.base.x(), z = block.getZ() - bot.base.z();
        return block.getY() == bot.base.y() - 1 && x * dx + z * dz > 0 && Math.abs(x * dz - z * dx) <= 1;
    }
    private void scanVeins(Bot bot) {
        int depth = bot.work.distance(), dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
        var centre = new MiningBotVeins.Pos(bot.base.x() + dx * depth, bot.base.y() + 1, bot.base.z() + dz * depth);
        List<MiningBotVeins.Pos> seeds = new ArrayList<>();
        for (int height : new int[]{-2, 2}) for (int side = -1; side <= 1; side++)
            seeds.add(new MiningBotVeins.Pos(centre.x() + dz * side, centre.y() + height, centre.z() - dx * side));
        for (int side : new int[]{-2, 2}) for (int height = -1; height <= 1; height++)
            seeds.add(new MiningBotVeins.Pos(centre.x() + dz * side, centre.y() + height, centre.z() - dx * side));
        var found = MiningBotVeins.find(seeds, centre, dx, dz, p -> {
            World world = world(bot);
            if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight() || !world.isChunkLoaded(p.x() >> 4, p.z() >> 4)
                    || !world.getWorldBorder().isInside(new Location(world, p.x() + .5, p.y() + .5, p.z() + .5))) return "";
            return world.getBlockAt(p.x(), p.y(), p.z()).getType().name();
        });
        bot.veins = new MiningBotVeins(true, depth, found);
    }
    private void placeTorch(Bot bot, Player operator) {
        int distance = bot.work.distance();
        if (!bot.lighting.due(distance)) return;
        int dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
        // Include hanging wall torches at every tunnel height, up to three blocks along the route.
        for (int along = -3; along <= 3; along++) for (int side = -2; side <= 2; side++) {
            int x = bot.base.x() + dx * (distance + along) + dz * side;
            int z = bot.base.z() + dz * (distance + along) - dx * side;
            if (!world(bot).isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int height = 0; height <= 2; height++) if (isTorch(world(bot).getBlockAt(x, bot.base.y() + height, z).getType())) {
                bot.lighting = bot.lighting.placed(distance); save(bot, bot.items); return;
            }
        }
        // Place beside the bot on an existing floor; never replace liquids or force-load a chunk.
        for (int side : new int[]{1, -1}) {
            int x = bot.base.x() + dx * distance + dz * side, z = bot.base.z() + dz * distance - dx * side;
            if (!world(bot).isChunkLoaded(x >> 4, z >> 4)) continue;
            Block target = world(bot).getBlockAt(x, bot.base.y(), z);
            if (isTorch(target.getType())) { bot.lighting = bot.lighting.placed(distance); save(bot, bot.items); return; }
            Block floor = target.getRelative(BlockFace.DOWN);
            if (!target.getType().isAir() || !floor.getType().isOccluding() || protectedBlock(target)) continue;
            BlockState previous = target.getState();
            target.setType(Material.TORCH, false);
            if (!allowPlace(bot, target, previous, floor, Material.TORCH, operator) || target.getType() != Material.TORCH) { previous.update(true, false); continue; }
            bot.lighting = bot.lighting.placed(distance);
            if (!save(bot, bot.items)) previous.update(true, false);
            return;
        }
    }
    private boolean allowBreak(Bot bot, Block block, Player operator) {
        if (!allowMachineChange(bot, block, Material.AIR)) return false;
        if (operator == null) return true;
        BlockBreakEvent event = new BlockBreakEvent(block, operator);
        event.setDropItems(false); event.setExpToDrop(0); Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
    private boolean allowMachineChange(Bot bot, Block block, Material material) {
        return allowMachineChange(bot, block, Bukkit.createBlockData(material));
    }
    private boolean allowMachineChange(Bot bot, Block block, org.bukkit.block.data.BlockData data) {
        List<Entity> parts = visuals.get(bot.base.id());
        if (parts == null || parts.isEmpty() || !parts.getFirst().isValid()) return false;
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(parts.getFirst(), block, data);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
    private boolean allowPlace(Bot bot, Block target, BlockState previous, Block against, Material material, Player operator) {
        // EntityChangeBlockEvent sees the original block, like a natural entity-caused change.
        previous.update(true, false);
        if (!allowMachineChange(bot, target, material)) return false;
        target.setType(material, false);
        if (operator == null) return true;
        BlockPlaceEvent event = new BlockPlaceEvent(target, previous, against, new ItemStack(material), operator, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled() && event.canBuild();
    }
    boolean correctTier(Material block, Material tool) {
        String name = tool.name();
        if (name.startsWith("WOODEN_")) return !Tag.INCORRECT_FOR_WOODEN_TOOL.isTagged(block);
        if (name.startsWith("STONE_")) return !Tag.INCORRECT_FOR_STONE_TOOL.isTagged(block);
        if (name.startsWith("COPPER_")) return !Tag.INCORRECT_FOR_COPPER_TOOL.isTagged(block);
        if (name.startsWith("IRON_")) return !Tag.INCORRECT_FOR_IRON_TOOL.isTagged(block);
        if (name.startsWith("GOLDEN_")) return !Tag.INCORRECT_FOR_GOLD_TOOL.isTagged(block);
        if (name.startsWith("DIAMOND_")) return !Tag.INCORRECT_FOR_DIAMOND_TOOL.isTagged(block);
        return name.startsWith("NETHERITE_") && !Tag.INCORRECT_FOR_NETHERITE_TOOL.isTagged(block);
    }
    static int fuelEnergy(Material material) {
        if (!material.isFuel()) return 0;
        return switch (material) {
            case COAL, CHARCOAL -> 64;
            case COAL_BLOCK -> 640;
            case LAVA_BUCKET -> 800;
            case BLAZE_ROD -> 96;
            case DRIED_KELP_BLOCK -> 160;
            case STICK, BAMBOO -> 4;
            default -> 12;
        };
    }
    private void unload(Bot bot) {
        if (!loaded(bot) || bot.failed || bot.work.distance() != 0 || base(bot).getBlock().getType() != Material.POWERED_RAIL) return;
        boolean cargo = false; for (int i = CARGO; i < SIZE; i++) cargo |= !BotInventory.empty(bot.items[i]);
        if (!cargo) {
            bot.unloading = false;
            if (bot.work.phase() == MiningBotWork.Phase.UNLOADING) bot.work = bot.work.emptied();
            bot.status = bot.work.phase() == MiningBotWork.Phase.MINING ? "Entladen · Arbeit wird fortgesetzt" : "Bot-Lager leer · Basis bereit";
            save(bot, bot.items); return;
        }
        Chest chest = null;
        // Deterministic: north, east, south, west. Double chests use the combined inventory.
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            int x = bot.base.x() + face.getModX(), z = bot.base.z() + face.getModZ();
            if (!world(bot).isChunkLoaded(x >> 4, z >> 4)) continue;
            if (world(bot).getBlockAt(x, bot.base.y(), z).getState() instanceof Chest candidate) {
                if (!candidate.isLocked()) { chest = candidate; break; }
            }
        }
        if (chest == null) { bot.status = "Warte auf Kiste direkt neben Basisschiene"; return; }
        Inventory destination = chest.getInventory();
        for (int slot = CARGO; slot < SIZE; slot++) {
            ItemStack offered = bot.items[slot]; if (BotInventory.empty(offered)) continue;
            Inventory source = Bukkit.createInventory(null, 27);
            source.setContents(Arrays.copyOfRange(BotInventory.copy(bot.items), CARGO, SIZE));
            InventoryMoveItemEvent transfer = new InventoryMoveItemEvent(source, offered.clone(), destination, true);
            Bukkit.getPluginManager().callEvent(transfer);
            if (transfer.isCancelled() || !offered.isSimilar(transfer.getItem()) || transfer.getItem().getAmount() != offered.getAmount()) { bot.status = "Kistentransfer durch Plugin gesperrt"; return; }
            ItemStack[] chestItems = BotInventory.copy(destination.getStorageContents());
            int accepted = BotInventory.insert(chestItems, 0, chestItems.length, offered);
            if (accepted == 0) continue;
            ItemStack[] next = BotInventory.copy(bot.items); next[slot].setAmount(offered.getAmount() - accepted);
            if (BotInventory.empty(next[slot])) next[slot] = null;
            if (!save(bot, next)) return;
            destination.setStorageContents(chestItems); bot.status = "Entlade in Basiskiste …"; return;
        }
        bot.status = "Basiskiste voll · Items bleiben im Bot";
    }
    private Bot at(Block block) {
        for (Bot bot : bots.values()) if (bot.base.world().equals(block.getWorld().getUID()) && bot.base.x() == block.getX()
                && bot.base.y() == block.getY() && bot.base.z() == block.getZ()) return bot;
        return null;
    }
    boolean protectedBlock(Block block) { return at(block) != null || at(block.getRelative(BlockFace.UP)) != null || extraProtection.test(block); }
    @EventHandler(ignoreCancelled = true) public void breakBase(BlockBreakEvent event) {
        if (protectedBlock(event.getBlock())) { event.setCancelled(true); event.getPlayer().sendMessage("Erst den leeren Bot mit Schleichen + Rechtsklick abbauen."); }
    }
    @EventHandler(ignoreCancelled = true) public void physics(BlockPhysicsEvent event) { if (at(event.getBlock()) != null) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }
    @EventHandler(ignoreCancelled = true) public void explode(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedBlock); }
    @EventHandler public void damage(VehicleDamageEvent event) { if (isPart(event.getVehicle())) event.setCancelled(true); }
    @EventHandler public void damage(EntityDamageEvent event) { if (isPart(event.getEntity())) event.setCancelled(true); }
    @EventHandler public void burn(EntityCombustEvent event) { if (isPart(event.getEntity())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void flow(BlockFromToEvent event) {
        Block target = event.getToBlock();
        if (protectedBlock(target)) { event.setCancelled(true); return; }
        for (Bot bot : bots.values()) {
            if (!bot.fluidShield || !bot.base.world().equals(target.getWorld().getUID())) continue;
            int dx = MiningBotWork.dx(bot.base.yaw()), dz = MiningBotWork.dz(bot.base.yaw());
            int x = target.getX()-bot.base.x(), z = target.getZ()-bot.base.z(), depth = x*dx+z*dz;
            if (depth >= Math.max(1,bot.work.distance()) && depth <= bot.work.distance()+1 && Math.abs(x*dz-z*dx)<=1
                    && target.getY()>=bot.base.y() && target.getY()<=bot.base.y()+2) { event.setCancelled(true); return; }
        }
    }
    @EventHandler(ignoreCancelled = true) public void falling(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock falling && protectedBlock(event.getBlock())) {
            event.setCancelled(true);
            if (falling.getDropItem()) event.getBlock().getWorld().dropItemNaturally(falling.getLocation(), new ItemStack(falling.getBlockData().getMaterial()));
            falling.remove();
        }
    }
    @EventHandler public void destroy(VehicleDestroyEvent event) { if (isPart(event.getVehicle())) event.setCancelled(true); }
    @EventHandler public void collision(VehicleEntityCollisionEvent event) { if (isPart(event.getVehicle())) event.setCancelled(true); }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(recipeKey); }
    @EventHandler public void load(ChunkLoadEvent event) {
        if (!stopping) Bukkit.getScheduler().runTask(plugin, () -> {
            for (Bot bot : bots.values()) if (!bot.failed && loaded(bot) && base(bot).getChunk().equals(event.getChunk())) {
                try { spawn(bot); }
                catch (RuntimeException error) { bot.failed = true; plugin.getLogger().log(java.util.logging.Level.SEVERE, "Bot-Anzeige laden fehlgeschlagen", error); }
            }
        });
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void unload(ChunkUnloadEvent event) {
        for (Bot bot : bots.values()) if (bot.base.world().equals(event.getWorld().getUID())) {
            if ((bot.base.x() >> 4) == event.getChunk().getX() && (bot.base.z() >> 4) == event.getChunk().getZ()) {
                closeViewers(bot); despawn(bot); removeTerminal(bot);
            } else if ((position(bot).getBlockX() >> 4) == event.getChunk().getX() && (position(bot).getBlockZ() >> 4) == event.getChunk().getZ()) despawn(bot);
        }
    }
    private void closeViewers(Bot bot) {
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Page page && page.bot == bot) player.closeInventory();
    }
    private void updateChunks() {
        Set<PluginChunks.Key> needed = new HashSet<>();
        for (Bot bot : bots.values()) {
            if (bot.failed || (bot.work.phase() == MiningBotWork.Phase.IDLE && !bot.unloading) || world(bot) == null) continue;
            addChunkArea(needed, bot, bot.base.x(), bot.base.z(), 1);
            if (bot.work.phase() == MiningBotWork.Phase.MINING || bot.work.phase() == MiningBotWork.Phase.RETURNING) {
                int x = bot.base.x() + MiningBotWork.dx(bot.base.yaw()) * bot.work.distance();
                int z = bot.base.z() + MiningBotWork.dz(bot.base.yaw()) * bot.work.distance();
                addChunkArea(needed, bot, x, z, bot.veins.enabled() ? 11 : 4);
            }
            if (bot.work.pending() != null) for (var block : bot.work.pending().blocks()) addChunkArea(needed, bot, block.x(), block.z(), 0);
            if (bot.veins.enabled() && !bot.veins.remaining().isEmpty() && bot.work.distance() >= bot.veins.checkedDepth()) {
                var block = bot.veins.remaining().getFirst(); addChunkArea(needed, bot, block.x(), block.z(), 0);
            }
        }
        PluginChunks.update(plugin, this, needed);
    }
    private void addChunkArea(Set<PluginChunks.Key> needed, Bot bot, int x, int z, int radius) {
        needed.addAll(PluginChunks.area(bot.base.world(), x, z, radius));
    }
    List<MachineOverview.Entry> machineEntries(){return bots.values().stream().map(b->new MachineOverview.Entry(b.base.id().toString(),b.base.owner(),b.base.world(),b.base.x(),b.base.y(),b.base.z(),"Tunnelbot",Material.IRON_PICKAXE,b.status,"Station · Entfernung: "+b.work.distance()+" Blöcke",false)).toList();}
    void disable() { stopping = true; for (Bot bot : bots.values()) { closeViewers(bot); despawn(bot); removeTerminal(bot); } PluginChunks.update(plugin, this, Set.of()); Bukkit.removeRecipe(recipeKey); }
}
