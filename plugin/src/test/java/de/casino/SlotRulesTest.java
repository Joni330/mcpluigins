package de.casino;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SlotRulesTest {
    @Test void exactDistribution() {
        int[] counts = new int[9];
        for (int i = 0; i < SlotRules.TOTAL_WEIGHT; i++) counts[SlotRules.select(i).ordinal()]++;
        assertArrayEquals(new int[]{125,150,100,70,50,40,20,10,435}, counts);
        assertThrows(IllegalArgumentException.class, () -> SlotRules.select(-1));
        assertThrows(IllegalArgumentException.class, () -> SlotRules.select(1000));
    }
    @Test void roundsHalfUpAndUsesAllMultipliers() {
        long[] expected = {0,50,120,150,200,250,300,500,0};
        for (var outcome : SlotRules.Outcome.values()) assertEquals(expected[outcome.ordinal()], outcome.payout(100));
        assertEquals(5, SlotRules.Outcome.COPPER.payout(10));
        assertEquals(12, SlotRules.Outcome.IRON.payout(10));
        assertEquals(2, SlotRules.Outcome.GOLD.payout(1));
    }
    @Test void respinsDoNotChargeAgain() {
        Random scripted = new Random() {
            int position;
            @Override public int nextInt(int bound) {
                assertEquals(1000, bound);
                return new int[]{0,124,564}[position++];
            }
        };
        var play = SlotRules.draw(100, scripted);
        assertEquals(3, play.rounds().size());
        assertEquals(500, play.payout());
        assertEquals(1400, SlotRules.settledBalance(1000, 100, play.payout()));
        assertEquals(900, SlotRules.settledBalance(1000, 100, 0));
        assertThrows(IllegalStateException.class, () -> SlotRules.settledBalance(9, 10, 50));
        assertThrows(ArithmeticException.class, () -> SlotRules.settledBalance(Long.MAX_VALUE, 10, 50));
    }
    @Test void lossesNeverShowThreeMatchingSymbols() {
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            var play = SlotRules.draw(10, random);
            for (var round : play.rounds()) {
                boolean triple = round.reels().stream().distinct().count() == 1;
                assertEquals(round.outcome() != SlotRules.Outcome.LOSS, triple);
            }
        }
    }
}
