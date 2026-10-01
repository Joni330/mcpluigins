package de.casino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageLayoutTest {
    @Test void oldInventoryKeepsEverySlotIncludingEmptyGaps() {
        String[] old = new String[54];
        old[0] = "enchanted sword"; old[44] = "diamonds";
        old[45] = "filled shulker"; old[53] = "named item";
        String[] migrated = StorageLayout.migrate(old);
        assertEquals(450, migrated.length);
        for (int i = 0; i < old.length; i++) assertEquals(old[i], migrated[i]);
        for (int i = 54; i < migrated.length; i++) assertNull(migrated[i]);
        assertEquals(1, StorageLayout.page(45));
        assertEquals(0, StorageLayout.slot(45));
        assertEquals(8, StorageLayout.slot(53));
        assertArrayEquals(migrated, StorageLayout.migrate(migrated));
    }
    @Test void noStorageItemMapsIntoControlRow() {
        boolean[][] occupied = new boolean[10][45];
        for (int i = 0; i < 450; i++) {
            int page = StorageLayout.page(i), slot = StorageLayout.slot(i);
            assertFalse(occupied[page][slot]); occupied[page][slot] = true;
            assertEquals(i, page * 45 + slot);
        }
        assertEquals(10,StorageLayout.page(450));
        assertThrows(IndexOutOfBoundsException.class, () -> StorageLayout.slot(-1));
        assertThrows(IllegalArgumentException.class, () -> StorageLayout.migrate(new String[55]));
    }
    @Test void expandedPayloadKeepsLastPageAndRejectsPartialPages(){
        String[] old=new String[StorageLayout.capacity(13)];old[0]="named tool";old[584]="shulker with items";
        String[] migrated=StorageLayout.migrate(old);
        assertArrayEquals(old,migrated);assertNotSame(old,migrated);assertEquals(13,StorageLayout.pages(migrated.length));
        assertEquals(12,StorageLayout.page(584));assertEquals(44,StorageLayout.slot(584));
        assertThrows(IllegalArgumentException.class,()->StorageLayout.pages(451));
        assertThrows(IllegalArgumentException.class,()->StorageLayout.migrate(new String[449]));
        assertThrows(IllegalArgumentException.class,()->StorageLayout.capacity(9));
        assertThrows(ArithmeticException.class,()->StorageLayout.capacity(Integer.MAX_VALUE));
    }
}
