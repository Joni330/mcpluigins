package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VirtualStorageTest {
    record Item(String type, String metadata) {}
    final Item iron = new Item("iron", "");
    @Test void legacyFull450SlotsMigrateWithoutQuantityLoss() {
        VirtualStorage<Item> storage = new VirtualStorage<>(450);
        for (int i = 0; i < 450; i++) assertEquals(64, storage.insert(iron, 64, Objects::equals));
        assertEquals(1024, storage.get(0).count());
        assertEquals(128, storage.get(28).count());
        assertNull(storage.get(29));
        int total = 0;
        for (int i = 0; i < storage.size(); i++) if (storage.get(i) != null) total += storage.get(i).count();
        assertEquals(450 * 64, total);
    }
    @Test void partialAcceptanceLeavesOverflowAndNeverExceeds1024() {
        VirtualStorage<Item> storage = new VirtualStorage<>(2);
        assertEquals(1020, storage.insert(iron, 1020, Objects::equals));
        assertEquals(16, storage.insert(iron, 16, Objects::equals));
        assertEquals(1024, storage.get(0).count()); assertEquals(12, storage.get(1).count());
        assertEquals(1012, storage.insert(iron, 2000, Objects::equals));
        assertEquals(0, storage.insert(iron, 16, Objects::equals));
    }
    @Test void distinctMetadataRemainsSeparateAndUnchanged() {
        VirtualStorage<Item> storage = new VirtualStorage<>(4);
        Item named = new Item("iron", "Milos Eisen");
        Item enchanted = new Item("sword", "sharpness:5;damage:4;custom:model");
        storage.insert(iron, 64, Objects::equals); storage.insert(named, 7, Objects::equals);
        storage.insert(enchanted, 2, Objects::equals);
        assertEquals(named, storage.get(1).item()); assertEquals(7, storage.get(1).count());
        assertEquals(1, storage.remove(2, 1)); assertEquals(enchanted, storage.get(2).item());
    }
    @Test void withdrawalsAndSenderOperateOnLatestQuantity() {
        VirtualStorage<Item> storage = new VirtualStorage<>(1);
        storage.insert(iron, 70, Objects::equals);
        assertEquals(64, storage.remove(0, 64));
        assertEquals(1, storage.remove(0, 1));
        assertEquals(5, storage.remove(0, 64));
        assertEquals(0, storage.remove(0, 64));
        assertNull(storage.get(0));
        assertEquals(16, storage.insert(iron, 16, Objects::equals));
    }
    @Test void rejectedSnapshotAndSortingDoNotMutateOriginal() {
        VirtualStorage<Item> original = new VirtualStorage<>(3);
        original.set(2, iron, 900);
        VirtualStorage<Item> proposed = original.copy();
        proposed.remove(2, 64); proposed.sort(Comparator.comparingInt(VirtualStorage.Entry<Item>::count));
        assertNull(original.get(0)); assertEquals(900, original.get(2).count());
        assertEquals(836, proposed.get(0).count()); assertNull(proposed.get(2));
    }
    @Test void invalidCountsFailClosed() {
        VirtualStorage<Item> storage = new VirtualStorage<>(1);
        assertThrows(IllegalArgumentException.class, () -> storage.set(0, iron, 1025));
        assertThrows(IllegalArgumentException.class, () -> storage.set(0, iron, -1));
        assertNull(storage.get(0));
    }
}
