package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import de.casino.MiningBotVeins.Pos;

class MiningBotVeinsTest {
    final Pos centre = new Pos(0, 65, 1);
    @Test void connectsNormalAndDeepslateButNotDiagonalOrDifferentOre() {
        var seed = new Pos(0, 67, 1);
        var map = Map.of(seed, "IRON_ORE", new Pos(1,67,1), "DEEPSLATE_IRON_ORE",
                new Pos(2,67,1), "GOLD_ORE", new Pos(-1,68,1), "IRON_ORE");
        assertEquals(List.of(seed, new Pos(1,67,1)), MiningBotVeins.find(List.of(seed), centre, 0,1, p -> map.getOrDefault(p,"STONE")));
    }
    @Test void hugeVeinIsCappedEvenWhenSeveralProbesTouchIt() {
        var found = MiningBotVeins.find(List.of(new Pos(0,67,1),new Pos(-1,67,1),new Pos(1,67,1)), centre,0,1,p -> "COAL_ORE");
        assertEquals(64, found.size()); assertEquals(64, new HashSet<>(found).size());
    }
    @Test void searchCannotWalkBeyondEightBlocksFromTunnel() {
        var found = MiningBotVeins.find(List.of(new Pos(0,67,1)), centre,0,1,
                p -> p.x()==0 && p.z()==1 && p.y()>=67 ? "DIAMOND_ORE" : "STONE");
        assertEquals(8, found.size()); assertTrue(found.contains(new Pos(0,74,1))); assertFalse(found.contains(new Pos(0,75,1)));
        assertTrue(MiningBotVeins.inRange(new Pos(8,65,1), centre,1,0));
        assertFalse(MiningBotVeins.inRange(new Pos(9,65,1), centre,1,0));
    }
    @Test void unrelatedVeinsAndAncientDebrisAreRecognized() {
        assertEquals("LAPIS_ORE", MiningBotVeins.ore("DEEPSLATE_LAPIS_ORE"));
        assertEquals("ANCIENT_DEBRIS", MiningBotVeins.ore("ANCIENT_DEBRIS"));
        assertEquals("", MiningBotVeins.ore("GOLD_BLOCK"));
        var a = new Pos(-1,67,1); var b = new Pos(1,63,1);
        var found = MiningBotVeins.find(List.of(a,b),centre,0,1,p -> p.equals(a)?"NETHER_GOLD_ORE":p.equals(b)?"ANCIENT_DEBRIS":"STONE");
        assertEquals(List.of(a,b),found);
    }
    @Test void interruptedVeinAndReplacementFloorSurviveRestart(@TempDir Path dir) throws Exception {
        var veins = new MiningBotVeins(true, 30, List.of(new Pos(1,63,30),new Pos(1,62,30)));
        var work = MiningBotWork.idle().start(UUID.randomUUID()).energy(5)
                .pending(new MiningBotWork.Pending(0,63,30,"minecraft:iron_ore",List.of(),"minecraft:cobblestone"));
        var store = new MiningBotStore(dir);
        store.save(new MiningBotStore.Saved(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),0,64,0,0,false,new byte[]{1},work,MiningBotLighting.off(),veins));
        var loaded = new MiningBotStore(dir).load().getFirst();
        assertEquals(veins,loaded.veins()); assertEquals(work,loaded.work());
        assertEquals("minecraft:cobblestone",loaded.work().pending().blocks().getFirst().replacement());
        assertEquals(List.of(new Pos(1,62,30)),loaded.veins().done().remaining());
        assertEquals(veins.remaining(),veins.toggle().toggle().remaining());
    }
}
