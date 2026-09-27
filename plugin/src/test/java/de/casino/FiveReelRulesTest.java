package de.casino;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class FiveReelRulesTest {
 @Test void exactOddsAndExpectedReturn() {
  long total=0; int winners=0;
  for(int ticket=0;ticket<10000;ticket++) {
   var outcome=FiveReelRules.ticket(ticket);
   if(outcome.matches()==0) continue;
   winners++;
   int[] line={0,1,2,3,4};
   for(int i=0;i<outcome.matches();i++) line[i]=outcome.symbol();
   if(outcome.matches()<5) line[outcome.matches()]=(outcome.symbol()+1)%7;
   total+=FiveReelRules.payout(100,line);
  }
  assertEquals(3835,winners); assertEquals(899000,total);
 }
 @Test void tableAndLeftToRightOnly() {
  int[] half={1,2,3,4,5,6,10};
  for(int s=0;s<7;s++) for(int count=3;count<=5;count++) {
   int[] line={s,s,s,s,s}; if(count<5) line[count]=(s+1)%7;
   assertEquals(100L*half[s]*(1L<<(count-3))/2,FiveReelRules.payout(100,line));
  }
  assertEquals(0,FiveReelRules.payout(100,new int[]{1,2,2,2,2}));
  assertEquals(0,FiveReelRules.payout(100,new int[]{2,2,1,2,2}));
 }
 @Test void shownLineAlwaysMatchesSettlement() {
  Random random=new Random(723);
  for(int i=0;i<10000;i++) {
   var play=FiveReelRules.draw(1000,random);
   assertEquals(FiveReelRules.payout(1000,play.middle()),play.payout());
  }
 }
 @Test void onlyWholeEurosAndBounds() {
  assertThrows(IllegalArgumentException.class,()->FiveReelRules.payout(150,new int[]{0,0,0,0,0}));
  assertThrows(IllegalArgumentException.class,()->FiveReelRules.payout(0,new int[]{0,0,0,0,0}));
  assertThrows(IllegalArgumentException.class,()->FiveReelRules.payout(1100,new int[]{0,0,0,0,0}));
  assertEquals(100,FiveReelMenu.adjustBet(100,false));
  assertEquals(1000,FiveReelMenu.adjustBet(1000,true));
  assertEquals(200,FiveReelMenu.adjustBet(100,true));
 }
}
