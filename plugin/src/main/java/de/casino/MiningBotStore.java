package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** One atomic file per bot. Never replace unreadable state with an empty inventory. */
final class MiningBotStore {
    record Saved(UUID id, UUID owner, UUID world, int x, int y, int z, float yaw, boolean unloading, byte[] items, MiningBotWork work, MiningBotLighting lighting) {
        Saved(UUID id, UUID owner, UUID world, int x, int y, int z, float yaw, boolean unloading, byte[] items, MiningBotWork work) {
            this(id, owner, world, x, y, z, yaw, unloading, items, work, MiningBotLighting.off());
        }
        Saved(UUID id, UUID owner, UUID world, int x, int y, int z, float yaw, boolean unloading, byte[] items) {
            this(id, owner, world, x, y, z, yaw, unloading, items, MiningBotWork.idle());
        }
    }
    private final Path folder;
    MiningBotStore(Path pluginFolder) throws IOException { folder = pluginFolder.resolve("miningbots"); Files.createDirectories(folder); }
    List<Saved> load() throws Exception {
        List<Saved> result = new ArrayList<>();
        try (var files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".yml")).toList()) {
                var yaml = new YamlConfiguration(); yaml.load(file.toFile());
                if (yaml.getInt("version") < 1 || yaml.getInt("version") > 4) throw new IOException("Unbekanntes Bot-Format: " + file);
                MiningBotWork work = new MiningBotWork(MiningBotWork.Phase.valueOf(yaml.getString("work.phase", "IDLE")),
                        yaml.getInt("work.distance"), yaml.getInt("work.energy"), yaml.getBoolean("work.resume"),
                        yaml.contains("work.operator") ? UUID.fromString(yaml.getString("work.operator")) : null,
                        yaml.contains("work.pending") ? new MiningBotWork.Pending(yaml.getInt("work.pending.x"), yaml.getInt("work.pending.y"),
                                yaml.getInt("work.pending.z"), Objects.requireNonNull(yaml.getString("work.pending.block")),
                                yaml.getMapList("work.pending.surrounding").stream().map(row -> new MiningBotWork.Pending(
                                        ((Number) row.get("x")).intValue(), ((Number) row.get("y")).intValue(), ((Number) row.get("z")).intValue(),
                                        Objects.requireNonNull((String) row.get("block")))).toList()) : null);
                UUID id = UUID.fromString(file.getFileName().toString().replace(".yml", ""));
                result.add(new Saved(id, UUID.fromString(Objects.requireNonNull(yaml.getString("owner"))),
                        UUID.fromString(Objects.requireNonNull(yaml.getString("world"))), yaml.getInt("x"), yaml.getInt("y"), yaml.getInt("z"),
                        (float) yaml.getDouble("yaw"), yaml.getBoolean("unloading"), Base64.getDecoder().decode(Objects.requireNonNull(yaml.getString("items"))), work,
                        new MiningBotLighting(yaml.getBoolean("lighting.enabled"), yaml.getInt("lighting.last-distance"))));
            }
        }
        return result;
    }
    void save(Saved state) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("version", 4); yaml.set("owner", state.owner().toString()); yaml.set("world", state.world().toString());
        yaml.set("lighting.enabled", state.lighting().enabled()); yaml.set("lighting.last-distance", state.lighting().lastDistance());
        yaml.set("x", state.x()); yaml.set("y", state.y()); yaml.set("z", state.z()); yaml.set("yaw", state.yaw());
        yaml.set("unloading", state.unloading());
        MiningBotWork work = state.work();
        yaml.set("work.phase", work.phase().name()); yaml.set("work.distance", work.distance()); yaml.set("work.energy", work.energy());
        yaml.set("work.resume", work.resume()); yaml.set("work.operator", work.operator() == null ? null : work.operator().toString());
        if (work.pending() != null) {
            yaml.set("work.pending.x", work.pending().x()); yaml.set("work.pending.y", work.pending().y()); yaml.set("work.pending.z", work.pending().z());
            yaml.set("work.pending.block", work.pending().blockData());
            yaml.set("work.pending.surrounding", work.pending().surrounding().stream().map(block ->
                    Map.of("x", block.x(), "y", block.y(), "z", block.z(), "block", block.blockData())).toList());
        }
        yaml.set("items", Base64.getEncoder().encodeToString(state.items()));
        Path temp = folder.resolve(state.id() + ".tmp"), target = folder.resolve(state.id() + ".yml");
        Files.writeString(temp, yaml.saveToString());
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    void remove(UUID id) throws IOException { Files.delete(folder.resolve(id + ".yml")); }
}
