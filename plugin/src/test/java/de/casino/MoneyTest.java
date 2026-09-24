package de.casino;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void pricesAndFormatting() {
        assertEquals(50, ExchangeRules.payout(Material.COPPER_INGOT, 1));
        assertEquals(200, ExchangeRules.payout(Material.IRON_INGOT, 1));
        assertEquals(300, ExchangeRules.payout(Material.GOLD_INGOT, 1));
        assertEquals(500, ExchangeRules.payout(Material.DIAMOND, 1));
        assertEquals(500, ExchangeRules.payout(Material.EMERALD, 1));
        assertEquals(10000, ExchangeRules.payout(Material.NETHERITE_INGOT, 1));
        assertEquals("0,50€", Money.format(50));
        assertEquals("2,00€", Money.format(200));
        assertEquals("0,10€", Money.format(10));
        assertEquals("100,00€", Money.format(10000));
        assertEquals("0,00€", Money.format(0));
    }
    @Test void futureSpinLimits() {
        assertFalse(Money.validSpin(9));
        assertTrue(Money.validSpin(10));
        assertTrue(Money.validSpin(1000));
        assertFalse(Money.validSpin(1001));
    }
    @Test void migratesEurosOnlyOnceAndBacksUpOriginal(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("accounts.yml");
        String original = "test-player:\n  balance: 123\n  name: Test\n";
        Files.writeString(file, original);
        new Accounts(directory);
        var data = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(12300L, data.getLong("test-player.balance"));
        assertEquals("cents", data.getString("currency-unit"));
        new Accounts(directory);
        assertEquals(12300L, YamlConfiguration.loadConfiguration(file.toFile()).getLong("test-player.balance"));
        try (var files = Files.list(directory)) {
            var backups = files.filter(p -> p.getFileName().toString().startsWith("accounts-before-cents-")).toList();
            assertEquals(1, backups.size());
            assertEquals(original, Files.readString(backups.getFirst()));
        }
    }
}
