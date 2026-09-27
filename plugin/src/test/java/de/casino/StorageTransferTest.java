package de.casino;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class StorageTransferTest {
    @Test void fillsMatchingStacksBeforeEmptySlotsAcrossPages() {
        int[] room = new int[450]; boolean[] matching = new boolean[450];
        room[0] = 64; room[449] = 4; matching[449] = true;
        int[] moves = StorageTransfer.plan(16, room, matching);
        assertEquals(4, moves[449]); assertEquals(12, moves[0]);
        assertEquals(16, Arrays.stream(moves).sum());
    }
    @Test void fullAndPartlyFullStorageNeverConsumesTheRemainder() {
        assertArrayEquals(new int[]{0, 0}, StorageTransfer.plan(16, new int[]{0, 0}, new boolean[]{false, true}));
        assertArrayEquals(new int[]{0, 3}, StorageTransfer.plan(16, new int[]{0, 3}, new boolean[]{false, true}));
        assertArrayEquals(new int[]{0, 2}, StorageTransfer.plan(2, new int[]{0, 3}, new boolean[]{false, true}));
    }
    @Test void respectsNonStackableItemsAndPerTickLimit() {
        int[] room = new int[30]; Arrays.fill(room, 1);
        int[] moves = StorageTransfer.plan(64, room, new boolean[30]);
        assertEquals(16, Arrays.stream(moves).sum());
        assertTrue(Arrays.stream(moves).allMatch(i -> i <= 1));
    }
}
