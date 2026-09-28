package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BackpackUpgradeTest {
    private Accounts seed(Path dir, UUID id) throws Exception {
        Files.writeString(dir.resolve("accounts.yml"), "currency-unit: cents\nwallet-version: 1\n" + id + ":\n  balance: 100000\n  casino-balance: 500\n");
        return new Accounts(dir);
    }
    @Test void upgradePersistsAndCannotBeBoughtTwice(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID(); Accounts accounts = seed(dir, id);
        assertEquals(1, accounts.backpackRows(id));
        accounts.upgradeBackpack(id, 1, 100000);
        assertEquals(2, new Accounts(dir).backpackRows(id));
        assertThrows(IllegalArgumentException.class, () -> accounts.upgradeBackpack(id, 1, 100000));
        assertThrows(IllegalArgumentException.class, () -> accounts.upgradeBackpack(id, 2, 1));
        assertEquals(1, accounts.history(id).size());
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dir.resolve("accounts.yml").toFile());
        assertEquals(0, yaml.getLong(id + ".balance"));
        assertEquals(500, yaml.getLong(id + ".casino-balance"));
    }
    @Test void saveFailureRollsBackMoneyRowsAndHistory(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID(); Accounts accounts = seed(dir, id);
        Path blocker = Files.createDirectory(dir.resolve("accounts.yml.tmp"));
        assertThrows(java.io.IOException.class, () -> accounts.upgradeBackpack(id, 1, 100000));
        assertEquals(1, accounts.backpackRows(id)); assertTrue(accounts.history(id).isEmpty());
        Files.delete(blocker);
        accounts.upgradeBackpack(id, 1, 100000);
        assertEquals(2, accounts.backpackRows(id));
    }
    @Test void stopsAtSixRowsAndKeepsPlayersSeparate(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID(); Accounts accounts = seed(dir, id);
        for (int row = 1; row < 6; row++) accounts.upgradeBackpack(id, row, 1);
        assertThrows(IllegalArgumentException.class, () -> accounts.upgradeBackpack(id, 6, 1));
        assertEquals(6, accounts.backpackRows(id)); assertEquals(1, accounts.backpackRows(UUID.randomUUID()));
    }
}
