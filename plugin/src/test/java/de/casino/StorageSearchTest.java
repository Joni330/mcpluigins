package de.casino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageSearchTest {
    @Test void searchAcrossPagesWrapsAndHandlesRemovedItems() {
        assertEquals(449, StorageSearch.next(450, -1, i -> i == 449));
        assertEquals(0, StorageSearch.next(450, 449, i -> i == 0));
        assertEquals(90, StorageSearch.next(450, 45, i -> i == 45 || i == 90));
        assertEquals(-1, StorageSearch.next(450, 45, i -> false));
    }
    @Test void materialAliasAndCustomNames() {
        assertTrue(StorageSearch.matches("IRON_INGOT", "", "Eisen"));
        assertTrue(StorageSearch.matches("IRON_INGOT", "", "iron ingot"));
        assertTrue(StorageSearch.matches("DIAMOND_SWORD", "MilOs Schwert", "milos"));
        assertFalse(StorageSearch.matches("GOLD_INGOT", "", "diamant"));
    }
}
