package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import de.casino.MiningBotVeins.Pos;
import de.casino.AdvancedBotData.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedBotDataTest {
    private State state(){State s=new State();s.owner=UUID.randomUUID();s.world=UUID.randomUUID();s.kind=Kind.SEEKER;s.station=new Pos(4,70,8);s.position=new Pos(-40,-54,90);s.resumePosition=s.position;s.configured=true;s.items=new byte[]{1,2,3};return s;}
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
