package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MiningBotTest {
    @Test void floorBuilderPersistsWithoutChangingInventoryAndDefaultsOff(@TempDir Path dir) throws Exception {
        var store = new MiningBotStore(dir);
        var base = state(UUID.randomUUID(), new byte[]{1, 2});
        assertFalse(base.floorBuilder());
        store.save(new MiningBotStore.Saved(base.id(), base.owner(), base.world(), base.x(), base.y(), base.z(), base.yaw(),
                base.unloading(), base.items(), MiningBotWork.idle(), MiningBotLighting.off(), MiningBotVeins.off(), true));
        var loaded = new MiningBotStore(dir).load().getFirst();
        assertTrue(loaded.floorBuilder()); assertArrayEquals(base.items(), loaded.items());
        store.save(base);
        assertFalse(new MiningBotStore(dir).load().getFirst().floorBuilder());
    }
    @Test void matchingStacksAreFilledBeforeEmptySlotsAndNoItemsAreLost() {
        assertArrayEquals(new int[]{60, 4, 0}, BotInventory.plan(64, new int[]{64, 4, 0}, new boolean[]{false, true, true}));
        assertArrayEquals(new int[]{2, 3}, BotInventory.plan(64, new int[]{2, 3}, new boolean[]{true, false}));
        assertArrayEquals(new int[]{0, 0}, BotInventory.plan(64, new int[]{0, 0}, new boolean[]{true, false}));
    }
    @Test void handlesNonStackableToolsAndNeverChangesInputPlan() {
        int[] room = {1, 0, 1};
        assertArrayEquals(new int[]{1, 0, 1}, BotInventory.plan(3, room, new boolean[3]));
        assertArrayEquals(new int[]{1, 0, 1}, room);
        assertThrows(IllegalArgumentException.class, () -> BotInventory.plan(-1, room, new boolean[3]));
    }
    private MiningBotStore.Saved state(UUID id, byte[] payload) {
        return new MiningBotStore.Saved(id, UUID.randomUUID(), UUID.randomUUID(), -33, 64, 47, 90, true, payload);
    }
    @Test void preservesOwnerBaseInventoriesAndUnloadStateAcrossRestart(@TempDir Path dir) throws Exception {
        MiningBotStore store = new MiningBotStore(dir);
        var original = state(UUID.randomUUID(), new byte[]{1, 2, 3, 4}); store.save(original);
        var loaded = new MiningBotStore(dir).load().getFirst();
        assertEquals(original.owner(), loaded.owner()); assertEquals(original.world(), loaded.world());
        assertEquals(-33, loaded.x()); assertEquals(64, loaded.y()); assertEquals(47, loaded.z());
        assertEquals(90, loaded.yaw()); assertTrue(loaded.unloading()); assertArrayEquals(original.items(), loaded.items());
    }
    @Test void failedSaveRetainsPreviousFile(@TempDir Path dir) throws Exception {
        MiningBotStore store = new MiningBotStore(dir); UUID id = UUID.randomUUID(); store.save(state(id, new byte[]{1}));
        Files.createDirectory(dir.resolve("miningbots").resolve(id + ".tmp"));
        assertThrows(java.io.IOException.class, () -> store.save(state(id, new byte[]{2})));
        assertArrayEquals(new byte[]{1}, store.load().getFirst().items());
    }
    @Test void invalidDataFailsClosed(@TempDir Path dir) throws Exception {
        MiningBotStore store = new MiningBotStore(dir);
        Files.writeString(dir.resolve("miningbots").resolve(UUID.randomUUID() + ".yml"), "version: 999\n");
        assertThrows(java.io.IOException.class, store::load);
    }
    @Test void removalDoesNotDeleteOtherBots(@TempDir Path dir) throws Exception {
        MiningBotStore store = new MiningBotStore(dir); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        store.save(state(a, new byte[0])); store.save(state(b, new byte[0])); store.remove(a);
        assertEquals(List.of(b), store.load().stream().map(MiningBotStore.Saved::id).toList());
    }
}
