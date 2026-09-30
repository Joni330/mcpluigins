package de.casino;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Stationary design prototype, using vanilla entities and item models. */
final class MiningBotDesign implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey marker;
    private final MiningBots bots;
    private final AdvancedMiningBots upgrades;
    MiningBotDesign(JavaPlugin plugin, MiningBots bots, AdvancedMiningBots upgrades) { this.plugin = plugin; this.bots = bots; this.upgrades=upgrades; marker = new NamespacedKey(plugin, "miningbot_design"); }
    private boolean marked(Entity entity) { return entity.getPersistentDataContainer().has(marker, PersistentDataType.BYTE); }
    private void mark(Entity entity) { entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1); entity.setPersistent(true); }
    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = Objects.requireNonNull(plugin.getCommand("miningbot"));
        command.setTabCompleter((s,c,a,args) -> args.length == 1 ? List.of("give", "steinbruch", "erzsucher", "holzfaeller", "design", "entfernen").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of());
        command.setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("Bitte im Spiel ausführen."); return true; }
            if (args.length != 1) return false;
            if (args[0].equalsIgnoreCase("give")) { bots.give(player); return true; }
            if (args[0].equalsIgnoreCase("steinbruch")) { upgrades.give(player,AdvancedBotData.Kind.QUARRY); return true; }
            if (args[0].equalsIgnoreCase("erzsucher")) { upgrades.give(player,AdvancedBotData.Kind.SEEKER); return true; }
            if (args[0].equalsIgnoreCase("holzfaeller")||args[0].equalsIgnoreCase("holzfäller")) { upgrades.give(player,AdvancedBotData.Kind.LUMBER); return true; }
            if (args[0].equalsIgnoreCase("entfernen")) {
                var hit = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 6, .35, e -> marked(e) || bots.isPart(e));
                if (hit == null || hit.getHitEntity() == null) { player.sendMessage("Schaue einen MiningBot-Entwurf in deiner Nähe an."); return true; }
                Entity root = hit.getHitEntity();
                if (bots.isPart(root)) { bots.remove(player, root); return true; }
                if (root.getVehicle() != null && marked(root.getVehicle())) root = root.getVehicle();
                for (Entity child : List.copyOf(root.getPassengers())) if (marked(child)) child.remove();
                root.remove(); player.sendMessage("MiningBot-Entwurf entfernt."); return true;
            }
            if (!args[0].equalsIgnoreCase("design")) return false;
            var block = player.getTargetBlockExact(6);
            if (block == null || !block.getType().isOccluding() || !block.getRelative(0, 1, 0).getType().isAir() || !block.getRelative(0, 2, 0).getType().isAir()) {
                player.sendMessage("Schaue auf einen vollen Bodenblock mit zwei freien Blöcken darüber."); return true;
            }
            Location location = block.getLocation().add(.5, 1.05, .5);
            if (!location.getWorld().getNearbyEntities(location, 1, 1, 1, this::marked).isEmpty()) {
                player.sendMessage("Hier steht bereits ein MiningBot-Entwurf."); return true;
            }
            location.setYaw(Math.round(player.getLocation().getYaw() / 90f) * 90f);
            List<Entity> created = new ArrayList<>();
            try {
                PoweredMinecart cart = location.getWorld().spawn(location, PoweredMinecart.class, entity -> {
                    mark(entity); entity.setGravity(false); entity.setInvulnerable(true); entity.setMaxSpeed(0);
                    entity.setSlowWhenEmpty(false); entity.customName(Component.text("MiningBot · Design"));
                });
                created.add(cart);
                tool(cart, Material.IRON_PICKAXE, -.72f, location.getYaw(), created);
                tool(cart, Material.IRON_SHOVEL, .72f, location.getYaw(), created);
                player.sendMessage("MiningBot-Design platziert: Ofenlore mit Eisenwerkzeugen. Noch ohne Abbau-Funktion.");
            } catch (RuntimeException error) {
                created.forEach(Entity::remove);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "MiningBot-Entwurf konnte nicht erzeugt werden", error);
                player.sendMessage("Entwurf konnte nicht platziert werden.");
            }
            return true;
        });
    }
    private void tool(PoweredMinecart cart, Material material, float side, float yaw, List<Entity> created) {
        ItemDisplay display = cart.getWorld().spawn(cart.getLocation(), ItemDisplay.class, entity -> {
            mark(entity); entity.setInvulnerable(true); entity.setGravity(false);
            entity.setItemStack(new ItemStack(material));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setRotation(yaw, 0);
            entity.setTransformation(new Transformation(new Vector3f(side, .15f, 0),
                    new Quaternionf().rotateY((float) Math.PI / 2), new Vector3f(.8f), new Quaternionf()));
        });
        created.add(display);
        if (!cart.addPassenger(display)) throw new IllegalStateException("Werkzeug konnte nicht befestigt werden");
    }
    @EventHandler public void interact(PlayerInteractEntityEvent event) { if (marked(event.getRightClicked())) event.setCancelled(true); }
    @EventHandler public void damage(VehicleDamageEvent event) { if (marked(event.getVehicle())) event.setCancelled(true); }
    @EventHandler public void destroy(VehicleDestroyEvent event) { if (marked(event.getVehicle())) event.setCancelled(true); }
    @EventHandler public void collide(VehicleEntityCollisionEvent event) { if (marked(event.getVehicle())) event.setCancelled(true); }
}
