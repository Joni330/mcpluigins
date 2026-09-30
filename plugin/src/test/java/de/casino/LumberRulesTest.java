package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import de.casino.MiningBotVeins.Pos;
import static org.junit.jupiter.api.Assertions.*;

class LumberRulesTest {
    @Test void crownsCrossEveryChunkEdgeButStayWithinOneNeighbourRing(){
        for(int cx:new int[]{-3,0,2})for(int cz:new int[]{-2,0,4}){
            int x=cx<<4,z=cz<<4;
            for(int dx:new int[]{-16,-1,0,15,16,31})for(int dz:new int[]{-16,-1,0,15,16,31})
                assertTrue(LumberRules.inTreeArea(new Pos(x+dx,80,z+dz),cx,cz));
            for(int beyond:new int[]{-17,32}){
                assertFalse(LumberRules.inTreeArea(new Pos(x+beyond,80,z),cx,cz));
                assertFalse(LumberRules.inTreeArea(new Pos(x,80,z+beyond),cx,cz));
            }
            assertFalse(LumberRules.inChunk(new Pos(x-1,80,z+16),cx,cz));
        }
    }
    @Test void overhangHarvestRequiresAnExactTrackedTreePart(){
        Pos branch=new Pos(16,70,15),leaf=new Pos(17,71,16),root=new Pos(-1,64,5),unrelated=new Pos(18,71,16);
        Map<Pos,String> tree=Map.of(branch,"OAK_LOG",leaf,"OAK_LEAVES",root,"MANGROVE_ROOTS");
        tree.forEach((p,type)->assertTrue(LumberRules.trackedTreePart(p,type,0,0,tree)));
        assertFalse(LumberRules.trackedTreePart(unrelated,"OAK_LEAVES",0,0,tree));
        assertFalse(LumberRules.trackedTreePart(leaf,"BIRCH_LEAVES",0,0,tree));
        assertFalse(LumberRules.trackedTreePart(branch,"CHEST",0,0,tree));
        assertFalse(LumberRules.trackedTreePart(branch,"CHEST",0,0,Map.of(branch,"CHEST")));
        Pos far=new Pos(32,70,0);
        assertFalse(LumberRules.trackedTreePart(far,"OAK_LOG",0,0,Map.of(far,"OAK_LOG")));
    }
    @Test void activeTreeChunksIncludeEveryAllowedOverhangWithoutAnExtraRing(){
        UUID world=UUID.randomUUID();
        for(int cx:new int[]{-3,0,2})for(int cz:new int[]{-2,0,4}){
            var chunks=LumberRules.treeChunks(world,cx,cz);assertEquals(9,chunks.size());
            for(int dx=-16;dx<32;dx++)for(int dz=-16;dz<32;dz++){
                Pos p=new Pos((cx<<4)+dx,70,(cz<<4)+dz);
                assertTrue(LumberRules.inTreeArea(p,cx,cz));
                assertTrue(chunks.contains(new PluginChunks.Key(world,p.x()>>4,p.z()>>4)));
            }
        }
    }
    @Test void groundCollectionAcceptsTreeLootRatherThanUnrelatedEquipment(){
        for(String type:List.of("OAK_SAPLING","MANGROVE_PROPAGULE","BIRCH_LOG","OAK_LEAVES","STICK","APPLE"))assertTrue(LumberRules.treeDrop(type));
        for(String type:List.of("DIAMOND_AXE","DIAMOND","CHEST","OAK_PLANKS"))assertFalse(LumberRules.treeDrop(type));
    }
    @Test void plotsAndTwoByTwoFootprintsStayInsideNegativeChunks(){
        List<Pos> plots=LumberRules.plots(-2,-3,64,"DARK_OAK_SAPLING");assertEquals(9,plots.size());
        for(Pos plot:plots){assertTrue(LumberRules.inChunk(plot,-2,-3));
            for(Pos p:LumberRules.footprint(plot,"DARK_OAK_SAPLING"))assertTrue(LumberRules.inChunk(p,-2,-3));}
        assertFalse(LumberRules.inChunk(new Pos(-16,64,-33),-2,-3));
    }
    @Test void densePlotsKeepTrunkSpacingAndThreeBlocksOfCanopyMargin(){
        for(int chunk:new int[]{-3,0,2})for(String type:LumberRules.SAPLINGS){
            List<Pos> plots=LumberRules.plots(chunk,chunk,70,type);assertEquals(plots.size(),new HashSet<>(plots).size());
            Map<Pos,String> occupied=new LinkedHashMap<>();
            for(Pos root:plots){
                assertEquals(70,root.y());assertTrue(LumberRules.separated(root,type,occupied));
                for(Pos cell:LumberRules.footprint(root,type)){
                    assertTrue(LumberRules.inChunk(new Pos(cell.x()-3,cell.y(),cell.z()-3),chunk,chunk));
                    assertTrue(LumberRules.inChunk(new Pos(cell.x()+3,cell.y(),cell.z()+3),chunk,chunk));
                }
                occupied.put(root,type);
            }
        }
        assertEquals(16,LumberRules.plots(0,0,64,"OAK_SAPLING").size());
        assertEquals(16,LumberRules.plots(0,0,64,"BIRCH_SAPLING").size());
        assertEquals(9,LumberRules.plots(0,0,64,"CHERRY_SAPLING").size());
    }
    @Test void existingPlantsKeepTheirSpaceUntilTheyAreHarvested(){
        Map<Pos,String> old=new LinkedHashMap<>();old.put(new Pos(4,64,4),"OAK_SAPLING");
        Pos nearby=new Pos(3,68,3);
        assertFalse(LumberRules.separated(nearby,"OAK_SAPLING",old));
        assertTrue(LumberRules.separated(new Pos(9,64,9),"OAK_SAPLING",old));
        old.clear();assertTrue(LumberRules.separated(nearby,"OAK_SAPLING",old));
    }
    @Test void mixedSpeciesRespectTheFarEdgeOfTwoByTwoTrunks(){
        Map<Pos,String> planted=Map.of(new Pos(3,64,3),"DARK_OAK_SAPLING");
        assertFalse(LumberRules.separated(new Pos(6,64,3),"OAK_SAPLING",planted));
        assertTrue(LumberRules.separated(new Pos(7,64,3),"OAK_SAPLING",planted));
    }
    @Test void speciesRequiringGroupsUseFourSaplings(){
        Pos root=new Pos(4,65,4);
        assertEquals(4,LumberRules.footprint(root,"DARK_OAK_SAPLING").size());
        assertEquals(4,LumberRules.footprint(root,"PALE_OAK_SAPLING").size());
        assertEquals(1,LumberRules.footprint(root,"OAK_SAPLING").size());
        assertEquals(1,LumberRules.footprint(root,"MANGROVE_PROPAGULE").size());
    }
    @Test void treeSelectionDoesNotTreatBuildingMaterialsAsHarvest(){
        assertTrue(LumberRules.treePart("OAK_LOG"));assertTrue(LumberRules.treePart("CHERRY_LEAVES"));
        assertTrue(LumberRules.treePart("MANGROVE_ROOTS"));
        for(String type:List.of("OAK_PLANKS","CHEST","OAK_FENCE","DIRT"))assertFalse(LumberRules.treePart(type));
        assertFalse(LumberRules.SAPLINGS.contains("CRIMSON_FUNGUS"));
    }
    @Test void plantingNeedsSoilRatherThanMachinesOrStone(){
        assertTrue(LumberRules.soil("GRASS_BLOCK"));assertTrue(LumberRules.soil("DIRT"));
        assertFalse(LumberRules.soil("STONE"));assertFalse(LumberRules.soil("BARREL"));
    }
}
