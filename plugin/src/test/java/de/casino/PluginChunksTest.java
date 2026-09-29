package de.casino;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PluginChunksTest {
    @Test void workAreaCoversNegativeChunkEdgesAndVeinRadius() {
        UUID world = UUID.randomUUID();
        for (int x : new int[]{-17,-16,-1,0,15,16}) {
            var area = PluginChunks.area(world,x,x,11);
            assertTrue(area.size() <= 9);
            for (int dx=-11; dx<=11; dx++) for (int dz=-11; dz<=11; dz++)
                assertTrue(area.contains(new PluginChunks.Key(world,(x+dx)>>4,(x+dz)>>4)));
        }
    }
    @Test void sharedBotAndStorageChunkRemainsNeededUntilLastOwnerLeaves() {
        var key = new PluginChunks.Key(UUID.randomUUID(),-1,0);
        var requests = new ArrayList<Set<PluginChunks.Key>>();
        requests.add(Set.of(key)); requests.add(Set.of(key));
        requests.removeFirst(); assertTrue(PluginChunks.stillNeeded(requests,key));
        requests.removeFirst(); assertFalse(PluginChunks.stillNeeded(requests,key));
    }
}
