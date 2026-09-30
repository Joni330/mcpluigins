package de.casino;

import java.util.*;
import de.casino.MiningBotVeins.Pos;

final class LumberRules {
    static final Set<String> SAPLINGS=Set.of("OAK_SAPLING","BIRCH_SAPLING","SPRUCE_SAPLING","JUNGLE_SAPLING","ACACIA_SAPLING","DARK_OAK_SAPLING","CHERRY_SAPLING","PALE_OAK_SAPLING","MANGROVE_PROPAGULE");
    private static final Set<String> SOILS=Set.of("DIRT","GRASS_BLOCK","PODZOL","COARSE_DIRT","ROOTED_DIRT","MOSS_BLOCK","MUD");
    static int width(String type){return type.equals("DARK_OAK_SAPLING")||type.equals("PALE_OAK_SAPLING")?2:1;}
    static List<Pos> footprint(Pos root,String type){
        List<Pos> result=new ArrayList<>();for(int x=0;x<width(type);x++)for(int z=0;z<width(type);z++)result.add(new Pos(root.x()+x,root.y(),root.z()+z));return result;
    }
    static boolean inChunk(Pos pos,int x,int z){return (pos.x()>>4)==x&&(pos.z()>>4)==z;}
    // Planting stays in the work chunk; a tree's own crown may reach the eight neighbours.
    static boolean inTreeArea(Pos pos,int chunkX,int chunkZ){
        return Math.abs((long)(pos.x()>>4)-chunkX)<=1&&Math.abs((long)(pos.z()>>4)-chunkZ)<=1;
    }
    static boolean trackedTreePart(Pos pos,String material,int chunkX,int chunkZ,Map<Pos,String> tracked){
        return inTreeArea(pos,chunkX,chunkZ)&&treePart(material)&&material.equals(tracked.get(pos));
    }
    static Set<PluginChunks.Key> treeChunks(UUID world,int chunkX,int chunkZ){
        return PluginChunks.area(world,(chunkX<<4)+8,(chunkZ<<4)+8,16);
    }
    static List<Pos> plots(int chunkX,int chunkZ,int y,String type){
        boolean wide=width(type)==2||type.equals("ACACIA_SAPLING")||type.equals("CHERRY_SAPLING")||type.equals("MANGROVE_PROPAGULE");
        int[] axis=wide?new int[]{3,7,11}:new int[]{3,6,9,12};
        List<Pos> result=new ArrayList<>();for(int x:axis)for(int z:axis)result.add(new Pos((chunkX<<4)+x,y,(chunkZ<<4)+z));return result;
    }
    static boolean separated(Pos root,String type,Map<Pos,String> growing){
        // Compare complete trunk footprints, including old layouts and mixed tree species.
        for(Pos cell:footprint(root,type))for(var other:growing.entrySet())for(Pos occupied:footprint(other.getKey(),other.getValue()))
            if(Math.max(Math.abs(cell.x()-occupied.x()),Math.abs(cell.z()-occupied.z()))<3)return false;
        return true;
    }
    static boolean treePart(String material){return material.endsWith("_LOG")||material.endsWith("_LEAVES")||material.equals("MANGROVE_ROOTS")||material.equals("MUDDY_MANGROVE_ROOTS");}
    static boolean treeDrop(String material){return SAPLINGS.contains(material)||treePart(material)||material.equals("STICK")||material.equals("APPLE");}
    static boolean soil(String material){return SOILS.contains(material);}
}
