package de.casino;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Single-colour chip stacks persisted through their tagged display entities. */
final class Chips implements Listener {
    enum Kind {
        RED("red", "rot", "Roter Chip · 1 €", 1),
        GREEN("green", "gruen", "Grüner Chip · 5 €", 5),
        BLUE("blue", "blau", "Blauer Chip · 10 €", 10),
        PURPLE("purple", "lila", "Lila Chip · 20 €", 20);
        final String id, model, title;
        final int euros;
        Kind(String id, String model, String title, int euros) { this.id = id; this.model = model; this.title = title; this.euros = euros; }
        static Kind fromId(String id) {
            return Arrays.stream(values()).filter(k -> k.id.equals(id)).findFirst().orElse(null);
        }
        static Kind fromName(String name) {
            String normalized = name.toLowerCase(Locale.ROOT).replace("ü", "ue");
            return Arrays.stream(values()).filter(k -> k.model.equals(normalized)).findFirst().orElse(null);
        }
    }
    private static final int MAX_STACK = 16;
    private static final double CHIP_HEIGHT = 1.0 / 16;
    private final NamespacedKey chipKey, groupKey;
    private final JavaPlugin plugin;
    Chips(JavaPlugin plugin) {
        this.plugin = plugin;
        chipKey = new NamespacedKey(plugin, "chip");
        groupKey = new NamespacedKey(plugin, "chip_group");
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getWorlds().forEach(world ->
                world.getEntities().stream().filter(this::isRoot).forEach(root -> refreshLabel(root, layers(root).size()))));
    }
    @EventHandler public void loadStacks(EntitiesLoadEvent event) {
        List<Entity> roots = event.getEntities().stream().filter(this::isRoot).toList();
        if (roots.isEmpty()) return;
        Bukkit.getScheduler().runTask(plugin, () -> roots.stream().filter(Entity::isValid)
                .forEach(root -> refreshLabel(root, layers(root).size())));
    }
    private void refreshLabel(Entity root, int count) {
        List<TextDisplay> labels = root.getWorld().getNearbyEntities(root.getLocation(), 1, 2, 1).stream()
                .filter(e -> e instanceof TextDisplay && root.getUniqueId().toString().equals(e.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING)))
                .map(e -> (TextDisplay) e).toList();
        if (count == 0) { labels.forEach(Entity::remove); return; }
        Location position = root.getLocation().add(0, count * CHIP_HEIGHT + .25, 0);
        TextDisplay label;
        if (labels.isEmpty()) {
            label = root.getWorld().spawn(position, TextDisplay.class, e -> {
                e.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, root.getUniqueId().toString());
                e.setPersistent(true);
                e.setBillboard(Display.Billboard.CENTER);
                e.setShadowed(true);
                e.setSeeThrough(false);
                e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                        new org.joml.Quaternionf(), new org.joml.Vector3f(.5f), new org.joml.Quaternionf()));
            });
        } else {
            label = labels.getFirst();
            labels.stream().skip(1).forEach(Entity::remove);
            label.teleport(position);
        }
        label.text(Component.text((count * kind(root).euros) + " €", NamedTextColor.GOLD));
    }
    ItemStack item(Kind kind) {
        ItemStack item = ExchangeMenu.icon(Material.PAPER, kind.title);
        var meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("casino:chip_" + kind.model));
        meta.getPersistentDataContainer().set(chipKey, PersistentDataType.STRING, kind.id);
        item.setItemMeta(meta);
        return item;
    }
    private boolean isChip(ItemStack item) {
        return kind(item) != null;
    }
    private Kind kind(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null : Kind.fromId(item.getItemMeta().getPersistentDataContainer().get(chipKey, PersistentDataType.STRING));
    }
    private Kind kind(Entity root) {
        return Kind.fromId(root.getPersistentDataContainer().get(chipKey, PersistentDataType.STRING));
    }
    private boolean isRoot(Entity entity) {
        return entity instanceof Interaction && kind(entity) != null;
    }
    private List<ItemDisplay> layers(Entity root) {
        return root.getWorld().getNearbyEntities(root.getLocation(), 1, 2, 1).stream()
                .filter(e -> e instanceof ItemDisplay && root.getUniqueId().toString().equals(e.getPersistentDataContainer().get(groupKey, PersistentDataType.STRING)))
                .map(e -> (ItemDisplay) e).sorted(Comparator.comparingDouble(e -> e.getLocation().getY())).toList();
    }
    private void consume(Player player, EquipmentSlot hand) {
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        ItemStack held = player.getInventory().getItem(hand);
        held.subtract(1); player.getInventory().setItem(hand, held);
    }
    private void addChip(Interaction root, Player player, EquipmentSlot hand) {
        if (!isChip(player.getInventory().getItem(hand))) return;
        Kind kind = kind(root);
        if (kind != kind(player.getInventory().getItem(hand))) {
            player.sendMessage("Auf einen Stapel passen nur Chips derselben Farbe."); return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.CREATIVE) return;
        List<ItemDisplay> existing = layers(root);
        if (existing.isEmpty()) { player.sendMessage("Dieser Chipstapel ist unvollständig."); return; }
        if (existing.size() >= MAX_STACK) { player.sendMessage("Maximal 16 Chips pro Stapel."); return; }
        if (!root.getLocation().getBlock().getType().isAir()) { player.sendMessage("Hier ist kein Platz für weitere Chips."); return; }
        Location next = root.getLocation().add(0, .5 + existing.size() * CHIP_HEIGHT, 0);
        root.getWorld().spawn(next, ItemDisplay.class, e -> {
            e.setItemStack(item(kind));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setRotation(existing.getFirst().getYaw(), 0);
            e.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, root.getUniqueId().toString());
            e.setPersistent(true);
        });
        root.setInteractionHeight((float) Math.max(.1, (existing.size() + 1) * CHIP_HEIGHT));
        consume(player, hand);
        refreshLabel(root, existing.size() + 1);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void stack(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent || !isRoot(event.getRightClicked())) return;
        event.setCancelled(true);
        // Use the main hand only: a single right-click must never add two chips.
        if (event.getHand() != EquipmentSlot.HAND) return;
        addChip((Interaction) event.getRightClicked(), event.getPlayer(), event.getHand());
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void place(PlayerInteractEvent event) {
        if (!isChip(event.getItem())) return;
        Kind kind = kind(event.getItem());
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.CREATIVE) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP) {
            player.sendMessage("Bitte den Chip auf einer Blockoberseite ablegen."); return;
        }
        // Full blocks only in this first placement version.
        var support = event.getClickedBlock();
        if (!support.getType().isOccluding()) {
            player.sendMessage("Bitte zunächst einen vollen Block als Unterlage verwenden."); return;
        }
        Location base = support.getLocation().add(.5, 1, .5);
        for (Entity nearby : base.getWorld().getNearbyEntities(base, .1, .1, .1, this::isRoot)) {
            if (event.getHand() == EquipmentSlot.HAND) addChip((Interaction) nearby, player, event.getHand());
            return;
        }
        if (!base.getBlock().getType().isAir() || !base.getWorld().getNearbyEntities(base, .5, .2, .5,
                e -> e instanceof Interaction).isEmpty()) {
            player.sendMessage("Hier liegt bereits etwas."); return;
        }
        List<Entity> created = new ArrayList<>();
        try {
            Interaction root = base.getWorld().spawn(base, Interaction.class, e -> {
                e.setInteractionWidth(.5f); e.setInteractionHeight(.1f); e.setResponsive(true);
                e.getPersistentDataContainer().set(chipKey, PersistentDataType.STRING, kind.id);
                e.setPersistent(true);
            });
            created.add(root);
            ItemDisplay display = base.getWorld().spawn(base.clone().add(0, .5, 0), ItemDisplay.class, e -> {
                e.setItemStack(item(kind));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setRotation(Math.round(player.getYaw() / 90f) * 90f + 180f, 0);
                e.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, root.getUniqueId().toString());
                e.setPersistent(true);
            });
            created.add(display);
            refreshLabel(root, 1);
        } catch (RuntimeException failure) { created.forEach(Entity::remove); throw failure; }
        consume(player, Objects.requireNonNull(event.getHand()));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakChip(EntityDamageByEntityEvent event) {
        Entity root = event.getEntity();
        if (!isRoot(root)) return;
        Kind kind = kind(root);
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.CREATIVE) return;
        Location base = root.getLocation();
        List<ItemDisplay> existing = layers(root);
        if (existing.isEmpty()) { refreshLabel(root, 0); root.remove(); return; }
        existing.getLast().remove();
        refreshLabel(root, existing.size() - 1);
        if (existing.size() == 1) {
            root.getPersistentDataContainer().remove(chipKey);
            root.remove();
        } else {
            ((Interaction) root).setInteractionHeight((float) Math.max(.1, (existing.size() - 1) * CHIP_HEIGHT));
        }
        base.getWorld().dropItemNaturally(base.clone().add(0, existing.size() * CHIP_HEIGHT + .15, 0), item(kind));
    }
}
