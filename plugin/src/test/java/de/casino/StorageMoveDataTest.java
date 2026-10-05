package de.casino;

import de.casino.StorageMoveData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageMoveDataTest {
    @TempDir Path folder;
    final UUID id=UUID.randomUUID(),owner=UUID.randomUUID(),world=UUID.randomUUID();
    final Point source=new Point(world,-20,70,18),target=new Point(world,280,90,-15);
    Snapshot snapshot(){
        int[] counts=new int[StorageLayout.capacity(12)];Arrays.fill(counts,0,8*45,1024);counts[counts.length-1]=7;
        return new Snapshot(owner,new byte[]{1,8,3,7,12},counts,new LinkedHashMap<>(Map.of("Alle","*","Eisen","iron","Garten","sapling")));
    }
    @Test void eightFullPagesAndPaidCapacitySurvivePackingAndRestart()throws Exception{
        var data=new StorageMoveData(folder);var snapshot=snapshot();var packed=data.pack(id,source,snapshot);
        var restored=new StorageMoveData(folder).get(id);
        assertEquals(Phase.PACKED,restored.phase());assertEquals(packed.token(),restored.token());assertEquals(owner,restored.snapshot().owner());
        assertEquals(12,StorageLayout.pages(restored.snapshot().counts().length));assertArrayEquals(snapshot.items(),restored.snapshot().items());
        assertArrayEquals(snapshot.counts(),restored.snapshot().counts());assertEquals(snapshot.categories(),restored.snapshot().categories());
        assertFalse(data.accessible(id,source));assertThrows(IllegalStateException.class,()->data.resolve(source.link(id)));
    }
    @Test void keepsIdAndRedirectsOldPhoneSenderAndReceiverBindingsAfterMultipleMoves()throws Exception{
        var data=new StorageMoveData(folder);var packed=data.pack(id,source,snapshot());
        data.beginPlace(id,packed.token(),owner,false,target);data.finish(id,packed.token());
        assertEquals(target.link(id),data.resolve(source.link(id)));assertFalse(data.accessible(id,source));assertTrue(data.accessible(id,target));
        Point third=new Point(UUID.randomUUID(),-100,80,-300);var second=data.pack(id,target,snapshot());
        assertThrows(IllegalStateException.class,()->data.resolve(source.link(id)));
        data.beginPlace(id,second.token(),owner,false,third);data.finish(id,second.token());
        var restarted=new StorageMoveData(folder);
        assertEquals(third.link(id),restarted.resolve(source.link(id)));assertEquals(third.link(id),restarted.resolve(target.link(id)));
        assertFalse(restarted.accessible(id,target));assertTrue(restarted.accessible(id,third));
    }
    @Test void duplicateTokensCannotPlaceTwiceOrRepackAStalePhysicalTerminal()throws Exception{
        var data=new StorageMoveData(folder);var packed=data.pack(id,source,snapshot());
        data.beginPlace(id,packed.token(),owner,false,target);
        assertThrows(IllegalArgumentException.class,()->data.beginPlace(id,packed.token(),owner,false,new Point(world,20,60,20)));
        assertEquals(Phase.PLACING,data.get(id).phase());assertEquals(target,data.get(id).target());
        data.finish(id,packed.token());
        assertThrows(IllegalArgumentException.class,()->data.beginPlace(id,packed.token(),owner,false,source));
        assertThrows(IllegalStateException.class,()->data.pack(id,source,snapshot()));
    }
    @Test void foreignPlayerCannotPlaceButAdminPreservesOriginalOwner()throws Exception{
        var data=new StorageMoveData(folder);var packed=data.pack(id,source,snapshot());UUID stranger=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->data.beginPlace(id,packed.token(),stranger,false,target));
        assertEquals(Phase.PACKED,data.get(id).phase());
        data.beginPlace(id,packed.token(),stranger,true,target);var finished=data.finish(id,packed.token());
        assertEquals(owner,finished.snapshot().owner());
    }
    @Test void cancelledPlacementAndRestartCanRollBackWithoutLosingContents()throws Exception{
        var data=new StorageMoveData(folder);var packed=data.pack(id,source,snapshot());data.beginPlace(id,packed.token(),owner,false,target);
        var restarted=new StorageMoveData(folder);assertEquals(Phase.PLACING,restarted.get(id).phase());
        assertFalse(restarted.accessible(id,target));var rolledBack=restarted.rollback(id,packed.token());
        assertEquals(Phase.PACKED,rolledBack.phase());assertNull(rolledBack.target());assertArrayEquals(snapshot().counts(),rolledBack.snapshot().counts());
        restarted.beginPlace(id,packed.token(),owner,false,target);restarted.finish(id,packed.token());assertTrue(restarted.accessible(id,target));
    }
    @Test void lostItemRecoveryInvalidatesAllEarlierCopies()throws Exception{
        var data=new StorageMoveData(folder);var packed=data.pack(id,source,snapshot());var reissued=data.reissue(id);
        assertNotEquals(packed.token(),reissued.token());assertThrows(IllegalArgumentException.class,()->data.beginPlace(id,packed.token(),owner,false,target));
        data.beginPlace(id,reissued.token(),owner,false,target);data.finish(id,reissued.token());
        assertThrows(IllegalArgumentException.class,()->data.reissue(id));
        assertThrows(IllegalArgumentException.class,()->data.beginPlace(id,reissued.token(),owner,false,source));
    }
    @Test void saveFailureDoesNotChangeJournalOrMemory()throws Exception{
        var data=new StorageMoveData(folder);var packed=data.pack(id,source,snapshot());
        var failing=new StorageMoveData(folder,(from,to)->{throw new AccessDeniedException(to.toString());});
        assertThrows(IOException.class,()->failing.beginPlace(id,packed.token(),owner,false,target));
        assertThrows(IOException.class,()->failing.reissue(id));assertEquals(Phase.PACKED,failing.get(id).phase());assertEquals(packed.token(),failing.get(id).token());
        assertEquals(Phase.PACKED,new StorageMoveData(folder).get(id).phase());
        data.beginPlace(id,packed.token(),owner,false,target);
        var placing=new StorageMoveData(folder,(from,to)->{throw new IOException("disk full");});
        assertThrows(IOException.class,()->placing.finish(id,packed.token()));assertThrows(IOException.class,()->placing.rollback(id,packed.token()));
        assertEquals(Phase.PLACING,new StorageMoveData(folder).get(id).phase());
        try(var files=Files.list(folder.resolve("storage-moves"))){assertTrue(files.noneMatch(p->p.toString().endsWith(".tmp")));}
    }
    @Test void failedInitialPackLeavesOriginalAccessible()throws Exception{
        var data=new StorageMoveData(folder,(from,to)->{throw new IOException("disk full");});
        assertThrows(IOException.class,()->data.pack(id,source,snapshot()));assertTrue(data.accessible(id,source));assertNull(data.get(id));
    }
    @Test void untrackedTerminalsAndMalformedLinksBehaveSafely()throws Exception{
        var data=new StorageMoveData(folder);assertEquals(source.link(id),data.resolve(source.link(id)));assertNull(data.resolve(null));
        assertThrows(IllegalArgumentException.class,()->data.resolve("invalid"));assertThrows(IllegalArgumentException.class,()->data.resolve(world+";0;0;0;invalid"));
        assertThrows(IllegalArgumentException.class,()->data.reissue(id));
    }
    @Test void snapshotsDefensivelyCopyPayloadCountsAndCategories(){
        byte[] items={1};int[] counts=new int[450];counts[0]=5;Map<String,String> categories=new LinkedHashMap<>(Map.of("Alle","*"));
        var snapshot=new Snapshot(owner,items,counts,categories);items[0]=8;counts[0]=9;categories.clear();
        snapshot.items()[0]=7;snapshot.counts()[0]=12;
        assertArrayEquals(new byte[]{1},snapshot.items());assertEquals(5,snapshot.counts()[0]);assertEquals(Map.of("Alle","*"),snapshot.categories());
    }
    @Test void corruptJournalFailsClosedWithoutOverwritingThePayload()throws Exception{
        var data=new StorageMoveData(folder);data.pack(id,source,snapshot());
        Path file;try(var paths=Files.list(folder.resolve("storage-moves"))){file=paths.findFirst().orElseThrow();}
        String broken=Files.readString(file).replace("phase: PACKED","phase: BROKEN");Files.writeString(file,broken);
        assertThrows(Exception.class,()->new StorageMoveData(folder));assertEquals(broken,Files.readString(file));
    }
}
