package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import de.casino.MiningBotVeins.Pos;
import de.casino.AdvancedBotData.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedBotDataTest {
    @Test void overhangingCrownAndHarvestJournalSurviveRestart(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.LUMBER;s.chunkX=-1;s.chunkZ=-2;
        s.phase=Phase.WORKING;s.lumberStage=LumberCycle.Stage.HARVESTING;s.lumberPlanned=true;
        Pos rootPos=new Pos(-4,64,-20),branch=new Pos(0,68,-20),leaf=new Pos(1,69,-15);
        s.planted.put(rootPos,"ACACIA_SAPLING");s.grownTrunks.put(rootPos,branch);
        s.treeBlocks.put(branch,"ACACIA_LOG");s.treeBlocks.put(leaf,"ACACIA_LEAVES");
        store.save(s);State loaded=new AdvancedBotData(root).load().getFirst();
        assertEquals(s.grownTrunks,loaded.grownTrunks);assertEquals(s.treeBlocks,loaded.treeBlocks);
        loaded.treeBlocks.forEach((p,type)->{
            assertFalse(LumberRules.inChunk(p,loaded.chunkX,loaded.chunkZ));
            assertTrue(LumberRules.trackedTreePart(p,type,loaded.chunkX,loaded.chunkZ,loaded.treeBlocks));
            assertTrue(LumberRules.treeChunks(loaded.world,loaded.chunkX,loaded.chunkZ).contains(new PluginChunks.Key(loaded.world,p.x()>>4,p.z()>>4)));
        });
        loaded.treeBlocks.clear();loaded.pendingPosition=leaf;loaded.pendingBefore="minecraft:acacia_leaves[distance=1,persistent=false,waterlogged=false]";loaded.pendingAfter="minecraft:air";
        loaded.pendingOthers.add(new Change(branch,"minecraft:acacia_log[axis=x]","minecraft:air"));store.save(loaded);
        State resumed=new AdvancedBotData(root).load().getFirst();
        assertEquals(loaded.pendingPosition,resumed.pendingPosition);assertEquals(loaded.pendingBefore,resumed.pendingBefore);
        assertEquals(loaded.pendingOthers,resumed.pendingOthers);assertTrue(resumed.treeBlocks.isEmpty());
        assertEquals(LumberCycle.Stage.HARVESTING,resumed.lumberStage);
    }
    @Test void lumberRoundAndVerifiedTrunksSurviveStopUnloadAndRestart(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.LUMBER;s.lumberPlanned=true;s.treesScanned=true;
        Pos first=new Pos(3,64,3),second=new Pos(6,64,3),trunk=new Pos(3,66,3);
        s.planted.put(first,"OAK_SAPLING");s.planted.put(second,"OAK_SAPLING");s.grownTrunks.put(first,trunk);s.treeBlocks.put(trunk,"OAK_LOG");
        for(var stage:LumberCycle.Stage.values()){
            s.lumberStage=stage;s.phase=Phase.UNLOADING;s.resume=false;s.cursor=1;s.toolUses[2]=7;store.save(s);
            State loaded=new AdvancedBotData(root).load().getFirst();
            assertEquals(stage,loaded.lumberStage);assertTrue(loaded.lumberPlanned);assertEquals(s.planted,loaded.planted);
            assertEquals(s.grownTrunks,loaded.grownTrunks);assertEquals(s.treeBlocks,loaded.treeBlocks);assertEquals(1,loaded.cursor);assertFalse(loaded.resume);
            assertEquals(7,loaded.toolUses[2]); // The eighth block still charges wear after stop/unload/restart.
        }
    }
    @Test void oldBotsBeginWithClearingAndFailedPhaseSaveDoesNotAdvanceRound(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.LUMBER;store.save(s);
        Path file;try(var files=Files.list(root.resolve("advanced-bots"))){file=files.filter(p->p.toString().endsWith(".yml")).findFirst().orElseThrow();}
        Files.writeString(file,Files.readString(file).replace("lumber-stage: CLEARING","").replace("lumber-planned: false","").replace("grown-trunks: []",""));
        State old=store.load().getFirst();assertEquals(LumberCycle.Stage.CLEARING,old.lumberStage);assertFalse(old.lumberPlanned);assertTrue(old.grownTrunks.isEmpty());
        s.lumberStage=LumberCycle.Stage.GROWING;store.save(s);
        var denied=new AdvancedBotData(root,(source,target)->{throw new AccessDeniedException(target.toString());});
        s.lumberStage=LumberCycle.Stage.HARVESTING;assertThrows(AccessDeniedException.class,()->denied.save(s));
        assertEquals(LumberCycle.Stage.GROWING,store.load().getFirst().lumberStage);
    }
    @Test void lumberRootsGeneratedTreeAndPendingHarvestSurviveRestart(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.LUMBER;s.phase=Phase.WORKING;s.chunkX=-2;s.chunkZ=-1;
        s.treesScanned=true;s.planted.put(new Pos(-28,64,-12),"OAK_SAPLING");s.treeBlocks.put(new Pos(-28,68,-12),"OAK_LOG");
        s.pendingPosition=new Pos(-28,69,-12);s.pendingBefore="minecraft:oak_log[axis=y]";s.pendingAfter="minecraft:air";
        store.save(s);State loaded=new AdvancedBotData(root).load().getFirst();
        assertEquals(Kind.LUMBER,loaded.kind);assertTrue(loaded.treesScanned);assertEquals(s.planted,loaded.planted);assertEquals(s.treeBlocks,loaded.treeBlocks);
        assertEquals(s.pendingPosition,loaded.pendingPosition);assertEquals(s.items.length,loaded.items.length);
        loaded.treeBlocks.clear();store.save(loaded);assertTrue(store.load().getFirst().treeBlocks.isEmpty());assertEquals(s.planted,store.load().getFirst().planted);
    }
    private State state(){State s=new State();s.owner=UUID.randomUUID();s.world=UUID.randomUUID();s.kind=Kind.SEEKER;s.station=new Pos(4,70,8);s.position=new Pos(-40,-54,90);s.resumePosition=s.position;s.configured=true;s.items=new byte[]{1,2,3};return s;}
    @Test void oldLumberBotGetsInitialScanAndNewWorkChunkCanResetIt(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.LUMBER;s.treesScanned=true;store.save(s);
        Path file;try(var files=Files.list(root.resolve("advanced-bots"))){file=files.filter(p->p.toString().endsWith(".yml")).findFirst().orElseThrow();}
        Files.writeString(file,Files.readString(file).replace("trees-scanned: true", ""));
        State loaded=store.load().getFirst();assertFalse(loaded.treesScanned);
        loaded.treeBlocks.put(new Pos(1,65,1),"OAK_LOG");loaded.treesScanned=true;store.save(loaded);
        State restarted=store.load().getFirst();assertTrue(restarted.treesScanned);assertEquals(loaded.treeBlocks,restarted.treeBlocks);
        restarted.chunkX=4;restarted.treesScanned=false;restarted.treeBlocks.clear();store.save(restarted);
        assertFalse(store.load().getFirst().treesScanned);assertTrue(store.load().getFirst().treeBlocks.isEmpty());
    }
    @Test void exclusionsSurviveRestartAndCanBeRemoved(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();
        assertTrue(s.excluded.isEmpty());s.excluded.add("COBBLESTONE");s.excluded.add("DIRT");store.save(s);
        State loaded=new AdvancedBotData(root).load().getFirst();assertEquals(Set.of("COBBLESTONE","DIRT"),loaded.excluded);
        loaded.excluded.remove("DIRT");store.save(loaded);
        assertEquals(Set.of("COBBLESTONE"),new AdvancedBotData(root).load().getFirst().excluded);
    }
    @Test void offlineUnloadPreservesRouteVeinAndWorkingPosition(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.phase=Phase.UNLOADING;s.position=s.station;s.resume=true;s.energy=30;
        s.route=new ArrayList<>(List.of(new Pos(-41,-54,90),new Pos(-41,-55,90)));s.vein=new ArrayList<>(List.of(new Pos(-41,-55,90)));s.ores=new LinkedHashSet<>(List.of("DIAMOND_ORE","IRON_ORE"));
        store.save(s);State loaded=new AdvancedBotData(root).load().getFirst();
        assertEquals(s.id,loaded.id);assertEquals(s.owner,loaded.owner);assertEquals(s.world,loaded.world);assertEquals(s.station,loaded.position);
        assertEquals(s.resumePosition,loaded.resumePosition);assertTrue(loaded.resume);assertEquals(30,loaded.energy);assertEquals(s.route,loaded.route);assertEquals(s.vein,loaded.vein);assertEquals(s.ores,loaded.ores);assertArrayEquals(s.items,loaded.items);
    }
    @Test void quarryProgressAndPendingWorldChangeSurviveTogether(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.QUARRY;s.top=100;s.chunkX=-3;s.chunkZ=2;s.cursor=1000;s.phase=Phase.WORKING;
        s.pendingPosition=new Pos(-40,97,45);s.pendingBefore="minecraft:diamond_ore";s.pendingAfter="minecraft:air";store.save(s);
        State loaded=store.load().getFirst();assertEquals(1000,loaded.cursor);assertEquals(-3,loaded.chunkX);assertEquals(2,loaded.chunkZ);assertEquals(100,loaded.top);
        assertEquals(s.pendingPosition,loaded.pendingPosition);assertEquals(s.pendingBefore,loaded.pendingBefore);assertEquals(s.pendingAfter,loaded.pendingAfter);
        s.pendingPosition=null;s.pendingBefore=null;s.pendingAfter=null;store.save(s);assertNull(store.load().getFirst().pendingPosition);
    }
    @Test void failedWriteKeepsPreviousBotAndUnknownFormatFailsClosed(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();store.save(s);s.energy=99;
        var denied=new AdvancedBotData(root,(source,target)->{throw new AccessDeniedException(source.toString(),target.toString(),"simulated Windows lock");});
        assertThrows(AccessDeniedException.class,()->denied.save(s));assertEquals(0,store.load().getFirst().energy);
        Files.writeString(root.resolve("advanced-bots").resolve(UUID.randomUUID()+".yml"),"version: 999\n");assertThrows(Exception.class,store::load);
    }
    @Test void newSavesNeverOverwriteAnExistingGeneration(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root,(source,target)->{assertFalse(Files.exists(target));Files.move(source,target);});
        State s=state();store.save(s);Path first;
        try(var files=Files.list(root.resolve("advanced-bots"))){first=files.filter(p->p.toString().endsWith(".yml")).findFirst().orElseThrow();}
        String original=Files.readString(first);
        try(var held=java.nio.channels.FileChannel.open(first,StandardOpenOption.READ)){
            s.energy=47;store.save(s);assertEquals(original,Files.readString(first));assertEquals(47,new AdvancedBotData(root).load().getFirst().energy);
        }
        for(int i=0;i<5;i++){s.energy++;store.save(s);}
        try(var files=Files.list(root.resolve("advanced-bots"))){assertEquals(2,files.filter(p->p.toString().endsWith(".yml")).count());}
        store.remove(s.id);assertTrue(new AdvancedBotData(root).load().isEmpty());
    }
    @Test void fullLayerJournalAndLegacyFilenameCanBeLoaded(@TempDir Path root)throws Exception{
        var store=new AdvancedBotData(root);State s=state();s.kind=Kind.QUARRY;s.cursor=256;
        s.pendingPosition=new Pos(0,64,0);s.pendingBefore="minecraft:stone";s.pendingAfter="minecraft:air";
        for(int i=1;i<256;i++)s.pendingOthers.add(new Change(new Pos(i%16,64,i/16),"minecraft:stone","minecraft:air"));
        store.save(s);State loaded=store.load().getFirst();assertEquals(255,loaded.pendingOthers.size());assertEquals(s.pendingOthers,loaded.pendingOthers);assertEquals(256,loaded.cursor);
        Path file;try(var files=Files.list(root.resolve("advanced-bots"))){file=files.filter(p->p.toString().endsWith(".yml")).findFirst().orElseThrow();}
        Files.move(file,file.resolveSibling(s.id+".yml"));assertEquals(s.id,store.load().getFirst().id);
    }
}
