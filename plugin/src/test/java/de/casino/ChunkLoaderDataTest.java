package de.casino;

import de.casino.ChunkLoaderData.Loader;
import de.casino.ChunkLoaderData.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkLoaderDataTest {
    @TempDir Path folder;
    private final UUID world = UUID.randomUUID(), owner = UUID.randomUUID();
    private Loader loader(int x, int z, boolean enabled) {
        return new Loader(UUID.randomUUID(), owner, new Position(world, x, 70, z), enabled);
    }
    @Test void persistsOwnershipCoordinatesAndSwitchesAcrossRestarts() throws Exception {
        var data = new ChunkLoaderData(folder); var on = loader(-17, 32, true); var off = loader(400, -1, false);
        data.put(on); data.put(off);
        var restored = new ChunkLoaderData(folder);
        assertEquals(on, restored.get(on.position())); assertEquals(off, restored.get(off.position()));
        restored.put(on.enabled(false)); restored.put(off.enabled(true));
        var restarted = new ChunkLoaderData(folder);
        assertEquals(Set.of(off.position().chunk()), ChunkLoaderData.requested(restarted.all(), Set.of()));
        restarted.remove(off.position());
        assertEquals(List.of(on.enabled(false)), new ArrayList<>(new ChunkLoaderData(folder).all()));
    }
    @Test void oneChunkAtEveryPositiveAndNegativeEdgeWithoutOwnerOnlineCheck() {
        for (int x : new int[]{-33,-32,-17,-16,-1,0,15,16,31,32}) {
            var loader = loader(x, -x, true);
            assertEquals(Set.of(new PluginChunks.Key(world, Math.floorDiv(x,16), Math.floorDiv(-x,16))),
                    ChunkLoaderData.requested(List.of(loader), Set.of()));
        }
    }
    @Test void overlapStaysLoadedUntilLastLoaderStopsAndWorldsStaySeparate() {
        var first = loader(0, 0, true); var second = loader(15, 15, true);
        var otherWorld = new Loader(UUID.randomUUID(), owner, new Position(UUID.randomUUID(), 0, 70, 0), true);
        assertEquals(Set.of(first.position().chunk()), ChunkLoaderData.requested(List.of(first, second), Set.of()));
        assertEquals(Set.of(first.position().chunk()), ChunkLoaderData.requested(List.of(first.enabled(false), second), Set.of()));
        assertTrue(ChunkLoaderData.requested(List.of(first.enabled(false), second.enabled(false)), Set.of()).isEmpty());
        assertEquals(2, ChunkLoaderData.requested(List.of(first, second, otherWorld), Set.of()).size());
    }
    @Test void pendingPlacementDoesNotLoadChunkAndCancelledPlacementIsRemoved() throws Exception {
        var data = new ChunkLoaderData(folder); var loader = loader(40, 50, true); data.put(loader);
        assertTrue(ChunkLoaderData.requested(data.all(), Set.of(loader.position())).isEmpty());
        data.remove(loader.position()); assertTrue(new ChunkLoaderData(folder).all().isEmpty());
    }
    @Test void failedSaveLeavesFileAndMemoryUnchangedForToggleAddAndRemove() throws Exception {
        var data = new ChunkLoaderData(folder); var loader = loader(0, 0, true); data.put(loader);
        byte[] before = Files.readAllBytes(folder.resolve("chunk-loaders.yml"));
        var failing = new ChunkLoaderData(folder, (from, to) -> { throw new AccessDeniedException(to.toString()); });
        assertThrows(IOException.class, () -> failing.put(loader.enabled(false)));
        assertThrows(IOException.class, () -> failing.put(loader(16, 16, true)));
        assertThrows(IOException.class, () -> failing.remove(loader.position()));
        assertEquals(List.of(loader), new ArrayList<>(failing.all()));
        assertArrayEquals(before, Files.readAllBytes(folder.resolve("chunk-loaders.yml")));
        try (var files = Files.list(folder)) { assertEquals(1, files.count()); }
    }
    @Test void orphanCleanupPersistsWithoutRemovingUnaffectedLoaders() throws Exception {
        var data = new ChunkLoaderData(folder); var stale = loader(0, 0, true); var valid = loader(80, 80, true);
        data.put(stale); data.put(valid); data.removeAll(Set.of(stale.position()));
        assertEquals(List.of(valid), new ArrayList<>(new ChunkLoaderData(folder).all()));
    }
    @Test void corruptOrUnknownDataFailsWithoutOverwritingIt() throws Exception {
        for (String broken : List.of("version: 9\nloaders: []\n", "version: 1\nloaders: [invalid]\n", "version: 1\n")) {
            Files.writeString(folder.resolve("chunk-loaders.yml"), broken);
            assertThrows(Exception.class, () -> new ChunkLoaderData(folder));
            assertEquals(broken, Files.readString(folder.resolve("chunk-loaders.yml")));
        }
    }
}
