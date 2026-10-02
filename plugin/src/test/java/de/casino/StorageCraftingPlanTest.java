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
    @Test void repeatedIngredientCanBeSplitBetweenStorageAndPlayerSlots() {
        VirtualStorage<String> storage = new VirtualStorage<>(450), inventory = new VirtualStorage<>(36);
        storage.set(449, "diamond", 2); inventory.set(0, "diamond", 1); inventory.set(35, "diamond", 3);
        var plan = StorageCraftingPlan.reserve(storage, inventory, Collections.nCopies(5, "diamond"), Objects::equals);
        assertNotNull(plan); assertNull(plan.storage().get(449)); assertNull(plan.inventory().get(0));
        assertEquals(1, plan.inventory().get(35).count());
        assertEquals(2, storage.get(449).count()); assertEquals(3, inventory.get(35).count());
    }
    @Test void usesStorageBeforeInventoryForIdenticalIngredients() {
        VirtualStorage<String> storage = new VirtualStorage<>(1), inventory = new VirtualStorage<>(36);
        storage.set(0, "iron", 5); inventory.set(8, "iron", 64);
        var plan = StorageCraftingPlan.reserve(storage, inventory, List.of("iron", "iron"), Objects::equals);
        assertNotNull(plan); assertEquals(3, plan.storage().get(0).count());
        assertEquals(64, plan.inventory().get(8).count());
    }
    @Test void canCraftEntirelyFromInventoryAndFreeConsumedSlotsForResults() {
        VirtualStorage<String> storage = new VirtualStorage<>(450), inventory = new VirtualStorage<>(36);
        inventory.set(9, "stick", 2); inventory.set(35, "diamond", 3);
        var plan = StorageCraftingPlan.reserve(storage, inventory, List.of("diamond", "diamond", "diamond", "stick", "stick"), Objects::equals);
        assertNotNull(plan); assertNull(plan.inventory().get(9)); assertNull(plan.inventory().get(35));
        assertEquals(2, inventory.get(9).count()); assertEquals(3, inventory.get(35).count());
    }
    @Test void shortageOrDiscardLeavesBothSourcesUntouched() {
        VirtualStorage<String> storage = new VirtualStorage<>(1), inventory = new VirtualStorage<>(2);
        storage.set(0, "iron", 1); inventory.set(1, "iron", 1);
        assertNull(StorageCraftingPlan.reserve(storage, inventory, Collections.nCopies(3, "iron"), Objects::equals));
        // A valid reservation may also be discarded for a full output inventory or failed persistence.
        assertNotNull(StorageCraftingPlan.reserve(storage, inventory, Collections.nCopies(2, "iron"), Objects::equals));
        assertEquals(1, storage.get(0).count()); assertEquals(1, inventory.get(1).count());
    }
    @Test void metadataInPlayerInventoryMustMatchTemplateAndOtherSlotsStayInPlace() {
        VirtualStorage<String> storage = new VirtualStorage<>(1), inventory = new VirtualStorage<>(36);
        inventory.set(0, "iron:named", 10); inventory.set(1, "iron:plain", 2); inventory.set(35, "sword:enchanted", 1);
        var plan = StorageCraftingPlan.reserve(storage, inventory, List.of("iron:plain"), Objects::equals);
        assertNotNull(plan); assertEquals(10, plan.inventory().get(0).count()); assertEquals(1, plan.inventory().get(1).count());
        assertEquals("sword:enchanted", plan.inventory().get(35).item());
        assertNull(StorageCraftingPlan.reserve(storage, inventory, List.of("iron:other"), Objects::equals));
    }
    @Test void repeatedCraftsUseUpdatedInventoryUntilCombinedStockRunsOut() {
        VirtualStorage<String> storage = new VirtualStorage<>(1), inventory = new VirtualStorage<>(36);
        storage.set(0, "iron", 2); inventory.set(27, "iron", 3);
        var nextStorage = storage; var nextInventory = inventory; int crafts = 0;
        for (int i = 0; i < 64; i++) {
            var plan = StorageCraftingPlan.reserve(nextStorage, nextInventory, List.of("iron"), Objects::equals);
            if (plan == null) break;
            crafts++; nextStorage = plan.storage(); nextInventory = plan.inventory();
        }
        assertEquals(5, crafts); assertNull(nextStorage.get(0)); assertNull(nextInventory.get(27));
        assertEquals(2, storage.get(0).count()); assertEquals(3, inventory.get(27).count());
    }
}
