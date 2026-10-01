package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageCraftingPlanTest {
    @Test void takesRepeatedIngredientsAcrossPagesWithoutTouchingLiveStorage() {
        VirtualStorage<String> source = new VirtualStorage<String>(450).expanded(StorageLayout.capacity(12));
        source.set(0, "iron", 1);
        source.set(539, "iron", 2);
        var plan = StorageCraftingPlan.reserve(source, Arrays.asList("iron", null, "iron", "iron"), Objects::equals);
        assertNotNull(plan);
        assertNull(plan.get(0)); assertNull(plan.get(539));
        assertEquals(1, source.get(0).count()); assertEquals(2, source.get(539).count());
    }
    @Test void shortageAfterPartialReservationConsumesNothing() {
        VirtualStorage<String> source = new VirtualStorage<>(2);
        source.set(0, "iron", 2);
        assertNull(StorageCraftingPlan.reserve(source, List.of("iron", "iron", "iron"), Objects::equals));
        assertEquals(2, source.get(0).count());
    }
    @Test void metadataMustMatchAndRejectedPlanCanBeDiscarded() {
        VirtualStorage<String> source = new VirtualStorage<>(2);
        source.set(0, "iron:named", 10);
        source.set(1, "iron:plain", 1);
        var plan = StorageCraftingPlan.reserve(source, List.of("iron:plain"), Objects::equals);
        assertNotNull(plan); assertNull(plan.get(1)); assertEquals(10, plan.get(0).count());
        // Full destination or failed persistence: discard the plan, source remains unchanged.
        assertEquals(1, source.get(1).count());
        assertNull(StorageCraftingPlan.reserve(source, List.of("iron:plain", "iron:plain"), Objects::equals));
    }
    @Test void secondTeamMemberUsesCurrentQuantityInsteadOfOldPreview() {
        VirtualStorage<String> source = new VirtualStorage<>(1);
        source.set(0, "diamond", 1);
        var first = StorageCraftingPlan.reserve(source, List.of("diamond"), Objects::equals);
        assertNotNull(first);
        assertNull(StorageCraftingPlan.reserve(first, List.of("diamond"), Objects::equals));
    }
}
