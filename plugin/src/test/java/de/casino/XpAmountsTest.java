package de.casino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XpAmountsTest {
    @Test void levelBoundariesMatchCumulativeVanillaCosts() {
        int sum = 0;
        for (int level = 0; level < 1000; level++) {
            assertEquals(sum, XpAmounts.levelPoints(level), "Level " + level);
            sum += level < 16 ? 2 * level + 7 : level < 31 ? 5 * level - 38 : 9 * level - 158;
        }
        assertEquals(352, XpAmounts.levelPoints(16));
        assertEquals(1507, XpAmounts.levelPoints(31));
        assertEquals(1628, XpAmounts.levelPoints(32));
    }
    @Test void depositsReachLowerBoundaryIncludingProgressWithoutNegativeLevels() {
        assertEquals(1395 - 1288, XpAmounts.deposit(1395, 30, 1, 0));
        assertEquals(1395 - 910, XpAmounts.deposit(1395, 30, 5, 0));
        assertEquals(1395, XpAmounts.deposit(1395, 30, 0, 0));
        assertEquals(3, XpAmounts.deposit(3, 0, 1, 0));
        assertEquals(8, XpAmounts.deposit(8, 1, 5, 0));
        assertEquals(0, XpAmounts.deposit(0, 0, 5, 0));
    }
    @Test void withdrawalsReachHigherBoundaryAndKeepRemainderWhenBankInsufficient() {
        assertEquals(1507 - 1395, XpAmounts.withdraw(1395, 30, 1, 10_000));
        assertEquals(XpAmounts.levelPoints(35) - 1395, XpAmounts.withdraw(1395, 30, 5, 10_000));
        assertEquals(20, XpAmounts.withdraw(1395, 30, 5, 20));
        assertEquals(10_000, XpAmounts.withdraw(1395, 30, 0, 10_000));
        assertEquals(0, XpAmounts.withdraw(1395, 30, 1, 0));
    }
    @Test void nativePlayerLimitAndLongTankLimitNeverOverflow() {
        assertEquals(Integer.MAX_VALUE, XpAmounts.levelPoints(Integer.MAX_VALUE));
        assertEquals(2, XpAmounts.withdraw(Integer.MAX_VALUE - 2, 21863, 0, Long.MAX_VALUE));
        assertEquals(0, XpAmounts.withdraw(Integer.MAX_VALUE, 21863, 5, Long.MAX_VALUE));
        assertEquals(4, XpAmounts.deposit(100, 7, 0, Long.MAX_VALUE - 4));
        assertEquals(0, XpAmounts.deposit(100, 7, 0, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> XpAmounts.levelPoints(-1));
        assertThrows(IllegalArgumentException.class, () -> XpAmounts.withdraw(-1, 0, 0, 0));
    }
}
