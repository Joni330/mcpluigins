package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

final class Elevators implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey marker, recipe;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    Elevators(JavaPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "elevator"); recipe = new NamespacedKey(plugin, "elevator_recipe");
    }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ShapedRecipe crafting = new ShapedRecipe(recipe, item());
        crafting.shape("III", "IEI", "III");
        crafting.setIngredient('I', Material.IRON_INGOT); crafting.setIngredient('E', Material.ENDER_PEARL);
        Bukkit.removeRecipe(recipe); Bukkit.addRecipe(crafting);
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipe(recipe));
    }
    void disable() { Bukkit.removeRecipe(recipe); cooldown.clear(); }
    private ItemStack item() {
        ItemStack item = ExchangeMenu.icon(Material.DAYLIGHT_DETECTOR, "Aufzug");
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        meta.lore(List.of(Component.text("Springen: hoch · Schleichen: runter"), Component.text("Etagen direkt übereinander platzieren.")));
        item.setItemMeta(meta); return item;
    }
    private boolean elevator(Block block) {
        return block.getType() == Material.DAYLIGHT_DETECTOR && block.getState() instanceof TileState state
                && state.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }
    private Block standing(Location location) {
        Block block = location.getBlock();
        if (!elevator(block)) block = block.getRelative(0, -1, 0);
        return elevator(block) && Math.abs(location.getY() - (block.getY() + .375)) < .12 ? block : null;
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void place(BlockPlaceEvent event) {
        var meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return;
        if (!(event.getBlockPlaced().getState() instanceof TileState state)) { event.setCancelled(true); return; }
        state.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        if (!state.update(false, false)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void drop(BlockDropItemEvent event) {
        if (!(event.getBlockState() instanceof TileState state) || !state.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return;
        if (!event.getItems().isEmpty()) {
            event.getItems().getFirst().setItemStack(item());
            for (int i = event.getItems().size() - 1; i > 0; i--) event.getItems().remove(i).remove();
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void jump(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null || event.getTo().getY() - event.getFrom().getY() < .05) return;
        Player player = event.getPlayer();
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) return;
        Block source = standing(event.getFrom());
        if (source != null) travel(player, source, true);
    }
    @EventHandler(ignoreCancelled = true)
    public void sneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Block source = standing(event.getPlayer().getLocation());
        if (source != null) travel(event.getPlayer(), source, false);
    }
    private void travel(Player player, Block source, boolean up) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.isInsideVehicle()) return;
        long now = System.nanoTime();
        if (cooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
        cooldown.put(player.getUniqueId(), now + 750_000_000L);
        int y = ElevatorRules.next(source.getY(), source.getWorld().getMinHeight(), source.getWorld().getMaxHeight(), up,
                height -> elevator(source.getWorld().getBlockAt(source.getX(), height, source.getZ())));
        if (y == Integer.MIN_VALUE) { player.sendActionBar(Component.text("Keine weitere Aufzugsetage " + (up ? "darüber." : "darunter."))); return; }
        Block target = source.getWorld().getBlockAt(source.getX(), y, source.getZ());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead() || player.getWorld() != source.getWorld()
                    || player.getLocation().distanceSquared(source.getLocation().add(.5, .375, .5)) > 4
                    || !elevator(source) || !elevator(target)) return;
            Location destination = target.getLocation().add(.5, .375, .5);
            if (y + 2 >= target.getWorld().getMaxHeight() || !clear(target.getRelative(0, 1, 0))
                    || !clear(target.getRelative(0, 2, 0)) || !target.getWorld().getWorldBorder().isInside(destination)) {
                player.sendActionBar(Component.text("Ziel blockiert: Zwei freie Blöcke über dem Aufzug benötigt.")); return;
            }
            destination.setYaw(player.getLocation().getYaw()); destination.setPitch(player.getLocation().getPitch());
            if (player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                player.setFallDistance(0);
                player.setVelocity(new org.bukkit.util.Vector());
                player.playSound(destination, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .6f, up ? 1.3f : .8f);
            }
        });
    }
    private boolean clear(Block block) { return block.isPassable() && !block.isLiquid()
            && block.getType() != Material.FIRE && block.getType() != Material.SOUL_FIRE && block.getType() != Material.POWDER_SNOW; }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipe(recipe); }
    @EventHandler public void quit(PlayerQuitEvent event) { cooldown.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled = true) public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::elevator)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void retract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::elevator)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void explode(EntityExplodeEvent event) { event.blockList().removeIf(this::elevator); }
    @EventHandler(ignoreCancelled = true) public void explode(BlockExplodeEvent event) { event.blockList().removeIf(this::elevator); }
}
