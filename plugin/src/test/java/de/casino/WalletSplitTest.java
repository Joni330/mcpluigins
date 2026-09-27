package de.casino;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class WalletSplitTest {
 private void seed(Path dir, UUID id, long amount) throws Exception {
  Files.writeString(dir.resolve("accounts.yml"),"currency-unit: cents\n"+id+":\n  balance: "+amount+"\n");
 }
 private long value(Path dir, UUID id, String field) {
  return YamlConfiguration.loadConfiguration(dir.resolve("accounts.yml").toFile()).getLong(id+"."+field);
 }
 @Test void migrationPreservesMainAndDoesNotResetCasinoOnRestart(@TempDir Path dir) throws Exception {
  UUID id=UUID.randomUUID(); seed(dir,id,12345);
  Accounts a=new Accounts(dir);
  assertEquals(12345,value(dir,id,"balance")); assertEquals(0,value(dir,id,"casino-balance"));
  try(var files=Files.list(dir)) { assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("accounts-before-wallets-"))); }
  a.move(id,345,true); new Accounts(dir);
  assertEquals(12000,value(dir,id,"balance")); assertEquals(345,value(dir,id,"casino-balance"));
 }
 @Test void twoWalletEntriesRollbackTogetherWhenSavingFails(@TempDir Path dir) throws Exception {
  UUID id=UUID.randomUUID(); seed(dir,id,1000); Accounts a=new Accounts(dir);
  Path blocker=Files.createDirectory(dir.resolve("accounts.yml.tmp"));
  assertThrows(java.io.IOException.class,()->a.move(id,1000,true)); assertTrue(a.history(id).isEmpty());
  Files.delete(blocker); a.move(id,1000,true);
  assertEquals(2,a.history(id).size());
  assertEquals("Casino",a.history(id).get(0).wallet()); assertEquals("Hauptkonto",a.history(id).get(1).wallet());
  assertEquals(0,value(dir,id,"balance")); assertEquals(1000,value(dir,id,"casino-balance"));
 }
 @Test void casinoPurchasesAndRedemptionStaySeparate(@TempDir Path dir) throws Exception {
  UUID id=UUID.randomUUID(); seed(dir,id,2000); Accounts a=new Accounts(dir);
  assertThrows(IllegalArgumentException.class,()->a.changeCasino(id,-100,"Chips"));
  a.move(id,1000,true); a.changeCasino(id,-100,"Chips gekauft"); a.changeCasino(id,100,"Chips eingelöst");
  a.move(id,1,false);
  assertEquals(1001,value(dir,id,"balance")); assertEquals(999,value(dir,id,"casino-balance"));
  a.debit(id,100,"Itemshop"); assertEquals(901,value(dir,id,"balance")); assertEquals(999,value(dir,id,"casino-balance"));
 }
 @Test void insufficientFundsAndOverflowLeaveBothUntouched(@TempDir Path dir) throws Exception {
  UUID id=UUID.randomUUID(); seed(dir,id,1000); Accounts a=new Accounts(dir);
  assertThrows(IllegalArgumentException.class,()->a.move(id,1001,true));
  a.changeCasino(id,Long.MAX_VALUE,"Test");
  assertThrows(ArithmeticException.class,()->a.move(id,1,true));
  assertEquals(1000,value(dir,id,"balance")); assertEquals(Long.MAX_VALUE,value(dir,id,"casino-balance"));
  assertEquals(1,a.history(id).size());
 }
}
