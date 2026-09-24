package de.casino;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PayTest {
    @Test void parsesExactCents() {
        assertEquals(300, Money.parsePositive("3"));
        assertEquals(50, Money.parsePositive("0,50"));
        assertEquals(50, Money.parsePositive("0.50"));
        assertEquals(1, Money.parsePositive("0.01"));
        for (String value : new String[]{"0", "-3", "1.001", "NaN", "1e3", "1,000.00"})
            assertThrows(IllegalArgumentException.class, () -> Money.parsePositive(value));
        assertThrows(ArithmeticException.class, () -> Money.parsePositive("999999999999999999999999"));
    }
    @Test void transfersPersistTogetherAndRejectInvalidPayments(@TempDir Path directory) throws Exception {
        UUID from = UUID.randomUUID(), to = UUID.randomUUID();
        Path file = directory.resolve("accounts.yml");
        Files.writeString(file, "currency-unit: cents\n" + from + ":\n  balance: 1000\n" + to + ":\n  balance: 200\n");
        Accounts accounts = new Accounts(directory);
        accounts.transfer(from, to, 300);
        var data = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(700, data.getLong(from + ".balance"));
        assertEquals(500, data.getLong(to + ".balance"));
        String saved = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> accounts.transfer(from, to, 701));
        assertThrows(IllegalArgumentException.class, () -> accounts.transfer(from, from, 100));
        assertThrows(IllegalArgumentException.class, () -> accounts.transfer(from, to, -100));
        assertEquals(saved, Files.readString(file));
        new Accounts(directory).transfer(to, from, 50);
        assertEquals(750, YamlConfiguration.loadConfiguration(file.toFile()).getLong(from + ".balance"));
    }
    @Test void failedSaveRollsBackBothBalances(@TempDir Path directory) throws Exception {
        UUID from = UUID.randomUUID(), to = UUID.randomUUID();
        Path file = directory.resolve("accounts.yml");
        Files.writeString(file, "currency-unit: cents\n" + from + ":\n  balance: 1000\n" + to + ":\n  balance: 200\n");
        Accounts accounts = new Accounts(directory);
        Path blocker = Files.createDirectory(directory.resolve("accounts.yml.tmp"));
        assertThrows(java.io.IOException.class, () -> accounts.transfer(from, to, 300));
        Files.delete(blocker);
        accounts.transfer(from, to, 300);
        var data = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(700, data.getLong(from + ".balance"));
        assertEquals(500, data.getLong(to + ".balance"));
    }
}
