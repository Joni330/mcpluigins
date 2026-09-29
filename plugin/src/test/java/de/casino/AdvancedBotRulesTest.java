package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import de.casino.MiningBotVeins.Pos;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedBotRulesTest {
    @Test void distantDiagonalOreIsReachedWithoutFloodingTheWholeSearchCube(){
        Pos start=new Pos(0,0,0),goal=new Pos(8,8,8);int[] checked={0};
        List<Pos> path=AdvancedBotRules.path(start,goal,p->{checked[0]++;return true;});
        assertEquals(24,path.size());assertEquals(goal,path.getLast());assertTrue(checked[0]<500);
    }
    @Test void seekerCanTakeWiderDetourAroundLongWall(){
        Pos start=new Pos(0,0,0),goal=new Pos(2,0,0);
        List<Pos> path=AdvancedBotRules.path(start,goal,p->p.y()==0&&!(p.x()==1&&Math.abs(p.z())<=10));
        assertFalse(path.isEmpty());assertEquals(goal,path.getLast());
        assertTrue(path.stream().anyMatch(p->Math.abs(p.z())==11));
    }
    @Test void partialLayerResumesInsideSameLayerAndIndexRoundTrips(){
        assertEquals(256,AdvancedBotRules.layerEnd(0,1000));assertEquals(256,AdvancedBotRules.layerEnd(250,1000));
        assertEquals(512,AdvancedBotRules.layerEnd(256,1000));assertEquals(1000,AdvancedBotRules.layerEnd(900,1000));
        for(int i=0;i<1024;i++)assertEquals(i,AdvancedBotRules.quarryIndex(70,AdvancedBotRules.quarry(-3,-2,70,i)));
    }
    @Test void quarryVisitsEveryBlockOfNegativeChunkOnceFromTopToBottom(){
        Set<Pos> visited=new HashSet<>();int top=5,bottom=-3,total=AdvancedBotRules.volume(top,bottom);
        assertEquals(9*256,total);
        for(int i=0;i<total;i++){
            Pos p=AdvancedBotRules.quarry(-2,-1,top,i);assertTrue(visited.add(p));
            assertEquals(-2,p.x()>>4);assertEquals(-1,p.z()>>4);assertEquals(top-i/256,p.y());
        }
        assertEquals(total,visited.size());assertEquals(bottom,AdvancedBotRules.quarry(-2,-1,top,total-1).y());
    }
    @Test void quarrySnakeNeverSkipsAnEdgeOrRepeatedCellWithinLayer(){
        for(int i=1;i<256;i++)assertEquals(1,AdvancedBotRules.distance(AdvancedBotRules.quarry(0,0,64,i-1),AdvancedBotRules.quarry(0,0,64,i)));
    }
    @Test void seekerRoutesAroundWallWithoutDiagonalJumps(){
        Pos start=new Pos(0,64,0),goal=new Pos(3,64,0);
        List<Pos> path=AdvancedBotRules.path(start,goal,p->p.y()==64&&!(p.x()==1&&p.z()==0));
        assertFalse(path.isEmpty());assertEquals(goal,path.getLast());assertFalse(path.contains(new Pos(1,64,0)));
        Pos previous=start;for(Pos p:path){assertEquals(1,AdvancedBotRules.distance(previous,p));previous=p;}
    }
    @Test void seekerCanClimbAndDescendButDoesNotCrossClosedRegions(){
        Pos start=new Pos(0,0,0),goal=new Pos(0,-4,0);
        List<Pos> path=AdvancedBotRules.path(start,goal,p->p.x()==0&&p.z()==0);
        assertEquals(4,path.size());assertEquals(goal,path.getLast());
        assertTrue(AdvancedBotRules.path(start,new Pos(3,0,0),p->false).isEmpty());
        assertTrue(AdvancedBotRules.path(start,new Pos(30,0,0),p->true).isEmpty());
    }
    @Test void blockingStatusesAreDistinguishedFromOrdinaryProgress(){
        assertTrue(BotAlerts.blocked("Pause · Axt fehlt"));assertTrue(BotAlerts.blocked("Ausgabekiste voll"));
        assertTrue(BotAlerts.blocked("Warte auf Kiste"));assertFalse(BotAlerts.blocked("Hebe Chunk aus · Ebene 30"));
    }
}
