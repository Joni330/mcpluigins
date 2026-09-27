package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.*;
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
import java.util.*;
import java.util.Comparator;

/** Shared, bounded inventories persisted as full item NBT on their barrel block. */
final class StorageTerminals implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final StorageTeams teams;
    private final NamespacedKey storageOwner;
    private final NamespacedKey marker, contents, recipe, identity, phoneKey, phoneRecipe, ownerKey, linkKey;
    private final Map<Location, Store> opened = new HashMap<>();
    private final NamespacedKey virtualItems, virtualCounts, legacyBackup;
    private final Map<UUID, UUID> remoteUsers = new HashMap<>();
    private boolean stopping;
    private StorageSenders senders;
    private record SearchRequest(Store store, boolean remote) {}
    private record SearchCursor(UUID store, String query, int slot) {}
    private final Map<UUID, SearchRequest> searching = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, SearchCursor> searches = new HashMap<>();
    private final Map<UUID, Craft> recipeSearches = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class Store {
        final Location location;
        final UUID id;
        final List<Page> pages = new ArrayList<>();
        final List<Craft> crafts = new ArrayList<>();
        VirtualStorage<ItemStack> data = new VirtualStorage<>(450);
        boolean failed;
        Store(Location location, UUID id) {
            this.location = location;
            this.id = id;
            for (int i = 0; i < 10; i++) pages.add(new Page(this, i));
        }
        List<org.bukkit.entity.HumanEntity> viewers() {
            return java.util.stream.Stream.concat(pages.stream().map(p -> p.inventory),
                    crafts.stream().map(c -> c.inventory)).flatMap(i -> i.getViewers().stream()).distinct().toList();
        }
        ItemStack[] items() {
            ItemStack[] items = new ItemStack[450];
            for (int i = 0; i < items.length; i++) items[i] = data.get(i) == null ? null : data.get(i).item();
            return items;
        }
    }
    private static final class Page implements InventoryHolder {
        final Store store;
        final int index;
        final Inventory inventory;
        Page(Store store, int index) {
            this.store = store; this.index = index;
            inventory = Bukkit.createInventory(this, 54, Component.text("Lager · Seite " + (index + 1) + "/10"));
            for (int i = 45; i < 54; i++) control(i, Material.GRAY_STAINED_GLASS_PANE, " ");
            control(46, Material.COMPASS, "Suchen · Namen im Chat eingeben");
            control(47, Material.HOPPER, "Sortieren · Links: Itemtyp / Rechts: Stapelgröße");
            control(48, Material.CRAFTING_TABLE, "Werkbank · Zutaten aus diesem Lager");
            control(51, Material.SPYGLASS, "Nächster Suchtreffer");
            if (index > 0) control(45, Material.ARROW, "Vorherige Seite");
            control(49, Material.PAPER, "Seite " + (index + 1) + "/10 · 450 Lagerplätze");
            if (index < 9) control(53, Material.ARROW, "Nächste Seite");
        }
        private void control(int slot, Material material, String name) {
            ItemStack icon = ExchangeMenu.icon(material, name);
            var meta = icon.getItemMeta();
            meta.setMaxStackSize(1); // A single full icon cannot receive shift-clicked items.
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final int[] CRAFT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final class Craft implements InventoryHolder {
        final Store store;
        final boolean remote;
        final ItemStack[] matrix = new ItemStack[9];
        final Map<Integer, ItemStack> choices = new HashMap<>();
        Inventory inventory;
        int selecting = -1, page;
        boolean book;
        int recipePage;
        String query = "";
        List<StorageRecipes.Entry> recipes = List.of();
        StorageRecipes.Entry selectedRecipe;
        Craft(Store store, boolean remote) { this.store = store; this.remote = remote; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private void showCraft(Player player, Craft craft) {
        if (!canUse(player, craft.store, craft.remote)) { player.closeInventory(); return; }
        craft.inventory = Bukkit.createInventory(craft, 54, Component.text(craft.selecting < 0
                ? "Lager · Werkbank" : "Zutat wählen · Seite " + (craft.page + 1) + "/10"));
        Inventory inventory = craft.inventory;
        craft.choices.clear();
        for (int i = 0; i < 54; i++) inventory.setItem(i, ExchangeMenu.icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        if (craft.book) {
            int pages = Math.max(1, (craft.recipes.size() + 44) / 45);
            craft.recipePage = Math.clamp(craft.recipePage, 0, pages - 1);
            for (int i = 0; i < 45; i++) {
                int index = craft.recipePage * 45 + i;
                inventory.setItem(i, null);
                if (index >= craft.recipes.size()) continue;
                var recipe = craft.recipes.get(index);
                var selection = recipe.select(craft.store.data);
                ItemStack icon = recipe.recipe().getResult().clone();
                var meta = icon.getItemMeta();
                var color = selection.available() ? net.kyori.adventure.text.format.NamedTextColor.GREEN : net.kyori.adventure.text.format.NamedTextColor.RED;
                Component name = meta.hasDisplayName() ? meta.displayName() : Component.translatable(icon.getType().translationKey());
                meta.displayName(name.color(color));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(selection.available() ? "Herstellbar · Klicken: Rezept übernehmen" : "Zutaten fehlen · Klicken: Rezept ansehen", color));
                for (ItemStack missing : selection.missing()) lore.add(Component.text("Fehlt: 1× ", color).append(Component.translatable(missing.getType().translationKey())));
                lore.add(Component.text(recipe.key(), net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
                meta.lore(lore); meta.setEnchantmentGlintOverride(selection.available()); icon.setItemMeta(meta);
                inventory.setItem(i, icon);
            }
            if (craft.recipePage > 0) inventory.setItem(45, ExchangeMenu.icon(Material.ARROW, "Vorherige Seite"));
            inventory.setItem(46, ExchangeMenu.icon(Material.COMPASS, "Rezept suchen · Rechtsklick: Suche löschen"));
            inventory.setItem(48, ExchangeMenu.icon(Material.PAPER, "Rezepte " + (craft.recipePage + 1) + "/" + pages + " · " + craft.query));
            inventory.setItem(49, ExchangeMenu.icon(Material.BARRIER, "Zurück zur Werkbank"));
            if (craft.recipePage + 1 < pages) inventory.setItem(53, ExchangeMenu.icon(Material.ARROW, "Nächste Seite"));
        } else if (craft.selecting >= 0) {
            for (int i = 0; i < 45; i++) {
                var entry = craft.store.data.get(craft.page * 45 + i);
                inventory.setItem(i, null);
                if (entry == null) continue;
                craft.choices.put(i, entry.item().clone());
                ItemStack icon = entry.item().clone();
                var meta = icon.getItemMeta();
                var lore = meta.lore() == null ? new ArrayList<Component>() : new ArrayList<>(meta.lore());
                lore.add(Component.text("Im Lager: " + entry.count() + " · Als Vorlage wählen"));
                meta.lore(lore); icon.setItemMeta(meta); inventory.setItem(i, icon);
            }
            if (craft.page > 0) inventory.setItem(45, ExchangeMenu.icon(Material.ARROW, "Vorherige Seite"));
            inventory.setItem(49, ExchangeMenu.icon(Material.BARRIER, "Zurück zur Werkbank"));
            if (craft.page < 9) inventory.setItem(53, ExchangeMenu.icon(Material.ARROW, "Nächste Seite"));
        } else {
            for (int i = 0; i < 9; i++) inventory.setItem(CRAFT_SLOTS[i], craft.matrix[i] == null
                    ? ExchangeMenu.icon(Material.WHITE_STAINED_GLASS_PANE, "Zutat wählen · Rechtsklick: leeren") : craft.matrix[i].clone());
            ItemStack result = craftingResult(craft);
            inventory.setItem(24, result == null ? ExchangeMenu.icon(Material.BARRIER, "Noch kein gültiges Rezept") : result);
            inventory.setItem(33, ExchangeMenu.icon(Material.LIME_STAINED_GLASS_PANE, "Herstellen · Shift-Linksklick: bis zu 64 Durchläufe"));
            inventory.setItem(45, ExchangeMenu.icon(Material.ARROW, "Zurück zum Lager"));
            inventory.setItem(49, ExchangeMenu.icon(Material.BOOK, "Rezeptbuch · Rezept suchen und automatisch befüllen"));
            inventory.setItem(53, ExchangeMenu.icon(Material.BARRIER, "Vorlage leeren"));
        }
        craft.store.location.getChunk().addPluginChunkTicket(plugin);
        player.openInventory(inventory);
        if (craft.book && player.getOpenInventory().getTopInventory() == inventory)
            player.getOpenInventory().setTitle("Lager · Rezeptbuch");
        if (player.getOpenInventory().getTopInventory() == inventory && craft.remote)
            remoteUsers.put(player.getUniqueId(), craft.store.id);
        releaseIfIdle(craft.store);
    }
    private static ItemStack[] matrixCopy(Craft craft) {
        return Arrays.stream(craft.matrix).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    }
    private ItemStack craftingResult(Craft craft) {
        // The server recipe manager also handles dynamic recipes and registered plugin recipes.
        ItemStack result = Bukkit.craftItemResult(matrixCopy(craft), craft.store.location.getWorld()).getResult();
        return result == null || result.getType().isAir() ? null : result.clone();
    }
    private void craftClick(InventoryClickEvent event, Craft craft) {
        boolean cancelled = event.isCancelled();
        event.setCancelled(true);
        if (cancelled || !(event.getWhoClicked() instanceof Player player)
                || event instanceof InventoryCreativeEvent || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT && event.getClick() != ClickType.SHIFT_LEFT) return;
        int slot = event.getRawSlot();
        Inventory previous = craft.inventory;
        boolean right = event.getClick() == ClickType.RIGHT;
        boolean multiple = event.getClick() == ClickType.SHIFT_LEFT;
        // Inventory changes are deferred; recheck the session, permissions and live quantities afterwards.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != previous) return;
            if (!canUse(player, craft.store, craft.remote)) { player.closeInventory(); return; }
            if (craft.book) {
                int index = craft.recipePage * 45 + slot;
                if (slot >= 0 && slot < 45 && index < craft.recipes.size()) {
                    craft.selectedRecipe = craft.recipes.get(index);
                    System.arraycopy(craft.selectedRecipe.select(craft.store.data).matrix(), 0, craft.matrix, 0, 9);
                    craft.book = false;
                } else if (slot == 45 && craft.recipePage > 0) craft.recipePage--;
                else if (slot == 53 && (craft.recipePage + 1) * 45 < craft.recipes.size()) craft.recipePage++;
                else if (slot == 49) craft.book = false;
                else if (slot == 46) {
                    if (right) { craft.query = ""; craft.recipePage = 0; craft.recipes = StorageRecipes.all(""); }
                    else {
                        recipeSearches.put(player.getUniqueId(), craft);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> recipeSearches.remove(player.getUniqueId(), craft), 1200);
                        player.closeInventory();
                        player.sendMessage("Rezeptsuche: innerhalb von 60 Sekunden den Namen im Chat eingeben (z.B. Diamanthelm). Abbrechen: abbrechen");
                        return;
                    }
                } else return;
            } else if (craft.selecting >= 0) {
                if (slot >= 0 && slot < 45 && craft.choices.containsKey(slot)) {
                    craft.matrix[craft.selecting] = craft.choices.get(slot).clone();
                    craft.selecting = -1;
                } else if (slot == 45 && craft.page > 0) craft.page--;
                else if (slot == 53 && craft.page < 9) craft.page++;
                else if (slot == 49) craft.selecting = -1;
                else return;
            } else {
                for (int i = 0; i < 9; i++) if (slot == CRAFT_SLOTS[i]) {
                    craft.selectedRecipe = null;
                    if (right) craft.matrix[i] = null;
                    else craft.selecting = i;
                    showCraft(player, craft); return;
                }
                if (slot == 45) { open(player, craft.store, 0, craft.remote); return; }
                if (slot == 53) { Arrays.fill(craft.matrix, null); craft.selectedRecipe = null; }
                else if (slot == 49) { craft.book = true; craft.recipes = StorageRecipes.all(craft.query); }
                else if ((slot == 24 || slot == 33) && !right) {
                    int completed = 0;
                    CraftPlan plan = new CraftPlan(craft.store.data, player.getInventory().getStorageContents());
                    for (int i = 0; i < (multiple ? 64 : 1); i++) {
                        CraftPlan next = performCraft(player, craft, plan, i == 0);
                        if (next == null) break;
                        plan = next;
                        completed++;
                    }
                    if (completed > 0) {
                        if (commit(craft.store, plan.data())) {
                            player.getInventory().setStorageContents(plan.inventory());
                            player.sendMessage("Hergestellt: " + completed + " Rezeptdurchläufe.");
                        } else player.sendMessage("Speicherfehler: Keine Zutaten verbraucht.");
                    }
                }
                else return;
            }
            showCraft(player, craft);
        });
    }
    private record CraftPlan(VirtualStorage<ItemStack> data, ItemStack[] inventory) {}
    private CraftPlan performCraft(Player player, Craft craft, CraftPlan source, boolean report) {
        if (craft.selectedRecipe != null) {
            var selection = craft.selectedRecipe.select(source.data());
            if (!selection.available()) { if (report) player.sendMessage("Im Lager fehlen Zutaten für dieses Rezept."); return null; }
            System.arraycopy(selection.matrix(), 0, craft.matrix, 0, 9);
            Recipe actual = Bukkit.getCraftingRecipe(matrixCopy(craft), craft.store.location.getWorld());
            if (!(actual instanceof Keyed keyed) || !keyed.getKey().toString().equals(craft.selectedRecipe.key())) {
                if (report) player.sendMessage("Dieses Rezept ist mit den gewählten Zutaten nicht verfügbar."); return null;
            }
        }
        VirtualStorage<ItemStack> next = StorageCraftingPlan.reserve(source.data(),
                Arrays.asList(craft.matrix), ItemStack::isSimilar);
        if (next == null) { if (report) player.sendMessage("Im Lager fehlen Zutaten für diese Vorlage."); return null; }
        var result = Bukkit.craftItemResult(matrixCopy(craft), craft.store.location.getWorld());
        ItemStack output = result.getResult();
        if (output == null || output.getType().isAir()) { if (report) player.sendMessage("Diese Vorlage ergibt kein Rezept."); return null; }
        List<ItemStack> delivered = new ArrayList<>();
        delivered.add(output.clone());
        // Buckets and other crafting remainders go to the player along with the result.
        for (ItemStack remainder : result.getResultingMatrix())
            if (remainder != null && !remainder.getType().isAir()) delivered.add(remainder.clone());
        for (ItemStack remainder : result.getOverflowItems())
            if (remainder != null && !remainder.getType().isAir()) delivered.add(remainder.clone());
        ItemStack[] destination = Arrays.stream(source.inventory())
                .map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        for (ItemStack item : delivered) {
            int left = item.getAmount();
            int limit = Math.min(64, item.getMaxStackSize());
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < destination.length && left > 0; i++) {
                ItemStack current = destination[i];
                boolean empty = current == null || current.getType().isAir();
                if (pass == 0 && !empty && current.isSimilar(item)) {
                    int moved = Math.min(left, Math.max(0, limit - current.getAmount()));
                    current.setAmount(current.getAmount() + moved); left -= moved;
                } else if (pass == 1 && empty) {
                    int moved = Math.min(left, limit);
                    destination[i] = item.clone(); destination[i].setAmount(moved); left -= moved;
                }
            }
            if (left > 0) { if (report) player.sendMessage("Dein Inventar hat nicht genug Platz für Ergebnis und Restitems. Nichts verbraucht."); return null; }
        }


        return new CraftPlan(next, destination);
    }
    StorageTerminals(JavaPlugin plugin, StorageTeams teams) {
        this.plugin = plugin;
        this.teams = teams;
        virtualItems = new NamespacedKey(plugin, "storage_virtual_items");
        virtualCounts = new NamespacedKey(plugin, "storage_virtual_counts");
        legacyBackup = new NamespacedKey(plugin, "storage_legacy_backup");
        storageOwner = new NamespacedKey(plugin, "storage_owner");
        marker = new NamespacedKey(plugin, "storage_terminal");
        contents = new NamespacedKey(plugin, "storage_contents");
        recipe = new NamespacedKey(plugin, "storage_terminal_recipe");
        identity = new NamespacedKey(plugin, "storage_id");
        phoneKey = new NamespacedKey(plugin, "storage_phone");
        phoneRecipe = new NamespacedKey(plugin, "storage_phone_recipe");
        ownerKey = new NamespacedKey(plugin, "phone_owner");
        linkKey = new NamespacedKey(plugin, "phone_link");
    }
    void enable() {
        senders = new StorageSenders(plugin, this, teams);
        senders.enable();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Objects.requireNonNull(plugin.getCommand("lager")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("lager")).setTabCompleter(this);
        ShapedRecipe crafting = new ShapedRecipe(recipe, item());
        crafting.shape("IRI", "DBD", "IRI");
        crafting.setIngredient('I', Material.IRON_INGOT);
        crafting.setIngredient('R', Material.REDSTONE);
        crafting.setIngredient('D', Material.DIAMOND);
        crafting.setIngredient('B', Material.BARREL);
        Bukkit.removeRecipe(recipe);
        if (!Bukkit.addRecipe(crafting)) throw new IllegalStateException("Lagerterminal-Rezept konnte nicht registriert werden");
        ShapedRecipe handset = new ShapedRecipe(phoneRecipe, phone());
        handset.shape("DRD", "IEI", "DID");
        handset.setIngredient('D', Material.DIAMOND);
        handset.setIngredient('R', Material.REDSTONE);
        handset.setIngredient('I', Material.IRON_INGOT);
        handset.setIngredient('E', Material.ENDER_EYE);
        Bukkit.removeRecipe(phoneRecipe);
        if (!Bukkit.addRecipe(handset)) throw new IllegalStateException("Handy-Rezept konnte nicht registriert werden");
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipes(List.of(recipe, phoneRecipe)));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Store store : List.copyOf(opened.values())) {
                for (var viewer : store.viewers())
                    if (!(viewer instanceof Player p) || !canUse(p, store)) viewer.closeInventory();
            }
        }, 20, 20);
    }
    ItemStack item() {
        ItemStack item = ExchangeMenu.icon(Material.BARREL, "Lagerterminal");
        var meta = item.getItemMeta();
        meta.lore(List.of(Component.text("450 gemeinsame Lagerplätze · 10 Seiten"), Component.text("Platzieren und rechtsklicken zum Öffnen.")));
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta); return item;
    }
    private ItemStack phone() {
        ItemStack item = ExchangeMenu.icon(Material.COMPASS, "Lager-Handy");
        var meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.lore(List.of(Component.text("Schleichen + Rechtsklick auf ein Terminal: verbinden."),
                Component.text("Rechtsklick: Lager in derselben Welt öffnen.")));
        meta.getPersistentDataContainer().set(phoneKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta); return item;
    }
    private boolean isPhone(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(phoneKey, PersistentDataType.BYTE);
    }
    private boolean validPhone(Player p, ItemStack item, Store store) {
        if (!isPhone(item)) return false;
        var data = item.getItemMeta().getPersistentDataContainer();
        return p.getUniqueId().toString().equals(data.get(ownerKey, PersistentDataType.STRING))
                && encode(store).equals(data.get(linkKey, PersistentDataType.STRING));
    }
    private String encode(Store store) {
        return store.location.getWorld().getUID() + ";" + store.location.getBlockX() + ";" + store.location.getBlockY()
                + ";" + store.location.getBlockZ() + ";" + store.id;
    }
    private boolean terminal(Block block) {
        return block != null && block.getState() instanceof Barrel barrel
                && barrel.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }
    private boolean canUse(Player player, Store store) {
        return canUse(player, store, store.id.equals(remoteUsers.get(player.getUniqueId())));
    }
    private boolean canUse(Player player, Store store, boolean remote) {
        return !store.failed && !stopping && player.getGameMode() != GameMode.SPECTATOR
                && allowed(player, store.location.getBlock())
                && player.getWorld().equals(store.location.getWorld())
                && (remote
                    ? Arrays.stream(player.getInventory().getContents()).anyMatch(i -> validPhone(player, i, store))
                    : player.getLocation().distanceSquared(store.location.clone().add(.5, .5, .5)) <= 64)
                && terminal(store.location.getBlock())
                && store.id.toString().equals(((Barrel) store.location.getBlock().getState()).getPersistentDataContainer().get(identity, PersistentDataType.STRING));
    }
    private boolean allowed(Player player, Block block) {
        if (!terminal(block)) return false;
        if (player.hasPermission("casino.admin")) return true;
        String owner = ((Barrel) block.getState()).getPersistentDataContainer().get(storageOwner, PersistentDataType.STRING);
        if (owner == null) return false;
        try { return teams.shares(UUID.fromString(owner), player.getUniqueId()); }
        catch (IllegalArgumentException error) { return false; }
    }
    private boolean owns(Player player, Block block) {
        return player.hasPermission("casino.admin") || player.getUniqueId().toString().equals(
                ((Barrel) block.getState()).getPersistentDataContainer().get(storageOwner, PersistentDataType.STRING));
    }
    private Store load(Block block) {
        Store existing = opened.get(block.getLocation());
        if (existing != null) return existing;
        Barrel barrel = (Barrel) block.getState();
        var pdc = barrel.getPersistentDataContainer();
        boolean virtual = pdc.has(virtualItems, PersistentDataType.BYTE_ARRAY);
        byte[] bytes = pdc.get(virtual ? virtualItems : contents, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) throw new IllegalStateException("Speicherdaten fehlen; Terminal wird nicht geleert.");
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
        int[] counts = virtual ? pdc.get(virtualCounts, PersistentDataType.INTEGER_ARRAY) : null;
        if (virtual && (items.length != 450 || counts == null || counts.length != 450)) throw new IllegalStateException("Ungültige virtuelle Lagerdaten.");
        items = StorageLayout.migrate(items);
        String storedId = pdc.get(identity, PersistentDataType.STRING);
        UUID id = storedId == null ? UUID.randomUUID() : UUID.fromString(storedId);
        Store store = new Store(block.getLocation(), id);
        for (int i = 0; i < items.length; i++) {
            boolean empty = items[i] == null || items[i].getType().isAir();
            if (virtual && (empty != (counts[i] == 0))) throw new IllegalStateException("Lagermenge passt nicht zum Item.");
            if (!empty) {
                int count = virtual ? counts[i] : items[i].getAmount();
                ItemStack prototype = items[i].clone(); prototype.setAmount(1);
                if (virtual) store.data.set(i, prototype, count);
                else if (store.data.insert(prototype, count, ItemStack::isSimilar) != count)
                    throw new IllegalStateException("Alte Lagerdaten passen nicht vollständig ins neue Lager.");
            }
        }
        // Save samples and quantities together. The old payload is retained once as a backup.
        if (!virtual || storedId == null) save(store);
        render(store);
        opened.put(store.location, store); return store;
    }
    private void save(Store store) {
        if (!terminal(store.location.getBlock())) throw new IllegalStateException("Lagerterminal-Block fehlt.");
        Barrel barrel = (Barrel) store.location.getBlock().getState();
        String currentId = barrel.getPersistentDataContainer().get(identity, PersistentDataType.STRING);
        if (currentId != null && !store.id.toString().equals(currentId)) throw new IllegalStateException("Das Lagerterminal wurde ersetzt.");
        var pdc = barrel.getPersistentDataContainer();
        byte[] old = pdc.get(contents, PersistentDataType.BYTE_ARRAY);
        if (old != null && !pdc.has(legacyBackup, PersistentDataType.BYTE_ARRAY)) pdc.set(legacyBackup, PersistentDataType.BYTE_ARRAY, old);
        int[] counts = new int[450];
        for (int i = 0; i < 450; i++) counts[i] = store.data.get(i) == null ? 0 : store.data.get(i).count();
        pdc.set(virtualItems, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(store.items()));
        pdc.set(virtualCounts, PersistentDataType.INTEGER_ARRAY, counts);
        pdc.remove(contents); // Old plugin versions must not restore stale contents.
        barrel.getPersistentDataContainer().set(identity, PersistentDataType.STRING, store.id.toString());
        if (!barrel.update(false, false)) throw new IllegalStateException("Lagerterminal konnte nicht gespeichert werden.");
    }
    private void flush(Store store) {
        if (store.failed) return;
        try { save(store); }
        catch (RuntimeException error) {
            store.failed = true;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Lagerterminal-Speicherfehler bei " + store.location, error);
            for (var viewer : store.viewers())
                viewer.sendMessage(Component.text("Lager-Speicherfehler. Weitere Änderungen sind gesperrt; bitte einen Admin informieren."));
        }
    }
    private boolean commit(Store store, VirtualStorage<ItemStack> next) {
        VirtualStorage<ItemStack> before = store.data;
        store.data = next;
        try { save(store); }
        catch (RuntimeException error) {
            store.data = before; store.failed = true;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Virtuelles Lager konnte nicht gespeichert werden", error);
            return false;
        }
        render(store);
        return true;
    }
    private void render(Store store) {
        for (int i = 0; i < 450; i++) {
            var entry = store.data.get(i);
            ItemStack icon = null;
            if (entry != null) {
                icon = entry.item().clone(); icon.setAmount(1);
                var meta = icon.getItemMeta();
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.text("Im Lager: " + entry.count() + " / 1.024"));
                meta.lore(lore); icon.setItemMeta(meta);
            }
            store.pages.get(StorageLayout.page(i)).inventory.setItem(StorageLayout.slot(i), icon);
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void place(BlockPlaceEvent event) {
        var meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return;
        if (!(event.getBlockPlaced().getState() instanceof Barrel barrel)) { event.setCancelled(true); return; }
        barrel.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        barrel.getPersistentDataContainer().set(contents, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(new ItemStack[450]));
        barrel.getPersistentDataContainer().set(identity, PersistentDataType.STRING, UUID.randomUUID().toString());
        barrel.getPersistentDataContainer().set(storageOwner, PersistentDataType.STRING, event.getPlayer().getUniqueId().toString());
        barrel.customName(Component.text("Lagerterminal"));
        if (!barrel.update(false, false)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void interact(PlayerInteractEvent event) {
        if (isPhone(event.getItem()) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY) return;
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
            usePhone(event.getPlayer(), event.getClickedBlock());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !terminal(event.getClickedBlock())
                || event.useInteractedBlock() == Event.Result.DENY) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
        try {
            if (!allowed(event.getPlayer(), event.getClickedBlock())) {
                event.getPlayer().sendMessage("Dieses Lager gehört nicht dir oder deinem Team. Alte Lager muss ein Admin mit /lager zuordnen <Spieler> zuordnen."); return;
            }
            Store store = load(event.getClickedBlock());
            remoteUsers.remove(event.getPlayer().getUniqueId());
            if (canUse(event.getPlayer(), store)) open(event.getPlayer(), store, 0, false);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Lagerterminal konnte nicht geladen werden", error);
            event.getPlayer().sendMessage("Das Lager konnte nicht geladen werden. Bitte einen Admin informieren.");
        }
    }
    private void open(Player player, Store store, int page, boolean remote) {
        store.location.getChunk().addPluginChunkTicket(plugin);
        Inventory target = store.pages.get(page).inventory;
        player.openInventory(target);
        if (player.getOpenInventory().getTopInventory() == target && remote) remoteUsers.put(player.getUniqueId(), store.id);
        else remoteUsers.remove(player.getUniqueId());
        releaseIfIdle(store);
    }
    private void releaseIfIdle(Store store) {
        if (stopping) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!store.viewers().isEmpty()) return;
            boolean chunkInUse = opened.values().stream().anyMatch(other ->
                    other.location.getWorld().equals(store.location.getWorld())
                    && (other.location.getBlockX() >> 4) == (store.location.getBlockX() >> 4)
                    && (other.location.getBlockZ() >> 4) == (store.location.getBlockZ() >> 4)
                    && !other.viewers().isEmpty());
            if (!chunkInUse) store.location.getWorld().removePluginChunkTicket(store.location.getBlockX() >> 4, store.location.getBlockZ() >> 4, plugin);
        });
    }
    private void usePhone(Player player, Block clicked) {
        ItemStack handset = player.getInventory().getItemInMainHand();
        var meta = handset.getItemMeta();
        var data = meta.getPersistentDataContainer();
        String owner = data.get(ownerKey, PersistentDataType.STRING);
        if (owner != null && !owner.equals(player.getUniqueId().toString())) {
            player.sendMessage("Dieses Handy gehört einem anderen Spieler."); return;
        }
        try {
            if (player.isSneaking() && terminal(clicked)) {
                if (!allowed(player, clicked)) { player.sendMessage("Du hast keinen Zugriff auf dieses Lager."); return; }
                Store store = load(clicked);
                if (store.failed) { player.sendMessage("Dieses Lager ist wegen eines Speicherfehlers gesperrt."); return; }
                data.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                data.set(linkKey, PersistentDataType.STRING, encode(store));
                meta.lore(List.of(Component.text("Besitzer: " + player.getName()),
                        Component.text("Lager: " + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ()),
                        Component.text("Rechtsklick: Fernzugriff in derselben Welt.")));
                handset.setItemMeta(meta);
                player.getInventory().setItemInMainHand(handset);
                player.sendMessage("Handy mit diesem Lager verbunden und an dich gebunden."); return;
            }
            String link = data.get(linkKey, PersistentDataType.STRING);
            if (owner == null || link == null) { player.sendMessage("Zuerst schleichen und mit dem Handy auf ein Lagerterminal rechtsklicken."); return; }
            String[] parts = link.split(";");
            if (parts.length != 5) throw new IllegalArgumentException("Ungültige Handy-Verknüpfung");
            if (!player.getWorld().getUID().toString().equals(parts[0])) { player.sendMessage("Das Lager liegt in einer anderen Welt."); return; }
            Location location = new Location(player.getWorld(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            Block block = location.getBlock(); // Loads the target chunk when the player is far away.
            if (!terminal(block)) { player.sendMessage("Das verknüpfte Lager existiert nicht mehr."); return; }
            if (!allowed(player, block)) { player.sendMessage("Du hast keinen Zugriff mehr auf dieses Lager."); return; }
            Store store = load(block);
            if (!validPhone(player, handset, store)) { player.sendMessage("Das ursprüngliche Lager wurde ersetzt. Bitte das Handy neu verbinden."); return; }
            if (store.failed || stopping) { player.sendMessage("Dieses Lager ist derzeit gesperrt."); return; }
            open(player, store, 0, true);
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Handy konnte Lager nicht öffnen", error);
            player.sendMessage("Das Lager konnte nicht geöffnet werden. Bitte einen Admin informieren.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Craft craft) {
            craftClick(event, craft); return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        Store store = page.store;
        if (!(event.getWhoClicked() instanceof Player p) || !canUse(p, store)
                || event.getClick() == ClickType.MIDDLE || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event instanceof InventoryCreativeEvent) {
            event.setCancelled(true); return;
        }
        int slot = event.getRawSlot();
        if (event.isCancelled()) return;
        if (slot >= 0 && slot < 45) {
            event.setCancelled(true);
            if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
            ItemStack cursor = p.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                deposit(p, store, cursor, -1); return;
            }
            int index = page.index * 45 + slot;
            var entry = store.data.get(index);
            if (entry == null) return;
            int amount = Math.min(entry.count(), event.getClick() == ClickType.RIGHT ? 1 : Math.min(64, entry.item().getMaxStackSize()));
            ItemStack[] destination = Arrays.stream(p.getInventory().getStorageContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
            int left = amount;
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < destination.length && left > 0; i++) {
                ItemStack existing = destination[i];
                boolean empty = existing == null || existing.getType().isAir();
                int limit = Math.min(64, entry.item().getMaxStackSize());
                if (pass == 0 && !empty && existing.isSimilar(entry.item())) {
                    int moved = Math.min(left, Math.max(0, limit - existing.getAmount()));
                    existing.setAmount(existing.getAmount() + moved); left -= moved;
                } else if (pass == 1 && empty) {
                    int moved = Math.min(left, limit);
                    destination[i] = entry.item().clone(); destination[i].setAmount(moved); left -= moved;
                }
            }
            if (left == amount) { p.sendMessage("Dein Inventar ist voll."); return; }
            VirtualStorage<ItemStack> next = store.data.copy();
            next.remove(index, amount - left);
            if (commit(store, next)) p.getInventory().setStorageContents(destination);
            else p.sendMessage("Speicherfehler: Keine Items entnommen. Bitte einen Admin informieren.");
            return;
        }
        if (slot >= 54 && event.isShiftClick()) {
            event.setCancelled(true);
            if (event.getClickedInventory() == p.getInventory()) {
                ItemStack source = p.getInventory().getItem(event.getSlot());
                if (source != null && !source.getType().isAir()) deposit(p, store, source, event.getSlot());
            }
            return;
        }
        if (slot >= 45 && slot < 54) {
            boolean previouslyCancelled = event.isCancelled();
            event.setCancelled(true);
            if (previouslyCancelled) return;
            boolean wasRemote = store.id.equals(remoteUsers.get(p.getUniqueId()));
            if (slot == 48 && event.getClick() == ClickType.LEFT) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.getOpenInventory().getTopInventory() != page.inventory || !canUse(p, store)) return;
                    Craft craft = new Craft(store, wasRemote);
                    store.crafts.add(craft);
                    showCraft(p, craft);
                });
                return;
            }
            if (slot == 46 || slot == 47 || slot == 51) {
                if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
                boolean byAmount = event.getClick() == ClickType.RIGHT;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.getOpenInventory().getTopInventory() != page.inventory || !canUse(p, store)) return;
                    if (slot == 46) {
                        SearchRequest request = new SearchRequest(store, wasRemote);
                        searching.put(p.getUniqueId(), request);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> searching.remove(p.getUniqueId(), request), 1200);
                        p.closeInventory();
                        p.sendMessage("Suchbegriff innerhalb von 60 Sekunden im Chat eingeben (z.B. eisen, diamond oder eigener Itemname). Abbrechen: abbrechen");
                    } else if (slot == 47) {
                        VirtualStorage<ItemStack> nextData = store.data.copy();
                        Comparator<VirtualStorage.Entry<ItemStack>> order = Comparator.comparing(e -> e.item().getType().name());
                        Comparator<VirtualStorage.Entry<ItemStack>> amounts = Comparator.comparingInt(VirtualStorage.Entry<ItemStack>::count).reversed();
                        nextData.sort(byAmount ? amounts.thenComparing(order) : order.thenComparing(amounts));
                        commit(store, nextData);
                        if (!store.failed) { open(p, store, 0, wasRemote); p.sendMessage("Lager sortiert nach " + (byAmount ? "Stapelgröße." : "Itemtyp.")); }
                    } else {
                        SearchCursor previous = searches.get(p.getUniqueId());
                        if (previous == null || !previous.store.equals(store.id)) p.sendMessage("Zuerst mit dem Kompass eine Suche starten.");
                        else find(p, store, wasRemote, previous.query, previous.slot);
                    }
                });
                return;
            }
            if (event.getClick() != ClickType.LEFT) return;
            int next = slot == 45 ? page.index - 1 : slot == 53 ? page.index + 1 : -1;
            if (next < 0 || next >= 10) return;
            boolean remote = store.id.equals(remoteUsers.get(p.getUniqueId()));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory() == page.inventory && canUse(p, store)) {
                    flush(store);
                    if (!store.failed) open(p, store, next, remote);
                }
            });
        }
    }
    private void deposit(Player player, Store store, ItemStack source, int playerSlot) {
        ItemStack prototype = source.clone(); prototype.setAmount(1);
        VirtualStorage<ItemStack> next = store.data.copy();
        int inserted = next.insert(prototype, source.getAmount(), ItemStack::isSimilar);
        if (inserted == 0) { player.sendMessage("Das Lager ist voll."); return; }
        if (!commit(store, next)) { player.sendMessage("Speicherfehler: Keine Items eingelagert."); return; }
        ItemStack remainder = source.clone(); remainder.setAmount(source.getAmount() - inserted);
        if (playerSlot < 0) player.setItemOnCursor(remainder.getAmount() == 0 ? null : remainder);
        else player.getInventory().setItem(playerSlot, remainder.getAmount() == 0 ? null : remainder);
    }
    private void find(Player player, Store store, boolean remote, String query, int after) {
        if (!canUse(player, store, remote)) { player.sendMessage("Das Lager ist nicht mehr zugänglich."); return; }
        ItemStack[] items = store.items();
        int found = StorageSearch.next(items.length, after, index -> {
            ItemStack item = items[index];
            if (item == null || item.getType().isAir()) return false;
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()) : "";
            return StorageSearch.matches(item.getType().name(), name, query);
        });
        searches.put(player.getUniqueId(), new SearchCursor(store.id, query, found));
        open(player, store, found < 0 ? 0 : StorageLayout.page(found), remote);
        player.sendMessage(found < 0 ? "Keine Treffer für: " + query
                : "Treffer für „" + query + "“: Seite " + (StorageLayout.page(found) + 1)
                + ", Reihe " + (StorageLayout.slot(found) / 9 + 1) + ", Spalte " + (StorageLayout.slot(found) % 9 + 1)
                + ". Fernrohr: nächster Treffer.");
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void searchChat(AsyncPlayerChatEvent event) {
        Craft pending = recipeSearches.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            String query = event.getMessage().strip();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = event.getPlayer();
                if (!player.isOnline() || stopping) return;
                if (query.equalsIgnoreCase("abbrechen")) { player.sendMessage("Rezeptsuche abgebrochen."); return; }
                if (query.isEmpty() || query.length() > 80) { player.sendMessage("Bitte 1 bis 80 Zeichen verwenden."); return; }
                try {
                    if (!terminal(pending.store.location.getBlock())) { player.sendMessage("Das Lager existiert nicht mehr."); return; }
                    Store current = load(pending.store.location.getBlock());
                    if (!current.id.equals(pending.store.id) || !canUse(player, current, pending.remote)) {
                        player.sendMessage("Kein Zugriff mehr auf dieses Lager."); return;
                    }
                    Craft craft = new Craft(current, pending.remote);
                    craft.book = true; craft.query = query; craft.recipes = StorageRecipes.all(query);
                    System.arraycopy(pending.matrix, 0, craft.matrix, 0, 9);
                    craft.selectedRecipe = pending.selectedRecipe;
                    current.crafts.add(craft);
                    showCraft(player, craft);
                    if (craft.recipes.isEmpty()) player.sendMessage("Keine Rezepte gefunden. Versuche einen kürzeren Suchbegriff oder den englischen Itemnamen.");
                } catch (RuntimeException error) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Rezeptsuche konnte Lager nicht öffnen", error);
                    player.sendMessage("Rezeptsuche konnte nicht geöffnet werden.");
                }
            });
            return;
        }
        SearchRequest request = searching.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        String query = event.getMessage().strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = event.getPlayer();
            if (!p.isOnline() || stopping) return;
            if (query.equalsIgnoreCase("abbrechen")) { p.sendMessage("Suche abgebrochen."); return; }
            if (query.isEmpty() || query.length() > 80) { p.sendMessage("Bitte einen Suchbegriff mit 1 bis 80 Zeichen verwenden."); return; }
            // The chat prompt holds no chunk ticket: reload and verify identity before using it.
            Store store = request.store;
            if (!terminal(store.location.getBlock())) { p.sendMessage("Das Lager existiert nicht mehr."); return; }
            try {
                Store current = load(store.location.getBlock());
                if (!current.id.equals(store.id)) { p.sendMessage("Das Lager wurde ersetzt."); return; }
                find(p, current, request.remote, query, -1);
            } catch (RuntimeException error) { p.sendMessage("Lager konnte nicht geladen werden."); }
        });
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        recipeSearches.remove(event.getPlayer().getUniqueId());
        searching.remove(event.getPlayer().getUniqueId());
        searches.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Craft) { event.setCancelled(true); return; }
        if (event.getView().getTopInventory().getHolder() instanceof Page page
                && (!(event.getWhoClicked() instanceof Player p) || !canUse(p, page.store)
                || event.getRawSlots().stream().anyMatch(slot -> slot < 54))) event.setCancelled(true);
    }    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Craft craft) {
            remoteUsers.remove(event.getPlayer().getUniqueId());
            if (!stopping) Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() != craft)
                    craft.store.crafts.remove(craft);
            });
            releaseIfIdle(craft.store);
        }
        if (event.getInventory().getHolder() instanceof Page page) {
            flush(page.store);
            remoteUsers.remove(event.getPlayer().getUniqueId());
            releaseIfIdle(page.store);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (!terminal(event.getBlock())) return;
        event.setCancelled(true);
        if (!owns(event.getPlayer(), event.getBlock())) { event.getPlayer().sendMessage("Nur der Besitzer oder ein Admin darf dieses Terminal abbauen."); return; }
        try {
            Store store = load(event.getBlock());
            if (store.failed || Arrays.stream(store.items()).anyMatch(i -> i != null && !i.getType().isAir()) || !store.viewers().isEmpty()) {
                event.getPlayer().sendMessage("Bitte das Lager vollständig leeren und alle Lagerfenster schließen, bevor du es abbaust."); return;
            }
            opened.remove(store.location);
            event.getBlock().setType(Material.AIR);
            event.getBlock().getWorld().dropItemNaturally(store.location.clone().add(.5, .5, .5), item());
        } catch (RuntimeException error) { event.getPlayer().sendMessage("Lagerdaten konnten nicht geprüft werden. Abbau gesperrt."); }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::terminal); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explodeBlock(BlockExplodeEvent event) { event.blockList().removeIf(this::terminal); }
    @EventHandler(ignoreCancelled = true) public void burn(BlockBurnEvent event) { if (terminal(event.getBlock())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::terminal)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void pistonBack(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::terminal)) event.setCancelled(true); }
    private boolean physicalTerminal(Inventory inventory) { return inventory.getHolder() instanceof Barrel barrel && terminal(barrel.getBlock()); }
    @EventHandler(ignoreCancelled = true) public void hopper(InventoryMoveItemEvent event) {
        if (physicalTerminal(event.getSource()) || physicalTerminal(event.getDestination())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void vanillaOpen(InventoryOpenEvent event) {
        if (physicalTerminal(event.getInventory())) event.setCancelled(true);
    }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipes(List.of(recipe, phoneRecipe)); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent event) {
        for (Store store : List.copyOf(opened.values())) {
            if (!store.location.getWorld().equals(event.getWorld()) || (store.location.getBlockX() >> 4) != event.getChunk().getX()
                    || (store.location.getBlockZ() >> 4) != event.getChunk().getZ()) continue;
            for (var viewer : store.viewers()) viewer.closeInventory();
            flush(store);
            if (!store.failed) { event.setSaveChunk(true); opened.remove(store.location); }
        }
    }
    @EventHandler public void worldSave(WorldSaveEvent event) {
        opened.values().stream().filter(s -> s.location.getWorld().equals(event.getWorld())).forEach(this::flush);
    }
    void disable() {
        if (senders != null) senders.disable();
        stopping = true;
        for (Store store : List.copyOf(opened.values())) {
            for (var viewer : store.viewers()) viewer.closeInventory();
            flush(store);
        }
        Bukkit.removeRecipe(recipe);
        Bukkit.removeRecipe(phoneRecipe);
        Bukkit.getWorlds().forEach(world -> world.removePluginChunkTickets(plugin));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("sender")) {
            if (!(sender instanceof Player p) || !p.hasPermission("casino.admin")) { sender.sendMessage("Nur Admins im Spiel können Sender erhalten."); return true; }
            p.getInventory().addItem(senders.item()).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("zuordnen")) {
            if (!(sender instanceof Player p) || !sender.hasPermission("casino.admin")) { sender.sendMessage("Nur Admins im Spiel können alte Lager zuordnen."); return true; }
            Block block = p.getTargetBlockExact(6);
            Player target = Bukkit.getPlayerExact(args[1]);
            if (!terminal(block) || target == null) { p.sendMessage("Auf ein Lagerterminal schauen und einen Online-Spieler angeben."); return true; }
            Barrel barrel = (Barrel) block.getState();
            if (barrel.getPersistentDataContainer().has(storageOwner, PersistentDataType.STRING)) { p.sendMessage("Dieses Lager hat bereits einen Besitzer."); return true; }
            barrel.getPersistentDataContainer().set(storageOwner, PersistentDataType.STRING, target.getUniqueId().toString());
            p.sendMessage(barrel.update(false, false) ? "Lager zugeordnet an " + target.getName() : "Zuordnung fehlgeschlagen.");
            return true;
        }
        if (args.length != 1 || (!args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("handy"))) { sender.sendMessage("/lager give – Terminal; /lager handy – Handy (Admin). Beides ist craftbar."); return true; }
        if (!sender.hasPermission("casino.admin")) { sender.sendMessage("Dafür fehlen dir die Admin-Rechte."); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage("Bitte im Spiel ausführen."); return true; }
        p.getInventory().addItem(args[0].equalsIgnoreCase("handy") ? phone() : item()).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i)); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return sender.hasPermission("casino.admin") && args.length == 1
                ? List.of("give", "handy", "sender", "zuordnen").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }

    String senderLink(Player player, ItemStack handset) {
        if (!isPhone(handset)) return null;
        var data = handset.getItemMeta().getPersistentDataContainer();
        if (!player.getUniqueId().toString().equals(data.get(ownerKey, PersistentDataType.STRING))) return null;
        return data.get(linkKey, PersistentDataType.STRING);
    }

    // Called synchronously. Re-check the sender owner's team rights even while they are offline.
    String senderTargetProblem(UUID owner, World world, String link) {
        if (link == null) return "Noch nicht mit einem Lager verbunden.";
        try {
            String[] parts = link.split(";");
            if (parts.length != 5) return "Ungültige Verbindung. Handy neu verbinden.";
            if (!world.getUID().toString().equals(parts[0])) return "Das Lager liegt in einer anderen Welt.";
            Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            if (!terminal(block)) return "Das Ziel-Lager existiert nicht mehr.";
            var data = ((Barrel) block.getState()).getPersistentDataContainer();
            if (!parts[4].equals(data.get(identity, PersistentDataType.STRING))) return "Das Ziel-Lager wurde ersetzt. Handy neu verbinden.";
            String targetOwner = data.get(storageOwner, PersistentDataType.STRING);
            if (targetOwner == null) return "Das Ziel-Lager hat keinen Besitzer. Admin: auf das Lager schauen und /lager zuordnen <Spieler> eingeben.";
            if (!teams.shares(UUID.fromString(targetOwner), owner)) return "Der Sender-Besitzer hat keinen Teamzugriff auf dieses Lager.";
            return null;
        } catch (IllegalArgumentException error) { return "Ungültige Verbindung. Handy neu verbinden."; }
    }

    int receive(UUID owner, World world, String link, ItemStack offered) {
        if (stopping || offered == null || offered.getType().isAir()) return 0;
        String[] parts = link.split(";");
        if (parts.length != 5 || !world.getUID().toString().equals(parts[0])) return 0;
        Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        if (!terminal(block)) return 0;
        var data = ((Barrel) block.getState()).getPersistentDataContainer();
        String targetOwner = data.get(storageOwner, PersistentDataType.STRING);
        if (!parts[4].equals(data.get(identity, PersistentDataType.STRING)) || targetOwner == null
                || !teams.shares(UUID.fromString(targetOwner), owner)) return 0;
        Store store = load(block);
        if (store.failed) throw new IllegalStateException("Ziel-Lager wegen Speicherfehler gesperrt.");
        VirtualStorage<ItemStack> next = store.data.copy();
        ItemStack prototype = offered.clone(); prototype.setAmount(1);
        int accepted = next.insert(prototype, Math.min(16, offered.getAmount()), ItemStack::isSimilar);
        if (accepted > 0 && !commit(store, next)) throw new IllegalStateException("Lager konnte nicht gespeichert werden.");
        return accepted;
    }
}




