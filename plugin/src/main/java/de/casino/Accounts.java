package de.casino;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.nio.file.*;

final class Accounts {
    private final Path file;
    private final YamlConfiguration data;

    Accounts(Path folder) throws Exception {
        Files.createDirectories(folder);
        file = folder.resolve("accounts.yml");
        data = new YamlConfiguration();
        if (Files.exists(file)) data.load(file.toFile()); // Fehler niemals mit leeren Konten überschreiben.
        if (!"cents".equals(data.getString("currency-unit"))) {
            if (data.contains("currency-unit")) throw new IOException("Unbekannte Guthabeneinheit");
            // Vor der einmaligen Euro->Cent-Migration Originaldatei sichern.
            if (Files.exists(file)) Files.copy(file, folder.resolve("accounts-before-cents-" + java.util.UUID.randomUUID() + ".yml"));
            for (String key : data.getKeys(false)) {
                if (data.contains(key + ".balance")) {
                    long euros = data.getLong(key + ".balance");
                    if (euros < 0) throw new IOException("Negatives Guthaben in " + key);
                    data.set(key + ".balance", Math.multiplyExact(euros, 100L));
                }
            }
            data.set("currency-unit", "cents");
            save();
        }
    }

    long balance(Player player) throws IOException {
        String key = player.getUniqueId().toString();
        if (!data.contains(key + ".balance")) {
            long balance = 0;
            var objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("casino_balance");
            if (objective != null && objective.getScore(player.getName()).isScoreSet())
                balance = Math.multiplyExact((long) Math.max(0, objective.getScore(player.getName()).getScore()), 100L);
            data.set(key + ".balance", balance);
            data.set(key + ".name", player.getName());
            try { save(); }
            catch (IOException error) { data.set(key, null); throw error; }
        }
        return data.getLong(key + ".balance");
    }

    long credit(Player player, long amount) throws IOException {
        if (amount <= 0) throw new IllegalArgumentException("Gutschrift muss positiv sein");
        long previous = balance(player);
        long updated = Math.addExact(previous, amount);
        String key = player.getUniqueId() + ".balance";
        data.set(key, updated);
        try { save(); }
        catch (IOException error) { data.set(key, previous); throw error; }
        return updated;
    }

    void settleSpin(Player player, long bet, long payout) throws IOException {
        long previous = balance(player);
        long updated = SlotRules.settledBalance(previous, bet, payout);
        String key = player.getUniqueId() + ".balance";
        String winKey = player.getUniqueId() + ".last-payout";
        Object previousPayout = data.get(winKey);
        data.set(key, updated);
        data.set(winKey, payout);
        try { save(); }
        catch (IOException error) { data.set(key, previous); data.set(winKey, previousPayout); throw error; }
    }

    long lastPayout(Player player) { return data.getLong(player.getUniqueId() + ".last-payout", 0L); }

    void debit(java.util.UUID player, long cents) throws IOException {
        if (cents <= 0) throw new IllegalArgumentException("Bitte zuerst Chips auswählen.");
        String key = player + ".balance";
        if (!data.contains(key)) throw new IllegalArgumentException("Konto nicht gefunden.");
        long previous = data.getLong(key);
        if (previous < cents) throw new IllegalArgumentException("Du hast nicht genügend Guthaben.");
        data.set(key, previous - cents);
        try { save(); }
        catch (IOException error) { data.set(key, previous); throw error; }
    }

    void transfer(java.util.UUID sender, java.util.UUID recipient, long cents) throws IOException {
        if (sender.equals(recipient)) throw new IllegalArgumentException("Du kannst dir nicht selbst Geld überweisen.");
        if (cents <= 0) throw new IllegalArgumentException("Der Betrag muss größer als 0 sein.");
        String from = sender + ".balance", to = recipient + ".balance";
        if (!data.contains(from) || !data.contains(to)) throw new IllegalArgumentException("Konto nicht gefunden.");
        long previousFrom = data.getLong(from), previousTo = data.getLong(to);
        if (previousFrom < cents) throw new IllegalArgumentException("Du hast nicht genügend Guthaben.");
        long nextTo = Math.addExact(previousTo, cents);
        data.set(from, previousFrom - cents);
        data.set(to, nextTo);
        try { save(); }
        catch (IOException error) { data.set(from, previousFrom); data.set(to, previousTo); throw error; }
    }

    private void save() throws IOException {
        Path temporary = file.resolveSibling("accounts.yml.tmp");
        Files.writeString(temporary, data.saveToString());
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}
