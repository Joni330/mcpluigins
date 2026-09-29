package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MiningBotFluidsTest {
    @Test void sealsEveryExposedFaceWithoutFillingTheWalkableInterior() {
        var shell = new HashSet<>(MiningBotFluids.shell());
        assertEquals(12, shell.size());
        for (int side=-1;side<=1;side++) for (int y=0;y<=2;y++) {
            assertFalse(shell.contains(new MiningBotFluids.Offset(side,y)));
            if (side==-1) assertTrue(shell.contains(new MiningBotFluids.Offset(side-1,y)));
            if (side==1) assertTrue(shell.contains(new MiningBotFluids.Offset(side+1,y)));
            if (y==0) assertTrue(shell.contains(new MiningBotFluids.Offset(side,y-1)));
            if (y==2) assertTrue(shell.contains(new MiningBotFluids.Offset(side,y+1)));
        }
    }
    @Test void fluidShieldPersistsIndependentlyOfFloorAndLighting(@TempDir Path dir) throws Exception {
        var store = new MiningBotStore(dir);
        var state = new MiningBotStore.Saved(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),0,64,0,0,
                false,new byte[]{1},MiningBotWork.idle(),MiningBotLighting.off(),MiningBotVeins.off(),false,true);
        store.save(state);
        var loaded = new MiningBotStore(dir).load().getFirst();
        assertTrue(loaded.fluidShield()); assertFalse(loaded.floorBuilder()); assertFalse(loaded.lighting().enabled());
        assertArrayEquals(state.items(),loaded.items());
        var legacy = new MiningBotStore.Saved(state.id(),state.owner(),state.world(),0,64,0,0,false,state.items());
        assertFalse(legacy.fluidShield());
    }
}
