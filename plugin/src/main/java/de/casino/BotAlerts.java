package de.casino;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** One notification per changed blocking reason; offline notices survive a restart. */
final class BotAlerts implements Listener {
    private final JavaPlugin plugin;
    private final Path path;
    private final YamlConfiguration data = new YamlConfiguration();
    BotAlerts(JavaPlugin plugin) throws Exception {
        this.plugin=plugin; path=plugin.getDataFolder().toPath().resolve("bot-meldungen.yml");
        if (Files.exists(path)) data.load(path.toFile());
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    static boolean blocked(String status) {
        return status.startsWith("Pause") || status.startsWith("Warte") || status.contains("fehlt") || status.contains("voll")
                || status.contains("gesperrt") || status.contains("Fehler") || status.contains("Hindernis") || status.contains("nicht verfügbar");
    }
    void report(UUID bot, UUID owner, String name, String status) {
        String key=bot.toString();
        if (!blocked(status)) return;
        if (status.equals(data.getString(key+".last"))) return;
        data.set(key+".last",status); data.set(key+".owner",owner.toString());
        String message="["+name+"] "+status;
        var player=Bukkit.getPlayer(owner);
        if (player!=null) { player.sendMessage(message); data.set(key+".pending",null); }
        else data.set(key+".pending",message);
        save();
    }
    void progressed(UUID bot) {
        String key=bot.toString();
        if (data.contains(key)) { data.set(key,null); save(); }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        boolean changed=false;
        for (String key:data.getKeys(false)) if (event.getPlayer().getUniqueId().toString().equals(data.getString(key+".owner")) && data.contains(key+".pending")) {
            event.getPlayer().sendMessage(data.getString(key+".pending")); data.set(key+".pending",null); changed=true;
        }
        if (changed) save();
    }
    private void save() {
        try { Path temp=path.resolveSibling(path.getFileName()+".tmp"); Files.writeString(temp,data.saveToString()); Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (Exception ex) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bot-Meldungen speichern fehlgeschlagen",ex); }
    }
}
