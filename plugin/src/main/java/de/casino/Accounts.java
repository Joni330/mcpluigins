package de.casino;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

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
        if (!data.contains("wallet-version")) {
            if (Files.exists(file)) Files.copy(file, folder.resolve("accounts-before-wallets-" + UUID.randomUUID() + ".yml"));
            for (String key : data.getKeys(false)) if (data.contains(key + ".balance")) data.set(key + ".casino-balance", 0L);
            data.set("wallet-version", 1);
            save();
        } else if (data.getInt("wallet-version") != 1) throw new IOException("Unbekannte Kontenversion");
    }

    long balance(Player player) throws IOException {
        String key = player.getUniqueId().toString();
        if (!data.contains(key + ".balance")) {
            long balance = 0;
            var objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("casino_balance");
            if (objective != null && objective.getScore(player.getName()).isScoreSet())
                balance = Math.multiplyExact((long) Math.max(0, objective.getScore(player.getName()).getScore()), 100L);
            data.set(key + ".balance", balance);
            data.set(key + ".casino-balance", 0L);
            data.set(key + ".name", player.getName());
            try { save(); }
            catch (IOException error) { data.set(key, null); throw error; }
        }
        return data.getLong(key + ".balance");
    }

    long credit(Player player, long amount, String reason) throws IOException {
        if (amount <= 0) throw new IllegalArgumentException("Gutschrift muss positiv sein");
        long previous = balance(player);
        long updated = Math.addExact(previous, amount);
        String key = player.getUniqueId() + ".balance";
        data.set(key, updated);
        try { saveHistory(new Booking(player.getUniqueId(), reason, amount, updated, 0, 0)); }
        catch (IOException error) { data.set(key, previous); throw error; }
        return updated;
    }

    void settleSpin(Player player, long bet, long payout, String game) throws IOException {
        long previous = casinoBalance(player);
        long updated = SlotRules.settledBalance(previous, bet, payout);
        String key = player.getUniqueId() + ".casino-balance";
        String winKey = player.getUniqueId() + ".last-payout";
        Object previousPayout = data.get(winKey);
        data.set(key, updated);
        data.set(winKey, payout);
        try { saveHistory(new Booking(player.getUniqueId(), game, payout - bet, updated, bet, payout, "Casino")); }
        catch (IOException error) { data.set(key, previous); data.set(winKey, previousPayout); throw error; }
    }

    long lastPayout(Player player) { return data.getLong(player.getUniqueId() + ".last-payout", 0L); }
    int backpackRows(UUID player) {
        int rows = data.getInt(player + ".backpack-rows", 1);
        if (rows < 1 || rows > 6) throw new IllegalStateException("Ungültige Rucksackgröße");
        return rows;
    }
    void upgradeBackpack(UUID player, int expectedRows, long price) throws IOException {
        int rows = backpackRows(player);
        if (rows != expectedRows || rows >= 6 || price <= 0) throw new IllegalArgumentException("Dieses Upgrade ist nicht verfügbar.");
        String balanceKey = player + ".balance", rowsKey = player + ".backpack-rows";
        if (!data.contains(balanceKey)) throw new IllegalArgumentException("Konto nicht gefunden.");
        long before = data.getLong(balanceKey);
        if (before < price) throw new IllegalArgumentException("Nicht genügend Geld auf deinem Hauptkonto.");
        Object oldRows = data.get(rowsKey);
        data.set(rowsKey, rows + 1); data.set(balanceKey, before - price);
        try { saveHistory(new Booking(player, "Rucksack auf " + (rows + 1) + " Reihen erweitert", -price, before - price, 0, 0)); }
        catch (IOException error) { data.set(rowsKey, oldRows); data.set(balanceKey, before); throw error; }
    }
    void debit(UUID player, long cents) throws IOException { debit(player, cents, "Abbuchung"); }

    void debit(java.util.UUID player, long cents, String reason) throws IOException {
        if (cents <= 0) throw new IllegalArgumentException("Bitte zuerst Chips auswählen.");
        String key = player + ".balance";
        if (!data.contains(key)) throw new IllegalArgumentException("Konto nicht gefunden.");
        long previous = data.getLong(key);
        if (previous < cents) throw new IllegalArgumentException("Du hast nicht genügend Guthaben.");
        data.set(key, previous - cents);
        try { saveHistory(new Booking(player, reason, -cents, previous - cents, 0, 0)); }
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
        try { saveHistory(new Booking(sender, "Überweisung an " + data.getString(recipient + ".name", recipient.toString()), -cents, previousFrom - cents, 0, 0),
                new Booking(recipient, "Überweisung von " + data.getString(sender + ".name", sender.toString()), cents, nextTo, 0, 0)); }
        catch (IOException error) { data.set(from, previousFrom); data.set(to, previousTo); throw error; }
    }

    long casinoBalance(Player player) throws IOException { balance(player); return data.getLong(player.getUniqueId() + ".casino-balance"); }
    void move(UUID player, long amount, boolean toCasino) throws IOException {
        if (amount <= 0) throw new IllegalArgumentException("Betrag muss positiv sein.");
        String main = player + ".balance", casino = player + ".casino-balance";
        if (!data.contains(main)) throw new IllegalArgumentException("Konto nicht gefunden.");
        long beforeMain = data.getLong(main), beforeCasino = data.getLong(casino);
        long source = toCasino ? beforeMain : beforeCasino;
        if (source < amount) throw new IllegalArgumentException("Nicht genügend Guthaben auf dem Ausgangskonto.");
        long nextMain = toCasino ? beforeMain - amount : Math.addExact(beforeMain, amount);
        long nextCasino = toCasino ? Math.addExact(beforeCasino, amount) : beforeCasino - amount;
        data.set(main, nextMain); data.set(casino, nextCasino);
        try { saveHistory(new Booking(player, toCasino ? "Casino eingezahlt" : "Casino ausgezahlt", nextMain - beforeMain, nextMain, 0, 0),
                new Booking(player, toCasino ? "Vom Hauptkonto eingezahlt" : "Auf Hauptkonto ausgezahlt", nextCasino - beforeCasino, nextCasino, 0, 0, "Casino")); }
        catch (IOException error) { data.set(main, beforeMain); data.set(casino, beforeCasino); throw error; }
    }
    void changeCasino(UUID player, long delta, String reason) throws IOException {
        if (!data.contains(player + ".balance")) throw new IllegalArgumentException("Konto nicht gefunden.");
        String key = player + ".casino-balance";
        long before = data.getLong(key), next = Math.addExact(before, delta);
        if (next < 0) throw new IllegalArgumentException("Nicht genügend Casino-Guthaben. Bitte am Wechselautomaten einzahlen.");
        if (delta == 0) throw new IllegalArgumentException("Bitte einen Betrag auswählen.");
        data.set(key, next);
        try { saveHistory(new Booking(player, reason, delta, next, 0, 0, "Casino")); }
        catch (IOException error) { data.set(key, before); throw error; }
    }
    record Booking(UUID player, String reason, long delta, long balance, long bet, long payout, String wallet) {
        Booking(UUID player, String reason, long delta, long balance, long bet, long payout) { this(player, reason, delta, balance, bet, payout, "Hauptkonto"); }
    }
    record History(long time, String reason, long delta, long balance, long bet, long payout, String wallet) {}
    List<History> history(UUID player) {
        return data.getMapList(player + ".history").stream().map(m -> new History(
                ((Number)m.get("time")).longValue(), (String)m.get("reason"), ((Number)m.get("delta")).longValue(),
                ((Number)m.get("balance")).longValue(), ((Number)m.get("bet")).longValue(), ((Number)m.get("payout")).longValue(),
                m.containsKey("wallet") ? (String)m.get("wallet") : "Vor Kontentrennung")).toList();
    }
    private void saveHistory(Booking... bookings) throws IOException {
        Map<String, Object> previous = new HashMap<>();
        for (Booking b : bookings) {
            String key = b.player() + ".history";
            if (!previous.containsKey(key)) previous.put(key, data.get(key));
            List<Map<?,?>> entries = new ArrayList<>(data.getMapList(key));
            entries.add(0, Map.of("time", System.currentTimeMillis(), "reason", b.reason(), "delta", b.delta(),
                    "balance", b.balance(), "bet", b.bet(), "payout", b.payout(), "wallet", b.wallet()));
            data.set(key, new ArrayList<>(entries.subList(0, Math.min(200, entries.size()))));
        }
        try { save(); }
        catch (IOException error) { previous.forEach(data::set); throw error; }
    }
    private void save() throws IOException {
        Path temporary = file.resolveSibling("accounts.yml.tmp");
        Files.writeString(temporary, data.saveToString());
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }
}

