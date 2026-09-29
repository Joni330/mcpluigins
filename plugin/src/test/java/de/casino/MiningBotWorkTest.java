package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MiningBotWorkTest {
    @Test void instantRecallStopsAtBaseAndPreservesEnergyAndOperator() {
        UUID operator = UUID.randomUUID();
        var work = MiningBotWork.idle().start(operator).energy(37);
        for (int i = 0; i < 100; i++) work = work.moved();
        work = work.recall(true).recallNow();
        assertEquals(0, work.distance());
        assertEquals(MiningBotWork.Phase.UNLOADING, work.phase());
        assertFalse(work.resume()); assertEquals(37, work.energy()); assertEquals(operator, work.operator());
        assertEquals(MiningBotWork.Phase.IDLE, work.emptied().phase());
    }
    @Test void instantRecallCannotDiscardAnUnfinishedMiningJournal() {
        var work = MiningBotWork.idle().start(UUID.randomUUID()).moved()
                .pending(new MiningBotWork.Pending(0, 64, 1, "minecraft:stone"));
        assertThrows(IllegalStateException.class, work::recallNow);
    }
    @Test void entireFaceJournalSurvivesRestartAndClearsTogether(@TempDir Path dir) throws Exception {
        var neighbours = new java.util.ArrayList<MiningBotWork.Pending>();
        for (int y = 64; y <= 66; y++) for (int x = -1; x <= 1; x++) {
            if (x != 0 || y != 65) neighbours.add(new MiningBotWork.Pending(x, y, 1, "minecraft:stone"));
        }
        var pending = new MiningBotWork.Pending(0, 65, 1, "minecraft:iron_ore", neighbours);
        neighbours.clear(); // A later mutable planning buffer cannot change the committed journal.
        assertEquals(9, pending.blocks().size());
        assertEquals("minecraft:iron_ore", pending.blocks().getFirst().blockData());
        var work = MiningBotWork.idle().start(UUID.randomUUID()).energy(63).pending(pending);
        var store = new MiningBotStore(dir);
        var state = new MiningBotStore.Saved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, 0, false, new byte[]{9}, work);
        store.save(state);
        var loaded = new MiningBotStore(dir).load().getFirst();
        assertEquals(work, loaded.work());
        assertEquals(9, loaded.work().pending().blocks().size());
        store.save(new MiningBotStore.Saved(state.id(), state.owner(), state.world(), 0, 64, 0, 0, false, loaded.items(), loaded.work().pending(null)));
        loaded = new MiningBotStore(dir).load().getFirst();
        assertNull(loaded.work().pending()); assertEquals(63, loaded.work().energy());
        assertArrayEquals(new byte[]{9}, loaded.items());
    }
    @Test void fullCargoReturnsAndResumesWithoutConsumingReturnFuel() {
        UUID player = UUID.randomUUID();
        var work = MiningBotWork.idle().start(player).energy(12).moved().moved().recall(true);
        work = work.homeStep();
        assertEquals(MiningBotWork.Phase.RETURNING, work.phase());
        assertEquals(1, work.distance());
        work = work.homeStep();
        assertEquals(MiningBotWork.Phase.UNLOADING, work.phase());
        assertEquals(0, work.distance());
        work = work.emptied();
        assertEquals(MiningBotWork.Phase.MINING, work.phase());
        assertEquals(12, work.energy()); assertEquals(player, work.operator());
    }
    @Test void manualRecallOverridesAutomaticResumeEvenWithoutFuel() {
        var work = MiningBotWork.idle().start(UUID.randomUUID()).moved().recall(true).recall(false).homeStep().emptied();
        assertEquals(MiningBotWork.Phase.IDLE, work.phase());
        assertEquals(0, work.distance()); assertEquals(0, work.energy()); assertFalse(work.resume());
    }
    @Test void recallAtBaseNeverGoesBehindBase() {
        var work = MiningBotWork.idle().recall(false).homeStep();
        assertEquals(0, work.distance()); assertEquals(MiningBotWork.Phase.UNLOADING, work.phase());
    }
    @Test void directionsMatchMinecraftYawIncludingNegativeYaw() {
        float[] yaw = {0, 90, 180, 270, -90, -180, 360};
        int[][] expected = {{0,1},{-1,0},{0,-1},{1,0},{1,0},{0,-1},{0,1}};
        for (int i = 0; i < yaw.length; i++) {
            assertEquals(expected[i][0], MiningBotWork.dx(yaw[i]));
            assertEquals(expected[i][1], MiningBotWork.dz(yaw[i]));
        }
    }
    @Test void miningJournalAndNavigationSurviveRestart(@TempDir Path dir) throws Exception {
        var store = new MiningBotStore(dir); UUID id = UUID.randomUUID();
        var work = MiningBotWork.idle().start(UUID.randomUUID()).moved().energy(63)
                .pending(new MiningBotWork.Pending(-1, 64, 8, "minecraft:stone"));
        var state = new MiningBotStore.Saved(id, UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0, 0, false, new byte[]{3}, work);
        store.save(state);
        assertEquals(work, new MiningBotStore(dir).load().getFirst().work());
        store.save(new MiningBotStore.Saved(id, state.owner(), state.world(), 0, 64, 0, 0, false, state.items(), work.pending(null)));
        assertNull(new MiningBotStore(dir).load().getFirst().work().pending());
        assertArrayEquals(new byte[]{3}, store.load().getFirst().items());
    }
    @Test void legacyBotLoadsAtIdleBase(@TempDir Path dir) throws Exception {
        var store = new MiningBotStore(dir); UUID id = UUID.randomUUID();
        Files.writeString(dir.resolve("miningbots").resolve(id + ".yml"), "version: 1\nowner: " + UUID.randomUUID()
                + "\nworld: " + UUID.randomUUID() + "\nx: 5\ny: 64\nz: 3\nitems: AQ==\n");
        assertEquals(MiningBotWork.idle(), store.load().getFirst().work());
        assertArrayEquals(new byte[]{1}, store.load().getFirst().items());
    }
}
