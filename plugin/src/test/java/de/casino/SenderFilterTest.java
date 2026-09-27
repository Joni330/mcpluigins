package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SenderFilterTest {
    @Test void emptyListsHaveSafeExplicitSemantics() {
        assertTrue(new SenderFilter(SenderFilter.Mode.ALL, List.of()).accepts("WHEAT"));
        assertFalse(new SenderFilter(SenderFilter.Mode.ALLOW, List.of()).accepts("WHEAT"));
        assertTrue(new SenderFilter(SenderFilter.Mode.DENY, List.of()).accepts("WHEAT"));
    }
    @Test void wheatFarmRulesAndModeCycle() {
        SenderFilter filter = new SenderFilter(SenderFilter.Mode.ALL, List.of()).add("WHEAT").cycle();
        assertTrue(filter.accepts("WHEAT")); assertFalse(filter.accepts("WHEAT_SEEDS"));
        filter = filter.cycle();
        assertFalse(filter.accepts("WHEAT")); assertTrue(filter.accepts("WHEAT_SEEDS"));
        assertEquals(SenderFilter.Mode.ALL, filter.cycle().mode());
    }
    @Test void nineDistinctPatternsAndRemovalWithoutMutatingPreviousState() {
        SenderFilter original = new SenderFilter(SenderFilter.Mode.ALLOW, List.of("WHEAT"));
        assertEquals(1, original.add("WHEAT").materials().size());
        SenderFilter full = original;
        for (int i = 0; i < 12; i++) full = full.add("ITEM" + i);
        assertEquals(9, full.materials().size());
        assertEquals(List.of("WHEAT"), original.materials());
        assertFalse(full.remove(0).accepts("WHEAT"));
        assertTrue(full.accepts("WHEAT"));
    }
}
