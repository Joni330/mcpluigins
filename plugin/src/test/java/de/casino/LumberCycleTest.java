package de.casino;

import java.util.*;
import org.junit.jupiter.api.Test;
import de.casino.MiningBotVeins.Pos;
import static de.casino.LumberCycle.Stage.*;
import static de.casino.LumberCycle.Plot.*;
import static org.junit.jupiter.api.Assertions.*;

class LumberCycleTest {
    @Test void wholeRoundMustBePlantedThenEveryTreeMustGrowBeforeHarvest(){
        assertEquals(CLEARING,LumberCycle.next(CLEARING,true,List.of()));
        var stage=LumberCycle.next(CLEARING,false,List.of());assertEquals(PLANTING,stage);
        assertEquals(PLANTING,LumberCycle.next(stage,false,List.of(SAPLING,EMPTY,EMPTY)));
        stage=LumberCycle.next(stage,false,List.of(SAPLING,SAPLING,SAPLING));assertEquals(GROWING,stage);
        // Even with harvestable tree blocks, early trees must remain standing.
        assertEquals(GROWING,LumberCycle.next(stage,true,List.of(GROWN,SAPLING,SAPLING)));
        assertEquals(GROWING,LumberCycle.next(stage,true,List.of(GROWN,GROWN,SAPLING)));
        stage=LumberCycle.next(stage,true,List.of(GROWN,GROWN,GROWN));assertEquals(HARVESTING,stage);
        assertEquals(HARVESTING,LumberCycle.next(stage,true,List.of()));
        assertEquals(PLANTING,LumberCycle.next(stage,false,List.of()));
    }
    @Test void oneUnfinishedTreeHoldsBackAnEntireSixteenTreeRound(){
        List<LumberCycle.Plot> plots=new ArrayList<>(Collections.nCopies(16,GROWN));plots.set(15,SAPLING);
        assertEquals(GROWING,LumberCycle.next(GROWING,true,plots));
        plots.set(15,GROWN);assertEquals(HARVESTING,LumberCycle.next(GROWING,true,plots));
    }
    @Test void feedingVisitsAllRemainingSaplingsAndSkipsMatureTrees(){
        List<LumberCycle.Plot> plots=List.of(SAPLING,GROWN,SAPLING,SAPLING);List<Integer> fed=new ArrayList<>();int cursor=0;
        for(int attempt=0;attempt<6;attempt++){int next=LumberCycle.nextSapling(plots,cursor);fed.add(next);cursor=next+1;}
        assertEquals(List.of(0,2,3,0,2,3),fed);
        assertEquals(-1,LumberCycle.nextSapling(List.of(GROWN,GROWN),0));assertEquals(-1,LumberCycle.nextSapling(List.of(),0));
    }
    @Test void cancelledGrowthAndMissingSaplingsNeverCountAsMature(){
        Pos root=new Pos(3,64,3);
        assertEquals(SAPLING,LumberCycle.inspect(root,"OAK_SAPLING",true,p->"OAK_SAPLING"));
        assertEquals(EMPTY,LumberCycle.inspect(root,"OAK_SAPLING",false,p->"AIR"));
        assertEquals(BLOCKED,LumberCycle.inspect(root,"OAK_SAPLING",false,p->"STONE"));
        assertEquals(GROWN,LumberCycle.inspect(root,"OAK_SAPLING",true,p->"OAK_LOG"));
        assertEquals(GROWN,LumberCycle.inspect(root,"MANGROVE_PROPAGULE",true,p->"AIR")); // Verified trunk above the roots.
    }
    @Test void twoByTwoGroupNeedsAllSaplingsAndItsTrunkMustReallyExist(){
        Pos root=new Pos(-13,64,-13);Map<Pos,String> blocks=new HashMap<>();
        for(Pos p:LumberRules.footprint(root,"DARK_OAK_SAPLING"))blocks.put(p,"DARK_OAK_SAPLING");
        assertEquals(SAPLING,LumberCycle.inspect(root,"DARK_OAK_SAPLING",false,blocks::get));
        blocks.put(root,"AIR");assertEquals(BLOCKED,LumberCycle.inspect(root,"DARK_OAK_SAPLING",false,blocks::get));
        blocks.replaceAll((p,t)->"DARK_OAK_LOG");assertEquals(GROWN,LumberCycle.inspect(root,"DARK_OAK_SAPLING",true,blocks::get));
        assertEquals(BLOCKED,LumberCycle.inspect(root,"DARK_OAK_SAPLING",false,blocks::get));
    }
    @Test void missingPlantIsRepairedWithoutHarvestingOtherTreesAndEmptyRoundsNeverHarvest(){
        assertEquals(PLANTING,LumberCycle.next(GROWING,true,List.of(GROWN,EMPTY)));
        assertEquals(GROWING,LumberCycle.next(GROWING,true,List.of(GROWN,BLOCKED)));
        assertEquals(PLANTING,LumberCycle.next(PLANTING,false,List.of()));
        assertNotEquals(HARVESTING,LumberCycle.next(GROWING,true,List.of()));
    }
}
