package de.casino;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.*;
import java.io.IOException;

final class Homes implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final HomeData data;
    private final StorageTeams teams;
    Homes(JavaPlugin plugin, HomeData data, StorageTeams teams) { this.plugin = plugin; this.data = data; this.teams = teams; }
    void enable() {
        for (String name : List.of("sethome", "home", "delhome", "setteamhome", "teamhome")) {
            var command = Objects.requireNonNull(plugin.getCommand(name));
            command.setExecutor(this); command.setTabCompleter(this);
        }
    }
    private HomeData.Point point(Player player) {
        Location l = player.getLocation();
        return new HomeData.Point(l.getWorld().getUID(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Bitte im Spiel ausführen."); return true; }
        String cmd = command.getName();
        boolean teamCommand = cmd.equals("teamhome") || cmd.equals("setteamhome");
        if (args.length != (teamCommand ? 0 : 1)) return false;
        try {
            switch (cmd) {
                case "sethome" -> {
                    data.set(player.getUniqueId(), args[0], point(player));
                    player.sendMessage("Home '" + HomeData.name(args[0]) + "' gespeichert (" + data.list(player.getUniqueId()).size() + "/3).");
                }
                case "delhome" -> { data.delete(player.getUniqueId(), args[0]); player.sendMessage("Home gelöscht."); }
                case "home" -> {
                    if (args[0].equalsIgnoreCase("list")) {
                        var list = data.list(player.getUniqueId());
                        player.sendMessage(list.isEmpty() ? "Noch keine Homes. Nutze /sethome <name>." : "Deine Homes (" + list.size() + "/3): " + String.join(", ", list));
                    } else teleport(player, data.get(player.getUniqueId(), args[0]));
                }
                case "setteamhome" -> { teams.setHome(player.getUniqueId(), point(player)); player.sendMessage("Teamhome für dein Team gespeichert."); }
                case "teamhome" -> teleport(player, teams.home(player.getUniqueId()));
            }
        } catch (IllegalArgumentException error) { player.sendMessage(error.getMessage()); }
        catch (IOException error) { player.sendMessage("Home konnte nicht gespeichert werden. Die bisherigen Homes bleiben erhalten."); plugin.getLogger().log(java.util.logging.Level.SEVERE, "Homes speichern fehlgeschlagen", error); }
        return true;
    }
    private void teleport(Player player, HomeData.Point point) {
        World world = Bukkit.getWorld(point.world());
        if (world == null) { player.sendMessage("Die Welt dieses Homes ist nicht geladen."); return; }
        Location destination = new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        if (!world.getWorldBorder().isInside(destination) || point.y() < world.getMinHeight() || point.y() >= world.getMaxHeight()) {
            player.sendMessage("Dieses Home liegt außerhalb der Weltgrenzen."); return;
        }
        if (player.teleport(destination, PlayerTeleportEvent.TeleportCause.COMMAND)) player.sendMessage("Zum Home teleportiert.");
        else player.sendMessage("Teleportation wurde verhindert.");
    }
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        if (!List.of("home", "delhome", "sethome").contains(command.getName())) return List.of();
        List<String> options = new ArrayList<>(data.list(player.getUniqueId()));
        if (command.getName().equals("home")) options.add("list");
        return options.stream().filter(n -> n.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
