package de.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageUpgradeTest {
    private Accounts seed(Path dir,UUID player,long cents)throws Exception{
        Files.writeString(dir.resolve("accounts.yml"),"currency-unit: cents\nwallet-version: 1\n"+player+":\n  balance: "+cents+"\n  casino-balance: 900000\n");
        return new Accounts(dir);
    }
    @Test void everyPageCostsTwoHundredAndSurvivesAnUnsavedTerminalChunk(@TempDir Path dir)throws Exception{
        UUID player=UUID.randomUUID(),storage=UUID.randomUUID();Accounts accounts=seed(dir,player,100000);
        for(int expected=10;expected<15;expected++){
            // The world may still have its original ten-page payload after a crash.
            accounts.upgradeStorage(player,storage,10,expected);
            accounts=new Accounts(dir);assertEquals(expected+1,accounts.storagePages(storage,10));
        }
        assertEquals(5,accounts.history(player).size());
        for(var booking:accounts.history(player)){assertEquals(-20000,booking.delta());assertEquals("Hauptkonto",booking.wallet());}
        Accounts finalAccounts=accounts;
        assertThrows(IllegalArgumentException.class,()->finalAccounts.upgradeStorage(player,storage,10,15));
        assertEquals(15,accounts.storagePages(storage,10));
        var yaml=YamlConfiguration.loadConfiguration(dir.resolve("accounts.yml").toFile());
        assertEquals(0,yaml.getLong(player+".balance"));assertEquals(900000,yaml.getLong(player+".casino-balance"));
        assertEquals(10,accounts.storagePages(UUID.randomUUID(),10));
    }
    @Test void teamPurchasesShareCapacityAndRejectStaleScreens(@TempDir Path dir)throws Exception{
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),storage=UUID.randomUUID();seed(dir,first,40000);
        Files.writeString(dir.resolve("accounts.yml"),second+":\n  balance: 60000\n  casino-balance: 0\n",StandardOpenOption.APPEND);
        Accounts accounts=new Accounts(dir);accounts.upgradeStorage(first,storage,10,10);
        assertThrows(IllegalArgumentException.class,()->accounts.upgradeStorage(second,storage,10,10));
        assertTrue(accounts.history(second).isEmpty());
        accounts.upgradeStorage(second,storage,11,11);assertEquals(12,accounts.storagePages(storage,10));
        assertEquals(1,accounts.history(first).size());assertEquals(1,accounts.history(second).size());
        assertEquals(20000,accounts.history(first).getFirst().balance());assertEquals(40000,accounts.history(second).getFirst().balance());
    }
    @Test void failedSaveRollsBackPaymentEntitlementAndHistory(@TempDir Path dir)throws Exception{
        UUID player=UUID.randomUUID(),storage=UUID.randomUUID();Accounts accounts=seed(dir,player,20000);
        Path blocker=Files.createDirectory(dir.resolve("accounts.yml.tmp"));
        assertThrows(java.io.IOException.class,()->accounts.upgradeStorage(player,storage,10,10));
        assertEquals(10,accounts.storagePages(storage,10));assertTrue(accounts.history(player).isEmpty());
        assertEquals(10,new Accounts(dir).storagePages(storage,10));
        Files.delete(blocker);accounts.upgradeStorage(player,storage,10,10);
        assertEquals(11,new Accounts(dir).storagePages(storage,10));assertEquals(0,accounts.history(player).getFirst().balance());
    }
    @Test void movedUpgradedTerminalKeepsItsBaseCapacity(@TempDir Path dir)throws Exception{
        UUID player=UUID.randomUUID(),newTerminal=UUID.randomUUID();Accounts accounts=seed(dir,player,20000);
        // A dismantled empty terminal carries its pages on the item, but receives a fresh identity.
        assertEquals(14,accounts.storagePages(newTerminal,14));
        accounts.upgradeStorage(player,newTerminal,14,14);
        assertEquals(15,new Accounts(dir).storagePages(newTerminal,14));
    }
}
