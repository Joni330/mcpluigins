package de.casino;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Persistent index: loaders must be found even when their chunks are not loaded yet. */
final class ChunkLoaderData {
    record Position(UUID world, int x, int y, int z) {
        PluginChunks.Key chunk() { return new PluginChunks.Key(world, x >> 4, z >> 4); }
    }
    record Loader(UUID id, UUID owner, Position position, boolean enabled) {
        Loader enabled(boolean value) { return new Loader(id, owner, position, value); }
    }
    interface Commit { void move(Path source, Path target) throws IOException; }
    private final Path file;
    private final Commit commit;
    private Map<Position, Loader> loaders = new LinkedHashMap<>();

    ChunkLoaderData(Path root) throws Exception { this(root, ChunkLoaderData::replace); }
    ChunkLoaderData(Path root, Commit commit) throws Exception {
        Files.createDirectories(root); file = root.resolve("chunk-loaders.yml"); this.commit = commit;
        if (!Files.exists(file)) return;
        var yaml = new YamlConfiguration(); yaml.load(file.toFile());
        if (yaml.getInt("version") != 1 || !yaml.isList("loaders")) throw new IOException("Ungültiges Chunkloader-Format: " + file);
        Set<UUID> ids = new HashSet<>();
        for (Object value : yaml.getList("loaders")) {
            if (!(value instanceof Map<?, ?> row) || !(row.get("enabled") instanceof Boolean enabled))
                throw new IOException("Ungültiger Chunkloader: " + file);
            var position = new Position(uuid(row.get("world")), integer(row.get("x")), integer(row.get("y")), integer(row.get("z")));
            var loader = new Loader(uuid(row.get("id")), uuid(row.get("owner")), position, enabled);
            if (!ids.add(loader.id()) || loaders.putIfAbsent(position, loader) != null)
                throw new IOException("Doppelter Chunkloader: " + file);
        }
    }
    Collection<Loader> all() { return List.copyOf(loaders.values()); }
    Loader get(Position position) { return loaders.get(position); }
    void put(Loader loader) throws IOException {
        var next = new LinkedHashMap<>(loaders); next.put(loader.position(), loader); save(next);
    }
    void remove(Position position) throws IOException { removeAll(Set.of(position)); }
    void removeAll(Set<Position> positions) throws IOException {
        var next = new LinkedHashMap<>(loaders); positions.forEach(next::remove);
        if (!next.equals(loaders)) save(next);
    }
    static Set<PluginChunks.Key> requested(Collection<Loader> loaders, Set<Position> pending) {
        Set<PluginChunks.Key> result = new HashSet<>();
        for (Loader loader : loaders) if (loader.enabled() && !pending.contains(loader.position())) result.add(loader.position().chunk());
        return result;
    }
    private void save(Map<Position, Loader> next) throws IOException {
        var yaml = new YamlConfiguration(); yaml.set("version", 1);
        yaml.set("loaders", next.values().stream().map(loader -> {
            var p = loader.position();
            return Map.of("id", loader.id().toString(), "owner", loader.owner().toString(), "world", p.world().toString(),
                    "x", p.x(), "y", p.y(), "z", p.z(), "enabled", loader.enabled());
        }).toList());
        Path temp = Files.createTempFile(file.getParent(), "chunk-loaders-", ".tmp");
        try { Files.writeString(temp, yaml.saveToString()); commit.move(temp, file); }
        finally { try { Files.deleteIfExists(temp); } catch (IOException ignored) {} }
        // Failed writes must not change active loaders or their saved on/off state.
        loaders = next;
    }
    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static UUID uuid(Object value) throws IOException {
        try { return UUID.fromString((String) value); }
        catch (RuntimeException error) { throw new IOException("Ungültige Chunkloader-UUID", error); }
    }
    private static int integer(Object value) throws IOException {
        if (!(value instanceof Integer number)) throw new IOException("Ungültige Chunkloader-Koordinate");
        return number;
    }
}
