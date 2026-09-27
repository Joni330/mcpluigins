package de.casino;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.io.IOException;

final class StorageTeamCommand implements CommandExecutor, TabCompleter {
    private final StorageTeams teams;
    StorageTeamCommand(StorageTeams teams) { this.teams = teams; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Bitte im Spiel ausführen."); return true; }
        String action = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        UUID id = p.getUniqueId();
        try {
            if (action.equals("erstellen") && args.length == 2) teams.create(id, args[1]);
            else if (action.equals("einladen") && args.length == 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) throw new IllegalArgumentException("Der Spieler muss zum Einladen online sein.");
                teams.invite(id, target.getUniqueId());
                target.sendMessage(p.getName() + " lädt dich ins Lagerteam ein. Annehmen: /lagerteam annehmen " + teams.name(teams.team(id)));
            } else if (action.equals("annehmen") && args.length == 2) teams.accept(id, args[1]);
            else if (action.equals("verlassen") && args.length == 1) teams.leave(id);
            else if (action.equals("aufloesen") && args.length == 1) teams.dissolve(id);
            else if (action.equals("entfernen") && args.length == 2) {
                UUID target = teams.members(id).stream().filter(member -> {
                    String name = Bukkit.getOfflinePlayer(member).getName();
                    return member.toString().equals(args[1]) || (name != null && name.equalsIgnoreCase(args[1]));
                }).findFirst().orElseThrow(() -> new IllegalArgumentException("Mitglied nicht gefunden."));
                teams.kick(id, target);
            } else if (action.equals("info") && args.length <= 1) {
                String team = teams.team(id);
                if (team == null) p.sendMessage("Du bist in keinem Lagerteam. /lagerteam erstellen <Name>");
                else {
                    p.sendMessage("Lagerteam: " + teams.name(team) + (teams.leader(id) ? " (Teamleiter)" : ""));
                    p.sendMessage("Mitglieder: " + String.join(", ", teams.members(id).stream().map(m -> {
                        String name = Bukkit.getOfflinePlayer(m).getName(); return name == null ? m.toString() : name;
                    }).toList()));
                }
                return true;
            } else { p.sendMessage("/lagerteam erstellen <Name> | einladen <Spieler> | annehmen <Team> | info | verlassen | entfernen <Spieler> | aufloesen"); return true; }
            p.sendMessage("Lagerteam aktualisiert. Teammitglieder können die Lager der anderen Mitglieder benutzen.");
        } catch (IllegalArgumentException error) { p.sendMessage(error.getMessage()); }
        catch (IOException error) { p.sendMessage("Team konnte nicht gespeichert werden. Keine Änderung übernommen."); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("erstellen", "einladen", "annehmen", "info", "verlassen", "entfernen", "aufloesen").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("einladen")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
