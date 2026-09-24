package de.casino;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChipPurchaseTest {
    @Test void debitPersistsAndRejectsOverdraft(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = dir.resolve("accounts.yml");
        Files.writeString(file, "currency-unit: cents\n" + id + ":\n  balance: 6000\n  last-payout: 300\n");
        Accounts accounts = new Accounts(dir);
        accounts.debit(id, 5000);
        var saved = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(1000, saved.getLong(id + ".balance"));
        assertEquals(300, saved.getLong(id + ".last-payout"));
        assertThrows(IllegalArgumentException.class, () -> accounts.debit(id, 1001));
        assertThrows(IllegalArgumentException.class, () -> accounts.debit(id, 0));
        assertThrows(IllegalArgumentException.class, () -> accounts.debit(id, -100));
        new Accounts(dir).debit(id, 1000);
        assertEquals(0, YamlConfiguration.loadConfiguration(file.toFile()).getLong(id + ".balance"));
    }
    @Test void debitRollsBackOnSaveFailure(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = dir.resolve("accounts.yml");
        Files.writeString(file, "currency-unit: cents\n" + id + ":\n  balance: 5000\n");
        Accounts accounts = new Accounts(dir);
        Path blocker = Files.createDirectory(dir.resolve("accounts.yml.tmp"));
        assertThrows(java.io.IOException.class, () -> accounts.debit(id, 5000));
        Files.delete(blocker);
        accounts.debit(id, 5000);
        assertEquals(0, YamlConfiguration.loadConfiguration(file.toFile()).getLong(id + ".balance"));
    }
}
