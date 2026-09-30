package de.casino;

import java.util.*;

final class StorageCategories {
    static LinkedHashMap<String,String> defaults(){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        result.put("Alle", "*");result.put("Rüstungen", "@armor");result.put("Erze", "@ores");result.put("Baublöcke", "@blocks");return result;
    }
    static boolean matches(String type,boolean block,String name,String filter){
        return switch(filter){
            case "*" -> true;
            case "@armor" -> type.endsWith("_HELMET")||type.endsWith("_CHESTPLATE")||type.endsWith("_LEGGINGS")||type.endsWith("_BOOTS")||type.equals("ELYTRA");
            case "@ores" -> type.endsWith("_ORE")||type.endsWith("_INGOT")||type.startsWith("RAW_")||Set.of("DIAMOND","EMERALD","COAL","REDSTONE","LAPIS_LAZULI","QUARTZ","ANCIENT_DEBRIS","NETHERITE_SCRAP").contains(type);
            case "@blocks" -> block;
            default -> Arrays.stream(filter.split(",")).map(String::strip).filter(s->!s.isEmpty()).anyMatch(s->StorageSearch.matches(type,name,s));
        };
    }
}
