package de.casino;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.IOException;
import java.util.*;

public final class CasinoPlugin extends JavaPlugin implements Listener, TabCompleter {
    private NamespacedKey itemKey, machineKey, groupKey, recipeKey, slotRecipeKey;
    private Accounts accounts;
    private Chips chips;

    @Override public void onEnable() {
        itemKey = new NamespacedKey(this, "exchange_item");
        machineKey = new NamespacedKey(this, "exchange_machine");
        groupKey = new NamespacedKey(this, "machine_group");
        recipeKey = new NamespacedKey(this, "exchange_machine_recipe");
        slotRecipeKey = new NamespacedKey(this, "slot_machine_recipe");
        try { accounts = new Accounts(getDataFolder().toPath()); }
        catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Kontodaten konnten nicht geladen werden.", error);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Objects.requireNonNull(getCommand("casino")).setExecutor(this);
        Objects.requireNonNull(getCommand("casino")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("pay")).setExecutor(this);
        Objects.requireNonNull(getCommand("pay")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        chips = new Chips(this);
        getServer().getPluginManager().registerEvents(chips, this);
        registerMachineRecipe();
        Bukkit.getOnlinePlayers().forEach(player -> player.discoverRecipes(List.of(recipeKey, slotRecipeKey)));
        Bukkit.getOnlinePlayers().forEach(this::ensureAccount);
        // Schließen, wenn Spieler den Automaten verlassen oder dieser entladen wird.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ChipShop shop && !canUseShop(player, shop.machine)) player.closeInventory();
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ExchangeMenu menu) {
                    Entity entity = Bukkit.getEntity(menu.machine);
                    if (entity == null || !entity.isValid() || entity.getWorld() != player.getWorld()
                            || entity.getLocation().distanceSquared(player.getLocation()) > 64)
                        player.closeInventory();
                }
            }
        }, 10L, 10L);
        getLogger().info("Casino-Plugin v" + getPluginMeta().getVersion() + " erfolgreich geladen!");
    }

    @Override public void onDisable() {
        if (recipeKey != null) Bukkit.removeRecipe(recipeKey);
        if (slotRecipeKey != null) Bukkit.removeRecipe(slotRecipeKey);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ChipShop) player.closeInventory();
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SlotMenu slots) {
                slots.close();
                player.closeInventory();
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ExchangeMenu menu) {
                menu.returnInput(player);
                player.closeInventory();
            }
        }
    }

    private void ensureAccount(Player player) {
        try { accounts.balance(player); }
        catch (IOException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Konto konnte nicht gespeichert werden", error);
            player.sendMessage(Component.text("Dein Casino-Konto konnte nicht gespeichert werden.", NamedTextColor.RED));
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        ensureAccount(event.getPlayer());
        event.getPlayer().discoverRecipes(List.of(recipeKey, slotRecipeKey));
    }

    private void registerMachineRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, machineItem());
        recipe.shape("GIP", " I ", " I ");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('P', Material.PAPER);
        Bukkit.removeRecipe(recipeKey);
        if (!Bukkit.addRecipe(recipe)) throw new IllegalStateException("Wechselautomaten-Rezept konnte nicht registriert werden");
        ShapedRecipe slotRecipe = new ShapedRecipe(slotRecipeKey, machineItem(true));
        slotRecipe.shape("SI ", "SGT", "SD ");
        slotRecipe.setIngredient('S', Material.STONE);
        slotRecipe.setIngredient('D', Material.DIAMOND_BLOCK);
        slotRecipe.setIngredient('I', Material.IRON_BLOCK);
        slotRecipe.setIngredient('G', Material.GOLD_BLOCK);
        slotRecipe.setIngredient('T', Material.STICK);
        Bukkit.removeRecipe(slotRecipeKey);
        if (!Bukkit.addRecipe(slotRecipe)) throw new IllegalStateException("Spielautomaten-Rezept konnte nicht registriert werden");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Bitte im Spiel ausführen."); return true; }
        if (command.getName().equalsIgnoreCase("pay")) return pay(player, args);
        String action = args.length == 0 ? "konto" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("konto")) {
            try { player.sendMessage(Component.text("Casino-Guthaben: " + Money.format(accounts.balance(player)), NamedTextColor.GOLD)); }
            catch (IOException error) { ensureAccount(player); }
        } else if (action.equals("spielautomat")) {
            try { player.openInventory(new SlotMenu(this, accounts, player).getInventory()); }
            catch (IOException error) { ensureAccount(player); }
        } else if (action.equals("give") || action.equals("migrate")) {
            if (!player.hasPermission("casino.admin")) { player.sendMessage("Dafür fehlen dir die Rechte."); return true; }
            if (action.equals("give")) {
                if (args.length > 1 && args[1].equalsIgnoreCase("chip")) {
                    Chips.Kind kind = args.length > 2 ? Chips.Kind.fromName(args[2]) : Chips.Kind.RED;
                    if (kind == null) { player.sendMessage("/casino give chip <rot|gruen|blau|lila>"); return true; }
                    player.getInventory().addItem(chips.item(kind)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    return true;
                }
                player.getInventory().addItem(machineItem(args.length > 1 && args[1].equalsIgnoreCase("spielautomat"))).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            } else migrate(player);
        } else player.sendMessage("/casino konto · /casino spielautomat · /casino give · /casino migrate");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("pay")) {
            return args.length == 1 ? Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(sender))
                    .map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
        }
        if (sender.hasPermission("casino.admin") && args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            List<String> options = args.length == 2 ? List.of("chip", "spielautomat")
                    : args.length == 3 && args[1].equalsIgnoreCase("chip") ? List.of("rot", "gruen", "blau", "lila") : List.of();
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT).replace("ü", "ue");
            return options.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length != 1) return List.of();
        return (sender.hasPermission("casino.admin") ? List.of("konto", "spielautomat", "give", "migrate") : List.of("konto", "spielautomat"))
                .stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }

    private ItemStack machineItem() { return machineItem(false); }

    private boolean pay(Player sender, String[] args) {
        if (args.length != 2) { sender.sendMessage("/pay <Spieler> <Betrag> – zum Beispiel /pay Milo 3"); return true; }
        Player recipient = Bukkit.getPlayerExact(args[0]);
        if (recipient == null) { sender.sendMessage("Dieser Spieler ist nicht online. Bitte den vollständigen Namen eingeben."); return true; }
        if (recipient.equals(sender)) { sender.sendMessage("Du kannst dir nicht selbst Geld überweisen."); return true; }
        long cents;
        try { cents = Money.parsePositive(args[1]); }
        catch (IllegalArgumentException | ArithmeticException error) {
            sender.sendMessage("Bitte einen positiven Euro-Betrag mit höchstens zwei Nachkommastellen eingeben, z. B. 3 oder 0,50."); return true;
        }
        try {
            accounts.balance(sender);
            accounts.balance(recipient);
            accounts.transfer(sender.getUniqueId(), recipient.getUniqueId(), cents);
            sender.sendMessage(Component.text("Du hast " + recipient.getName() + " " + Money.format(cents) + " überwiesen.", NamedTextColor.GREEN));
            recipient.sendMessage(Component.text(sender.getName() + " hat dir " + Money.format(cents) + " überwiesen.", NamedTextColor.GREEN));
        } catch (IllegalArgumentException error) { sender.sendMessage(error.getMessage()); }
        catch (ArithmeticException error) { sender.sendMessage("Das Konto des Empfängers würde sein Limit überschreiten."); }
        catch (IOException error) {
            sender.sendMessage("Die Überweisung konnte nicht gespeichert werden. Es wurde kein Geld überwiesen.");
            getLogger().log(java.util.logging.Level.SEVERE, "Überweisung fehlgeschlagen", error);
        }
        return true;
    }
    private ItemStack machineItem(boolean slots) {
        ItemStack item = ExchangeMenu.icon(slots ? Material.DIAMOND_BLOCK : Material.GOLD_BLOCK, slots ? "Spielautomat" : "Wechselautomat");
        var meta = item.getItemMeta();
        meta.lore(List.of(Component.text("Auf den Boden rechtsklicken zum Aufstellen.")));
        meta.setItemModel(new NamespacedKey(this, slots ? "spielautomat" : "wechsler"));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) (slots ? 2 : 1));
        item.setItemMeta(meta);
        return item;
    }
    private boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }
    private boolean isMachine(Entity entity) {
        return entity instanceof Interaction && entity.getPersistentDataContainer().has(machineKey, PersistentDataType.BYTE);
    }
    private void mark(Entity entity, UUID group) {
        entity.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, group.toString());
        entity.setPersistent(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void place(PlayerInteractEvent event) {
        if (machineAt(event.getClickedBlock()) != null) return;
        if (!isItem(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.CREATIVE) return;
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP) { player.sendMessage("Bitte auf die Oberseite des Bodens klicken."); return; }
        Location base = event.getClickedBlock().getRelative(org.bukkit.block.BlockFace.UP).getLocation().add(.5, 0, .5);
        if (!base.getBlock().getType().isAir() || !base.clone().add(0, 1, 0).getBlock().getType().isAir()
                || !base.getWorld().getNearbyEntities(base, 1, 2, 1, this::isMachine).isEmpty()) {
            player.sendMessage("Hier ist nicht genug Platz für einen Wechselautomaten."); return;
        }
        base.setYaw(Math.round(player.getYaw() / 90f) * 90f);
        spawnMachine(base, event.getItem().getItemMeta().getPersistentDataContainer().getOrDefault(itemKey, PersistentDataType.BYTE, (byte) 1) == 2);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItem(Objects.requireNonNull(event.getHand()));
            hand.subtract(1);
            player.getInventory().setItem(event.getHand(), hand);
        }
    }

    private void spawnMachine(Location base) { spawnMachine(base, false); }
    private void spawnMachine(Location base, boolean slots) {
        List<Entity> created = new ArrayList<>();
        List<org.bukkit.block.Block> barriers = new ArrayList<>();
        if (!base.getBlock().getType().isAir() || !base.clone().add(0, 1, 0).getBlock().getType().isAir())
            throw new IllegalStateException("Der Platz für den Automaten ist belegt.");
        try {
            Interaction root = base.getWorld().spawn(base, Interaction.class, entity -> {
                entity.setInteractionWidth(1.05f); entity.setInteractionHeight(2f); entity.setResponsive(true);
                entity.getPersistentDataContainer().set(machineKey, PersistentDataType.BYTE, (byte) (slots ? 2 : 1));
            });
            created.add(root); mark(root, root.getUniqueId());
            {
                ItemDisplay display = base.getWorld().spawn(base.clone().add(0, .5, 0), ItemDisplay.class, entity -> {
                    entity.setItemStack(machineItem(slots));
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    // Slot-machine front points west in the exported model; exchanger points north.
                    entity.setRotation(base.getYaw() + (slots ? 270f : 180f), 0);
                });
                created.add(display); mark(display, root.getUniqueId());
            }
            TextDisplay label = base.getWorld().spawn(base.clone().add(0, 2.3, 0), TextDisplay.class, entity -> {
                entity.text(Component.text(slots ? "SPIELAUTOMAT\nRechtsklick zum Spielen" : "WECHSELAUTOMAT\nRechtsklick zum Tauschen", NamedTextColor.GOLD));
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(.7f), new Quaternionf()));
            });
            created.add(label); mark(label, root.getUniqueId());
            {
                for (int layer = 0; layer < 2; layer++) {
                    org.bukkit.block.Block block = base.clone().add(0, layer, 0).getBlock();
                    barriers.add(block);
                    block.setType(Material.BARRIER, false);
                }
                root.getPersistentDataContainer().set(new NamespacedKey(this, "solid_machine"), PersistentDataType.BYTE, (byte) 1);
            }
        } catch (RuntimeException error) {
            barriers.forEach(block -> { if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false); });
            created.forEach(Entity::remove); throw error;
        }
    }

    private Entity machineAt(org.bukkit.block.Block block) {
        if (block == null || block.getType() != Material.BARRIER) return null;
        for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation().add(.5, .5, .5), 1, 2, 1, this::isMachine)) {
            if (!entity.getPersistentDataContainer().has(new NamespacedKey(this, "solid_machine"), PersistentDataType.BYTE)) continue;
            Location base = entity.getLocation();
            if (base.getBlockX() == block.getX() && base.getBlockZ() == block.getZ()
                    && (base.getBlockY() == block.getY() || base.getBlockY() + 1 == block.getY())) return entity;
        }
        return null;
    }

    // Air-item interactions can be pre-cancelled by vanilla; check block use separately.
    @EventHandler(priority = EventPriority.HIGH)
    public void interactBarrier(PlayerInteractEvent event) {
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        Entity root = machineAt(event.getClickedBlock());
        if (root == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) dismantle(root, event.getPlayer());
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) openMachine(root, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void breakBarrier(org.bukkit.event.block.BlockBreakEvent event) {
        Entity root = machineAt(event.getBlock());
        if (root == null) return;
        event.setCancelled(true);
        dismantle(root, event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void interact(PlayerInteractEntityEvent event) {
        if (!isMachine(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().getGameMode() == GameMode.SPECTATOR) return;
        openMachine(event.getRightClicked(), event.getPlayer());
    }

    private void openMachine(Entity root, Player player) {
        if (!isMachine(root) || player.getGameMode() == GameMode.SPECTATOR) return;
        if (root.getPersistentDataContainer().getOrDefault(machineKey, PersistentDataType.BYTE, (byte) 1) == 2) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SlotMenu) return;
            try { player.openInventory(new SlotMenu(this, accounts, player, root.getUniqueId()).getInventory()); }
            catch (IOException error) { ensureAccount(player); }
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ExchangeMenu) return;
        player.openInventory(new ExchangeMenu(root.getUniqueId()).getInventory());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void breakMachine(EntityDamageByEntityEvent event) {
        if (!isMachine(event.getEntity())) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        dismantle(event.getEntity(), player);
    }

    private void dismantle(Entity root, Player player) {
        if (!isMachine(root)) return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.CREATIVE) return;
        Location location = root.getLocation();
        boolean slots = root.getPersistentDataContainer().getOrDefault(machineKey, PersistentDataType.BYTE, (byte) 1) == 2;
        root.getPersistentDataContainer().remove(machineKey); // Vor Drop gegen doppelte Events sperren.
        for (Player viewer : Bukkit.getOnlinePlayers())
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof SlotMenu menu && root.getUniqueId().equals(menu.machine))
                viewer.closeInventory();
        for (Player viewer : Bukkit.getOnlinePlayers())
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof ExchangeMenu menu && menu.machine.equals(root.getUniqueId()))
                viewer.closeInventory();
        removeMachine(root);
        location.getWorld().dropItemNaturally(location.clone().add(0, .5, 0), machineItem(slots));
        location.getWorld().playSound(location, Sound.BLOCK_STONE_BREAK, 1, 1);
    }

    private void removeMachine(Entity root) {
        if (root.getPersistentDataContainer().has(new NamespacedKey(this, "solid_machine"), PersistentDataType.BYTE)) {
            for (int layer = 0; layer < 2; layer++) {
                org.bukkit.block.Block block = root.getLocation().add(0, layer, 0).getBlock();
                if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
            }
        }
        String group = root.getUniqueId().toString();
        for (Entity entity : root.getWorld().getNearbyEntities(root.getLocation(), 3, 4, 3))
            if (group.equals(entity.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING))) entity.remove();
        root.remove();
    }

    private void migrate(Player player) {
        int count = 0;
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (!(entity instanceof Interaction) || !entity.getScoreboardTags().contains("casino.exchange") || isMachine(entity)) continue;
            // Erst neues Modell erfolgreich erzeugen, dann den alten Automaten entfernen.
            spawnMachine(entity.getLocation());
            for (Entity passenger : entity.getPassengers()) passenger.remove();
            entity.remove(); count++;
        }
        player.sendMessage(count + " alte Automaten übernommen. Das Casino-Datapack muss deaktiviert bleiben.");
    }

    private boolean canUseShop(Player player, UUID machine) {
        Entity root = Bukkit.getEntity(machine);
        return root != null && isMachine(root) && root.getWorld() == player.getWorld()
                && player.getGameMode() != GameMode.SPECTATOR && root.getLocation().distanceSquared(player.getLocation()) <= 64;
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChipShop shop) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
            int slot = event.getRawSlot();
            Bukkit.getScheduler().runTask(this, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != shop) return;
                if (!canUseShop(player, shop.machine)) { player.closeInventory(); return; }
                try { shop.click(slot, player); }
                catch (IllegalArgumentException error) { player.sendMessage(error.getMessage()); }
                catch (IOException error) {
                    getLogger().log(java.util.logging.Level.SEVERE, "Chipkauf konnte nicht gespeichert werden", error);
                    player.sendMessage("Speichern fehlgeschlagen. Es wurde nichts abgebucht.");
                }
            });
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof SlotMenu slots) {
            event.setCancelled(true);
            if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) slots.click(event.getRawSlot());
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ExchangeMenu menu)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (menu.selection) {
            event.setCancelled(true);
            if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
            if (raw == ExchangeMenu.EXCHANGE_OPTION) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != menu) return;
                    Entity machine = Bukkit.getEntity(menu.machine);
                    if (machine == null || !isMachine(machine) || machine.getWorld() != player.getWorld()
                            || machine.getLocation().distanceSquared(player.getLocation()) > 64) {
                        player.closeInventory();
                        return;
                    }
                    player.openInventory(new ExchangeMenu(menu.machine, false).getInventory());
                });
            } else if (raw == ExchangeMenu.CHIPS_OPTION) {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == menu && canUseShop(player, menu.machine))
                        player.openInventory(new ChipShop(menu.machine, chips, accounts).getInventory());
                });
            }
            return;
        }
        if (raw < 0) return;
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) { event.setCancelled(true); return; }
        if (raw < 27) {
            if (raw != ExchangeRules.INPUT) {
                event.setCancelled(true);
                if (raw == ExchangeRules.CONFIRM && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                    Entity machine = Bukkit.getEntity(menu.machine);
                    if (machine == null || !isMachine(machine) || machine.getWorld() != player.getWorld()
                            || machine.getLocation().distanceSquared(player.getLocation()) > 64
                            || player.getGameMode() == GameMode.SPECTATOR) return;
                    try { menu.exchange(player, accounts); }
                    catch (IOException error) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Umtausch konnte nicht gespeichert werden", error);
                        player.sendMessage("Speichern fehlgeschlagen. Dein Eisen bleibt im Eingabefeld.");
                    } catch (ArithmeticException error) {
                        player.sendMessage("Dein Guthabenkonto ist voll. Dein Eisen bleibt im Eingabefeld.");
                    }
                }
                if (raw == ExchangeRules.CLOSE) Bukkit.getScheduler().runTask(this, () -> player.closeInventory());
                return;
            }
            // Hotbar-/Offhand-Tausch und Creative-Klonen im Eingabefeld sperren.
            if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND || event.getClick() == ClickType.MIDDLE) {
                event.setCancelled(true); return;
            }
            ItemStack cursor = event.getCursor();
            if (!cursor.getType().isAir() && !ExchangeRules.accepts(cursor.getType())) { event.setCancelled(true); return; }
        } else if (event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack source = event.getCurrentItem();
            if (source == null || !ExchangeRules.accepts(source.getType()) || isItem(source)) return;
            ItemStack target = menu.getInventory().getItem(ExchangeRules.INPUT);
            if (target != null && !target.isSimilar(source)) return;
            int existing = target == null ? 0 : target.getAmount();
            int move = Math.min(source.getAmount(), source.getMaxStackSize() - existing);
            if (move > 0) {
                ItemStack result = source.clone(); result.setAmount(existing + move);
                menu.getInventory().setItem(ExchangeRules.INPUT, result);
                source.setAmount(source.getAmount() - move);
                event.setCurrentItem(source.getAmount() == 0 ? null : source);
            }
        }
        Bukkit.getScheduler().runTask(this, menu::refresh);
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChipShop) { event.setCancelled(true); return; }
        if (event.getView().getTopInventory().getHolder() instanceof SlotMenu) { event.setCancelled(true); return; }
        if (!(event.getView().getTopInventory().getHolder() instanceof ExchangeMenu menu)) return;
        if (menu.selection) { event.setCancelled(true); return; }
        for (int slot : event.getRawSlots()) {
            if (slot < 27 && (slot != ExchangeRules.INPUT || !ExchangeRules.accepts(event.getOldCursor().getType()))) {
                event.setCancelled(true); return;
            }
        }
        Bukkit.getScheduler().runTask(this, menu::refresh);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof SlotMenu slots) slots.close();
        if (event.getInventory().getHolder() instanceof ExchangeMenu menu && event.getPlayer() instanceof Player player) menu.returnInput(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof SlotMenu slots) slots.close();
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof ExchangeMenu menu) menu.returnInput(event.getPlayer());
    }
}
