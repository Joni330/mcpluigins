package de.casino;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AccountHistoryTest {
 @Test void transfersPersistBothSides(@TempDir Path dir) throws Exception {
  UUID a=UUID.randomUUID(), b=UUID.randomUUID();
  Files.writeString(dir.resolve("accounts.yml"),"currency-unit: cents\n"+a+":\n  balance: 1000\n  name: Joni\n"+b+":\n  balance: 0\n  name: Milo\n");
  Accounts accounts=new Accounts(dir);
  accounts.transfer(a,b,300);
  accounts=new Accounts(dir);
  assertEquals(-300,accounts.history(a).get(0).delta());
  assertEquals(700,accounts.history(a).get(0).balance());
  assertEquals("Überweisung an Milo",accounts.history(a).get(0).reason());
  assertEquals(300,accounts.history(b).get(0).delta());
 }
 @Test void failureRollsBackHistoryTogetherWithBalances(@TempDir Path dir) throws Exception {
  UUID a=UUID.randomUUID(),b=UUID.randomUUID();
  Files.writeString(dir.resolve("accounts.yml"),"currency-unit: cents\n"+a+":\n  balance: 1000\n"+b+":\n  balance: 0\n");
  Accounts accounts=new Accounts(dir);
  Path blocker=Files.createDirectory(dir.resolve("accounts.yml.tmp"));
  assertThrows(java.io.IOException.class,()->accounts.transfer(a,b,300));
  assertTrue(accounts.history(a).isEmpty()); assertTrue(accounts.history(b).isEmpty());
  Files.delete(blocker); accounts.transfer(a,b,1000);
  assertEquals(1,accounts.history(a).size());
  assertEquals(1000,accounts.history(b).get(0).balance());
 }
 @Test void retainsNewest200AndNoRejectedBookings(@TempDir Path dir) throws Exception {
  UUID id=UUID.randomUUID();
  Files.writeString(dir.resolve("accounts.yml"),"currency-unit: cents\n"+id+":\n  balance: 300\n");
  Accounts accounts=new Accounts(dir);
  for(int i=0;i<205;i++) accounts.debit(id,1,"Kauf "+i);
  assertEquals(200,accounts.history(id).size());
  assertEquals("Kauf 204",accounts.history(id).get(0).reason());
  assertEquals("Kauf 5",accounts.history(id).get(199).reason());
  assertThrows(IllegalArgumentException.class,()->accounts.debit(id,1000,"Fehlgeschlagen"));
  assertEquals(200,new Accounts(dir).history(id).size());
 }
}
