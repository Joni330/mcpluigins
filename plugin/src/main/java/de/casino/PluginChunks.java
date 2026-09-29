package de.casino;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.*;

/** Shared ticket ownership so a closed storage GUI cannot unload an active bot's chunk. */
final class PluginChunks {
    record Key(UUID world, int x, int z) {}
    private static final Map<Object, Set<Key>> owners = new IdentityHashMap<>();
    static Set<Key> area(UUID world, int x, int z, int radius) {
        Set<Key> result = new HashSet<>();
        for (int cx = (x-radius) >> 4; cx <= (x+radius) >> 4; cx++)
            for (int cz = (z-radius) >> 4; cz <= (z+radius) >> 4; cz++) result.add(new Key(world, cx, cz));
        return result;
    }
    static void acquire(Plugin plugin, Object owner, Key key) {
        var world = Bukkit.getWorld(key.world()); if (world == null) return;
        world.addPluginChunkTicket(key.x(), key.z(), plugin);
        owners.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
    }
    static void release(Plugin plugin, Object owner, Key key) {
        Set<Key> held = owners.get(owner); if (held == null || !held.remove(key)) return;
        if (held.isEmpty()) owners.remove(owner);
        if (stillNeeded(owners.values(), key)) return;
        var world = Bukkit.getWorld(key.world()); if (world != null) world.removePluginChunkTicket(key.x(), key.z(), plugin);
    }
    static boolean stillNeeded(Collection<Set<Key>> requests, Key key) { return requests.stream().anyMatch(keys -> keys.contains(key)); }
    static void update(Plugin plugin, Object owner, Set<Key> needed) {
        Set<Key> old = new HashSet<>(owners.getOrDefault(owner, Set.of()));
        for (Key key : needed) if (!old.contains(key)) acquire(plugin, owner, key);
        for (Key key : old) if (!needed.contains(key)) release(plugin, owner, key);
    }
}
