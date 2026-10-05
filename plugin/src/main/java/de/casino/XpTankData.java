package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** New generation files avoid overwriting a Windows file that another process has open. */
final class XpTankData {
    record Position(UUID world, int x, int y, int z) {}
    record Tank(UUID id, UUID owner, Position position, long points, boolean shared, boolean active) {
        Tank {
            Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(position);
            Objects.requireNonNull(position.world());
            if (points < 0 || !active && points != 0) throw new IllegalArgumentException("Ungültiger XP-Tankbestand");
        }
        Tank points(long value) { return new Tank(id, owner, position, value, shared, active); }
        Tank shared(boolean value) { return new Tank(id, owner, position, points, value, active); }
    }
    record Transfer(Tank tank, int playerPoints, long amount) {}
    interface Commit { void move(Path source, Path target) throws IOException; }
    private final Path folder;
    private final Commit commit;
    private final Map<UUID, Tank> records = new LinkedHashMap<>();
    private final Map<Position, Tank> positions = new HashMap<>();
    XpTankData(Path root) throws Exception { this(root, XpTankData::moveNew); }
    XpTankData(Path root, Commit commit) throws Exception {
        folder = root.resolve("xp-tanks"); Files.createDirectories(folder); this.commit = commit;
        Map<UUID, Path> latest = new HashMap<>();
        try (var paths = Files.list(folder)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".yml")).toList())
                latest.merge(id(path), path, (a, b) -> revision(a) > revision(b) ? a : b);
        }
        for (var entry : latest.entrySet()) {
            var y = new YamlConfiguration(); y.load(entry.getValue().toFile());
            if (y.getInt("version") != 1 || !y.isLong("points") && !y.isInt("points")
                    || !y.isBoolean("shared") || !y.isBoolean("active")
                    || !y.isInt("x") || !y.isInt("y") || !y.isInt("z")) throw new IOException("Ungültige XP-Tankdatei: " + entry.getValue());
            var p = new Position(UUID.fromString(y.getString("world")), y.getInt("x"), y.getInt("y"), y.getInt("z"));
            var tank = new Tank(entry.getKey(), UUID.fromString(y.getString("owner")), p, y.getLong("points"), y.getBoolean("shared"), y.getBoolean("active"));
            if (tank.active() && positions.putIfAbsent(p, tank) != null) throw new IOException("Doppelter XP-Tankstandort");
            records.put(tank.id(), tank);
        }
    }
    Tank get(Position p) { return positions.get(p); }
    Collection<Tank> all() { return List.copyOf(positions.values()); }
    void create(Tank tank) throws IOException {
        if (!tank.active() || tank.points() != 0 || records.containsKey(tank.id()) || positions.containsKey(tank.position()))
            throw new IllegalArgumentException("Dieser XP-Tank existiert bereits.");
        save(tank);
    }
    void sharing(Tank expected, boolean shared) throws IOException { save(current(expected).shared(shared)); }
    void remove(Tank expected) throws IOException {
        Tank tank = current(expected);
        if (tank.points() != 0) throw new IllegalArgumentException("Bitte den XP-Tank zuerst leeren.");
        save(new Tank(tank.id(), tank.owner(), tank.position(), 0, tank.shared(), false));
    }
    Transfer transfer(Tank expected, int playerPoints, long amount, boolean deposit) throws IOException {
        Tank tank = current(expected);
        if (playerPoints < 0 || amount <= 0 || deposit && (amount > playerPoints || amount > Long.MAX_VALUE - tank.points())
                || !deposit && (amount > tank.points() || amount > Integer.MAX_VALUE - (long) playerPoints))
            throw new IllegalArgumentException("Ungültige XP-Umbuchung");
        Tank next = tank.points(deposit ? tank.points() + amount : tank.points() - amount);
        int nextPlayer = (int) (deposit ? playerPoints - amount : playerPoints + amount);
        save(next);
        return new Transfer(next, nextPlayer, amount);
    }
    private Tank current(Tank expected) {
        Tank tank = get(expected.position());
        if (tank == null || !tank.equals(expected)) throw new IllegalArgumentException("Der XP-Tank wurde inzwischen verändert. Bitte erneut klicken.");
        return tank;
    }
    private void save(Tank tank) throws IOException {
        var y = new YamlConfiguration(); y.set("version", 1); y.set("owner", tank.owner().toString());
        var p = tank.position(); y.set("world", p.world().toString()); y.set("x", p.x()); y.set("y", p.y()); y.set("z", p.z());
        y.set("points", tank.points()); y.set("shared", tank.shared()); y.set("active", tank.active());
        List<Path> previous;
        try (var paths = Files.list(folder)) {
            previous = paths.filter(path -> path.getFileName().toString().startsWith(tank.id() + "--") && path.toString().endsWith(".yml"))
                    .sorted(Comparator.comparingLong(XpTankData::revision).reversed()).toList();
        }
        long next = Math.max(System.currentTimeMillis(), previous.isEmpty() ? 1 : revision(previous.getFirst()) + 1);
        Path target = folder.resolve(tank.id() + "--" + String.format(Locale.ROOT, "%019d", next) + ".yml");
        Path temp = Files.createTempFile(folder, "tank-", ".tmp");
        try { Files.writeString(temp, y.saveToString()); commit.move(temp, target); }
        finally { try { Files.deleteIfExists(temp); } catch (IOException ignored) {} }
        records.put(tank.id(), tank);
        if (tank.active()) positions.put(p, tank); else positions.remove(p);
        // Keep one preceding generation for backup; never restore it automatically over newer data.
        for (int i = 1; i < previous.size(); i++) try { Files.deleteIfExists(previous.get(i)); } catch (IOException ignored) {}
    }
    private static UUID id(Path path) { return UUID.fromString(path.getFileName().toString().substring(0, 36)); }
    private static long revision(Path path) { String s = path.getFileName().toString(); return Long.parseLong(s.substring(38, s.length() - 4)); }
    private static void moveNew(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException error) { Files.move(source, target); }
    }
}
