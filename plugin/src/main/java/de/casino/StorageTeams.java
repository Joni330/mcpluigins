package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class StorageTeams {
    private final Path file;
    private YamlConfiguration data = new YamlConfiguration();
    StorageTeams(Path folder) throws Exception {
        Files.createDirectories(folder); file = folder.resolve("storage-teams.yml");
        if (Files.exists(file)) data.load(file.toFile());
    }
    String team(UUID player) { return data.getString("members." + player); }
    boolean shares(UUID owner, UUID player) {
        if (owner.equals(player)) return true;
        String team = team(owner);
        return team != null && team.equals(team(player));
    }
    String name(String key) { return data.getString("teams." + key + ".name", key); }
    boolean leader(UUID player) {
        String team = team(player);
        return team != null && player.toString().equals(data.getString("teams." + team + ".leader"));
    }
    private String key(String name) {
        if (!name.matches("[A-Za-z0-9_-]{3,20}")) throw new IllegalArgumentException("Teamname: 3–20 Buchstaben, Zahlen, _ oder -.");
        return name.toLowerCase(Locale.ROOT);
    }
    private void commit(java.util.function.Consumer<YamlConfiguration> edit) throws IOException {
        YamlConfiguration next = new YamlConfiguration();
        try { next.loadFromString(data.saveToString()); }
        catch (Exception e) { throw new IOException(e); }
        edit.accept(next);
        Path temp = file.resolveSibling("storage-teams.yml.tmp");
        Files.writeString(temp, next.saveToString());
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        data = next;
    }
    void create(UUID player, String name) throws IOException {
        String key = key(name);
        if (team(player) != null) throw new IllegalArgumentException("Du bist bereits in einem Team.");
        if (data.contains("teams." + key)) throw new IllegalArgumentException("Dieser Teamname ist vergeben.");
        commit(d -> { d.set("teams." + key + ".name", name); d.set("teams." + key + ".leader", player.toString()); d.set("members." + player, key); });
    }
    void invite(UUID sender, UUID target) throws IOException {
        if (!leader(sender)) throw new IllegalArgumentException("Nur der Teamleiter darf einladen.");
        if (team(target) != null) throw new IllegalArgumentException("Dieser Spieler ist bereits in einem Team.");
        String key = team(sender);
        commit(d -> d.set("invites." + target + "." + key, System.currentTimeMillis() + 600_000L));
    }
    void accept(UUID player, String name) throws IOException {
        String key = key(name);
        if (team(player) != null) throw new IllegalArgumentException("Du bist bereits in einem Team.");
        if (!data.contains("teams." + key) || data.getLong("invites." + player + "." + key) <= System.currentTimeMillis())
            throw new IllegalArgumentException("Keine gültige Einladung. Einladungen gelten 10 Minuten.");
        commit(d -> { d.set("members." + player, key); d.set("invites." + player, null); });
    }
    void leave(UUID player) throws IOException {
        if (team(player) == null) throw new IllegalArgumentException("Du bist in keinem Team.");
        if (leader(player)) throw new IllegalArgumentException("Als Teamleiter musst du das Team mit /lagerteam aufloesen auflösen.");
        commit(d -> d.set("members." + player, null));
    }
    void kick(UUID sender, UUID target) throws IOException {
        if (!leader(sender)) throw new IllegalArgumentException("Nur der Teamleiter darf Mitglieder entfernen.");
        if (sender.equals(target) || !Objects.equals(team(sender), team(target))) throw new IllegalArgumentException("Kein entfernbares Mitglied deines Teams.");
        commit(d -> { d.set("members." + target, null); d.set("invites." + target, null); });
    }
    List<UUID> members(UUID player) {
        String team = team(player);
        var section = data.getConfigurationSection("members");
        if (team == null || section == null) return List.of();
        return section.getKeys(false).stream().filter(id -> team.equals(section.getString(id))).map(UUID::fromString).toList();
    }
    void dissolve(UUID player) throws IOException {
        if (!leader(player)) throw new IllegalArgumentException("Nur der Teamleiter darf das Team auflösen.");
        String key = team(player); List<UUID> members = members(player);
        commit(d -> {
            members.forEach(id -> d.set("members." + id, null)); d.set("teams." + key, null);
            var invites = d.getConfigurationSection("invites");
            if (invites != null) for (String target : invites.getKeys(false)) d.set("invites." + target + "." + key, null);
        });
    }
}
