package de.casino;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExchangeRulesTest {
    @Test void ironPayoutScalesWithActualStackSize() {
        assertEquals(200, ExchangeRules.payout(Material.IRON_INGOT, 1));
        assertEquals(400, ExchangeRules.payout(Material.IRON_INGOT, 2));
        assertEquals(12800, ExchangeRules.payout(Material.IRON_INGOT, 64));
        assertEquals(0, ExchangeRules.payout(Material.IRON_INGOT, 0));
        assertEquals(0, ExchangeRules.payout(Material.IRON_INGOT, -1));
        assertEquals(600, ExchangeRules.payout(Material.GOLD_INGOT, 2));
        assertThrows(ArithmeticException.class, () -> ExchangeRules.payout(Material.IRON_INGOT, Integer.MAX_VALUE));
    }
    @Test void acceptsExactlyTheRequestedResources() {
        assertTrue(ExchangeRules.accepts(Material.COPPER_INGOT));
        assertTrue(ExchangeRules.accepts(Material.IRON_INGOT));
        assertTrue(ExchangeRules.accepts(Material.GOLD_INGOT));
        assertTrue(ExchangeRules.accepts(Material.DIAMOND));
        assertTrue(ExchangeRules.accepts(Material.EMERALD));
        assertEquals(6, ExchangeRules.ACCEPTED.size());
        assertTrue(ExchangeRules.accepts(Material.NETHERITE_INGOT));
        assertFalse(ExchangeRules.accepts(Material.PAPER));
        assertFalse(ExchangeRules.accepts(Material.GOLD_BLOCK));
    }
}
