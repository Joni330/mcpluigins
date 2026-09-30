package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import de.casino.MiningBotVeins.Pos;
import static org.junit.jupiter.api.Assertions.*;

class LumberScanTest {
    private static final LumberScan.Cell AIR = new LumberScan.Cell("AIR", false);
    private final Map<Pos, LumberScan.Cell> world = new HashMap<>();

    private void put(int x, int y, int z, String type) { world.put(new Pos(x,y,z), new LumberScan.Cell(type,false)); }
    private void tree(int x, int y, int z, String species) {
        put(x,y-1,z,"GRASS_BLOCK");
        for (int h=0;h<4;h++) put(x,y+h,z,species+"_LOG");
        for (int dx=-1;dx<=1;dx++) for (int dz=-1;dz<=1;dz++)
            if(dx!=0||dz!=0) put(x+dx,y+3,z+dz,species+"_LEAVES");
    }
    private LumberScan scan(int cx,int cz,int min,int max) {
        LumberScan scan=new LumberScan(cx,cz,min,max,p->world.getOrDefault(p,AIR));
        while(!scan.step(256)) {}
        return scan;
    }

    @Test void recognizesSeveralGrownSpeciesOnDifferentGroundHeights() {
        tree(3,1,3,"OAK"); tree(10,7,10,"BIRCH"); tree(3,14,10,"SPRUCE");
        LumberScan scan=scan(0,0,-1,20);
        assertEquals(3,scan.trees()); assertEquals(36,scan.harvest().size());
        assertEquals("BIRCH_LOG",scan.harvest().get(new Pos(10,7,10)));
        assertFalse(scan.harvest().containsKey(new Pos(3,0,3))); // The ground stays intact.
    }

    @Test void scanIsBudgetedReadOnlyAndNeverReadsOutsideItsChunkOrWorldHeight() {
        tree(-16,1,-1,"OAK"); AtomicInteger reads=new AtomicInteger();
        Map<Pos,LumberScan.Cell> before=Map.copyOf(world);
        LumberScan scan=new LumberScan(-1,-1,-2,10,p->{
            assertTrue(LumberRules.inChunk(p,-1,-1)); assertTrue(p.y()>=-2&&p.y()<10);
            reads.incrementAndGet(); return world.getOrDefault(p,AIR);
        });
        assertThrows(IllegalStateException.class,scan::harvest);
        assertFalse(scan.step(17));assertEquals(17,reads.get());
        while(!scan.step(93)) {}
        assertEquals(12*256,reads.get()); assertEquals(100,scan.percent()); assertEquals(before,world);
        assertTrue(scan.step(93)); assertEquals(12*256,reads.get());
    }

    @Test void doesNotClaimConstructionLogsOrPlacedLeaves() {
        tree(3,1,3,"OAK");
        world.replaceAll((p,c)->c.type().endsWith("_LEAVES")?new LumberScan.Cell(c.type(),true):c);
        put(8,0,8,"DIRT"); for(int y=1;y<6;y++)put(8,y,8,"OAK_LOG"); // Bare timber column.
        tree(12,1,12,"BIRCH");
        world.replaceAll((p,c)->c.type().equals("BIRCH_LOG")?new LumberScan.Cell("STRIPPED_BIRCH_LOG",false):c);
        assertTrue(scan(0,0,0,8).harvest().isEmpty());
    }

    @Test void needsRootedTrunksWithMatchingNaturalLeaves() {
        tree(3,2,3,"OAK"); world.remove(new Pos(3,1,3)); // Floating crown/wood structure.
        tree(10,1,10,"BIRCH");
        world.replaceAll((p,c)->c.type().equals("BIRCH_LEAVES")?new LumberScan.Cell("OAK_LEAVES",false):c);
        assertTrue(scan(0,0,0,8).harvest().isEmpty());
    }

    @Test void crownsAndBranchesAtChunkEdgeStayWithinAssignedChunk() {
        tree(15,1,8,"OAK"); tree(17,1,8,"BIRCH");
        LumberScan scan=scan(0,0,0,8);
        assertEquals(1,scan.trees()); assertEquals("OAK_LOG",scan.harvest().get(new Pos(15,1,8)));
        assertTrue(scan.harvest().keySet().stream().allMatch(p->LumberRules.inChunk(p,0,0)));
        assertFalse(scan.harvest().containsValue("BIRCH_LOG"));
    }

    @Test void followsDiagonalAcaciaBranchesAndHandlesMangroveRoots() {
        put(2,0,2,"DIRT"); put(2,1,2,"ACACIA_LOG"); put(3,2,2,"ACACIA_LOG"); put(4,3,2,"ACACIA_LOG");
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)put(4+dx,4,2+dz,"ACACIA_LEAVES");
        tree(10,3,10,"MANGROVE");put(10,0,10,"MUD");put(10,1,10,"MUDDY_MANGROVE_ROOTS");put(10,2,10,"MANGROVE_ROOTS");
        LumberScan scan=scan(0,0,0,10);
        assertEquals(2,scan.trees());assertEquals("ACACIA_LOG",scan.harvest().get(new Pos(3,2,2)));
        assertEquals("MUDDY_MANGROVE_ROOTS",scan.harvest().get(new Pos(10,1,10)));
    }
}
