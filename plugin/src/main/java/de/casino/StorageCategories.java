package de.casino;

import java.util.*;

final class StorageCategories {
    private static final Set<String> WOODS=Set.of("OAK","SPRUCE","BIRCH","JUNGLE","ACACIA","DARK_OAK","MANGROVE","CHERRY","PALE_OAK","CRIMSON","WARPED","BAMBOO");
    private static final Set<String> FOOD=Set.of("APPLE","GOLDEN_APPLE","ENCHANTED_GOLDEN_APPLE","BREAD","CARROT","GOLDEN_CARROT","POTATO","BAKED_POTATO","POISONOUS_POTATO","BEETROOT","BEETROOT_SOUP","MUSHROOM_STEW","RABBIT_STEW","SUSPICIOUS_STEW","BEEF","COOKED_BEEF","PORKCHOP","COOKED_PORKCHOP","CHICKEN","COOKED_CHICKEN","MUTTON","COOKED_MUTTON","RABBIT","COOKED_RABBIT","COD","COOKED_COD","SALMON","COOKED_SALMON","TROPICAL_FISH","PUFFERFISH","COOKIE","CAKE","PUMPKIN_PIE","MELON_SLICE","DRIED_KELP","SWEET_BERRIES","GLOW_BERRIES","HONEY_BOTTLE","CHORUS_FRUIT","ROTTEN_FLESH","SPIDER_EYE");
    private static final Set<String> PLANTS=Set.of("WHEAT","CARROT","POTATO","BEETROOT","SUGAR_CANE","BAMBOO","CACTUS","KELP","DRIED_KELP","COCOA_BEANS","NETHER_WART","MANGROVE_PROPAGULE","VINE","LILY_PAD","MOSS_BLOCK","MOSS_CARPET","PALE_MOSS_BLOCK","PALE_MOSS_CARPET","PALE_HANGING_MOSS","DANDELION","POPPY","BLUE_ORCHID","ALLIUM","AZURE_BLUET","OXEYE_DAISY","CORNFLOWER","LILY_OF_THE_VALLEY","WITHER_ROSE","SUNFLOWER","LILAC","ROSE_BUSH","PEONY","PINK_PETALS","SPORE_BLOSSOM","TORCHFLOWER","PITCHER_PLANT","AZALEA","FLOWERING_AZALEA","SHORT_GRASS","TALL_GRASS","FERN","LARGE_FERN","DEAD_BUSH","SEAGRASS","SEA_PICKLE","SWEET_BERRIES","GLOW_BERRIES","PUMPKIN","MELON");
    private static final Set<String> REDSTONE=Set.of("REDSTONE","REDSTONE_BLOCK","REDSTONE_TORCH","REPEATER","COMPARATOR","LEVER","PISTON","STICKY_PISTON","OBSERVER","DISPENSER","DROPPER","HOPPER","CRAFTER","TARGET","TRIPWIRE_HOOK","DAYLIGHT_DETECTOR","REDSTONE_LAMP","NOTE_BLOCK","TNT","RAIL","POWERED_RAIL","DETECTOR_RAIL","ACTIVATOR_RAIL","SCULK_SENSOR","CALIBRATED_SCULK_SENSOR","LIGHTNING_ROD");
    private static final Set<String> MOB_DROPS=Set.of("ROTTEN_FLESH","BONE","BONE_MEAL","STRING","SPIDER_EYE","FERMENTED_SPIDER_EYE","GUNPOWDER","ENDER_PEARL","BLAZE_ROD","BLAZE_POWDER","BREEZE_ROD","GHAST_TEAR","SLIME_BALL","MAGMA_CREAM","LEATHER","RABBIT_HIDE","RABBIT_FOOT","FEATHER","INK_SAC","GLOW_INK_SAC","PRISMARINE_SHARD","PRISMARINE_CRYSTALS","PHANTOM_MEMBRANE","NAUTILUS_SHELL","SHULKER_SHELL","ARMADILLO_SCUTE","TURTLE_SCUTE","NETHER_STAR","TOTEM_OF_UNDYING");
    static LinkedHashMap<String,String> defaults(){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        result.put("Alle", "*");result.put("Rüstungen", "@armor");result.put("Erze", "@ores");result.put("Holz", "@wood");
        result.put("Baublöcke", "@blocks");result.put("Werkzeuge", "@tools");result.put("Waffen", "@weapons");
        result.put("Nahrung", "@food");result.put("Pflanzen", "@plants");result.put("Redstone", "@redstone");result.put("Mob-Drops", "@mobdrops");return result;
    }
    static void addMissingDefaults(Map<String,String> categories){
        defaults().forEach(categories::putIfAbsent);
    }
    private static boolean wood(String type){
        String unstripped=type.startsWith("STRIPPED_")?type.substring(9):type;
        return (WOODS.stream().anyMatch(w->unstripped.startsWith(w+"_"))
                &&!unstripped.endsWith("_LEAVES")&&!unstripped.endsWith("_SAPLING")&&!unstripped.endsWith("_PROPAGULE"))
                ||Set.of("STICK","BOWL","LADDER","CRAFTING_TABLE","CHEST","TRAPPED_CHEST","BARREL").contains(type);
    }
    static boolean matches(String type,boolean block,String name,String filter){
        return switch(filter){
            case "*" -> true;
            case "@armor" -> type.endsWith("_HELMET")||type.endsWith("_CHESTPLATE")||type.endsWith("_LEGGINGS")||type.endsWith("_BOOTS")||type.equals("ELYTRA");
            case "@ores" -> type.endsWith("_ORE")||type.endsWith("_INGOT")||type.endsWith("_NUGGET")||type.startsWith("RAW_")||Set.of("DIAMOND","EMERALD","COAL","REDSTONE","LAPIS_LAZULI","QUARTZ","ANCIENT_DEBRIS","NETHERITE_SCRAP","AMETHYST_SHARD","IRON_BLOCK","GOLD_BLOCK","COPPER_BLOCK","DIAMOND_BLOCK","EMERALD_BLOCK","COAL_BLOCK","REDSTONE_BLOCK","LAPIS_BLOCK","NETHERITE_BLOCK").contains(type);
            case "@wood" -> wood(type);
            case "@tools" -> type.endsWith("_PICKAXE")||type.endsWith("_AXE")||type.endsWith("_SHOVEL")||type.endsWith("_HOE")||Set.of("SHEARS","FLINT_AND_STEEL","FISHING_ROD","BRUSH","CLOCK","COMPASS","RECOVERY_COMPASS","SPYGLASS").contains(type);
            case "@weapons" -> type.endsWith("_SWORD")||type.endsWith("_SPEAR")||Set.of("BOW","CROSSBOW","TRIDENT","MACE","SHIELD","ARROW","SPECTRAL_ARROW","TIPPED_ARROW").contains(type);
            case "@food" -> FOOD.contains(type);
            case "@plants" -> PLANTS.contains(type)||type.endsWith("_SAPLING")||type.endsWith("_LEAVES")||type.endsWith("_SEEDS")||type.endsWith("_TULIP")||type.endsWith("_MUSHROOM")||type.endsWith("_FUNGUS")||type.endsWith("_ROOTS")||type.endsWith("_VINES");
            case "@redstone" -> REDSTONE.contains(type)||type.endsWith("_BUTTON")||type.endsWith("_PRESSURE_PLATE");
            case "@mobdrops" -> MOB_DROPS.contains(type);
            case "@blocks" -> block;
            default -> Arrays.stream(filter.split(",")).map(String::strip).filter(s->!s.isEmpty()).anyMatch(s->StorageSearch.matches(type,name,s));
        };
    }
}
