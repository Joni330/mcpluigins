package de.casino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlotMenuTest {
    @Test void euroStepsRespectBounds() {
        assertEquals(110, SlotMenu.adjustBet(10, true, 100));
        assertEquals(10, SlotMenu.adjustBet(110, false, 100));
        assertEquals(10, SlotMenu.adjustBet(50, false, 100));
        assertEquals(1000, SlotMenu.adjustBet(950, true, 100));
    }
    @Test void adjustsInTenCentStepsAndClampsAtLimits() {
        assertEquals(20, SlotMenu.adjustBet(10, true));
        assertEquals(10, SlotMenu.adjustBet(20, false));
        assertEquals(10, SlotMenu.adjustBet(10, false));
        assertEquals(1000, SlotMenu.adjustBet(1000, true));
        long bet = 10;
        for (int i = 0; i < 99; i++) bet = SlotMenu.adjustBet(bet, true);
        assertEquals(1000, bet);
        for (int i = 0; i < 99; i++) bet = SlotMenu.adjustBet(bet, false);
        assertEquals(10, bet);
    }
}
