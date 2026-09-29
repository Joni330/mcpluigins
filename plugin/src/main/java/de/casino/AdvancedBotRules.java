package de.casino;

import java.util.*;
import java.util.function.Predicate;
import de.casino.MiningBotVeins.Pos;

final class AdvancedBotRules {
    static Pos quarry(int chunkX,int chunkZ,int top,int cursor) {
        int layer=cursor/256, cell=cursor%256, z=cell/16, x=cell%16;
        if ((z&1)==1) x=15-x;
        return new Pos((chunkX<<4)+x,top-layer,(chunkZ<<4)+z);
    }
    static int volume(int top,int bottom) { return Math.max(0,top-bottom+1)*256; }
    static int layerEnd(int cursor,int total) { return Math.min(total,(cursor/256+1)*256); }
    static int quarryIndex(int top,Pos position) {
        int z=Math.floorMod(position.z(),16),x=Math.floorMod(position.x(),16);
        return (top-position.y())*256+z*16+((z&1)==1?15-x:x);
    }
    static List<Pos> path(Pos start,Pos goal,Predicate<Pos> allowed) {
        if(start.equals(goal)||distanceOutside(start,goal)||!allowed.test(goal))return List.of();
        record Step(Pos position,int cost,int estimate) {}
        PriorityQueue<Step> queue=new PriorityQueue<>(Comparator.comparingInt(Step::estimate).thenComparingInt(s->distance(s.position(),goal)));
        Map<Pos,Integer> costs=new HashMap<>();Map<Pos,Pos> previous=new HashMap<>();
        queue.add(new Step(start,0,distance(start,goal)));costs.put(start,0);
        int expanded=0;
        while(!queue.isEmpty()&&expanded<6000){
            Step step=queue.remove();Pos current=step.position();
            if(step.cost()!=costs.getOrDefault(current,Integer.MAX_VALUE))continue;
            if(current.equals(goal)){
                LinkedList<Pos> result=new LinkedList<>();
                for(Pos p=goal;!p.equals(start);p=previous.get(p))result.addFirst(p);
                return result;
            }
            expanded++;
            for(Pos next:current.neighbours()){
                int cost=step.cost()+1;
                if(distanceOutside(start,next)||cost>=costs.getOrDefault(next,Integer.MAX_VALUE)||!allowed.test(next))continue;
                costs.put(next,cost);previous.put(next,current);queue.add(new Step(next,cost,cost+distance(next,goal)));
            }
        }
        return List.of();
    }
    private static boolean distanceOutside(Pos start,Pos pos){
        return Math.abs(pos.x()-start.x())>12||Math.abs(pos.y()-start.y())>12||Math.abs(pos.z()-start.z())>12;
    }
    static int distance(Pos a,Pos b) { return Math.abs(a.x()-b.x())+Math.abs(a.y()-b.y())+Math.abs(a.z()-b.z()); }
}
