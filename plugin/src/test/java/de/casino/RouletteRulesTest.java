package de.casino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RouletteRulesTest {
 @Test void europeanDistribution() {
  int[] counts=new int[3];
  for(int n=0;n<=36;n++) counts[RouletteRules.color(n).ordinal()]++;
  assertArrayEquals(new int[]{18,18,1},counts);
  assertEquals(RouletteRules.Color.GREEN,RouletteRules.color(0));
 }
 @Test void allOutcomesAndLimits() {
  for(long bet:new long[]{10,1000}) for(var selected:RouletteRules.Color.values()) for(int n=0;n<=36;n++)
   assertEquals(RouletteRules.color(n)==selected?bet*(selected==RouletteRules.Color.GREEN?36:2):0,RouletteRules.payout(bet,selected,n));
  assertThrows(IllegalArgumentException.class,()->RouletteRules.payout(9,RouletteRules.Color.RED,1));
  assertThrows(IllegalArgumentException.class,()->RouletteRules.payout(1001,RouletteRules.Color.RED,1));
  assertThrows(IllegalArgumentException.class,()->RouletteRules.color(37));
 }
}
