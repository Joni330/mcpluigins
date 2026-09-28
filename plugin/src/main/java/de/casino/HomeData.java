package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

final class HomeData {
    record Point(UUID world, double x, double y, double z, float yaw, float pitch) {
        Map<String, Object> values() { return Map.of("world", world.toString(), "x", x, "y", y, "z", z, "yaw", yaw, "pitch", pitch); }
        static Point read(Map<String, Object> values) {
            return new Point(UUID.fromString((String) values.get("world")), ((Number) values.get("x")).doubleValue(),
                    ((Number) values.get("y")).doubleValue(), ((Number) values.get("z")).doubleValue(),
                    ((Number) values.get("yaw")).floatValue(), ((Number) values.get("pitch")).floatValue());
        }
    }
    private final Path file;
    private YamlConfiguration data = new YamlConfiguration();
    HomeData(Path folder) throws Exception {
        Files.createDirectories(folder); file = folder.resolve("homes.yml");
        if (Files.exists(file)) data.load(file.toFile());
    }
    static String name(String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,20}") || name.equalsIgnoreCase("list"))
            throw new IllegalArgumentException("Homename: 1–20 Buchstaben, Zahlen, _ oder -. 'list' ist reserviert.");
        return name.toLowerCase(Locale.ROOT);
    }
    List<String> list(UUID player) {
        var section = data.getConfigurationSection(player.toString());
        return section == null ? List.of() : section.getKeys(false).stream().sorted().toList();
    }
    Point get(UUID player, String name) {
        var section = data.getConfigurationSection(player + "." + name(name));
        if (section == null) throw new IllegalArgumentException("Dieses Home existiert nicht. Nutze /home list.");
        return Point.read(section.getValues(false));
    }
    void set(UUID player, String name, Point point) throws IOException {
        String key = name(name);
        if (!list(player).contains(key) && list(player).size() >= 3)
            throw new IllegalArgumentException("Du hast bereits 3 Homes. Lösche zuerst eines mit /delhome <name>.");
        edit(player + "." + key, point.values());
    }
    void delete(UUID player, String name) throws IOException {
        get(player, name); edit(player + "." + name(name), null);
    }
    private void edit(String key, Object value) throws IOException {
        YamlConfiguration next = new YamlConfiguration();
        try { next.loadFromString(data.saveToString()); } catch (Exception error) { throw new IOException(error); }
        next.set(key, null);
        if (value instanceof Map<?, ?> values) next.createSection(key, values);
        Path temp = file.resolveSibling("homes.yml.tmp");
        Files.writeString(temp, next.saveToString());
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        data = next;
    }
}
