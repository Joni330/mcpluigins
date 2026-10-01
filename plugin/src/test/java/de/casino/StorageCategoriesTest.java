package de.casino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StorageCategoriesTest {
    @Test void newCategoriesSelectTheirMaterialsWithoutToolSubstringMistakes(){
        for(String type:new String[]{"STRIPPED_OAK_LOG","CHERRY_PLANKS","BAMBOO_MOSAIC","CRIMSON_HYPHAE","WARPED_STEM","DARK_OAK_STAIRS","SPRUCE_CHEST_BOAT"})assertTrue(StorageCategories.matches(type,true,"","@wood"),type);
        for(String type:new String[]{"STONE","DIAMOND_AXE","OAK_LEAVES","OAK_SAPLING"})assertFalse(StorageCategories.matches(type,true,"","@wood"),type);
        assertTrue(StorageCategories.matches("DIAMOND_AXE",false,"","@tools"));assertTrue(StorageCategories.matches("IRON_PICKAXE",false,"","@tools"));
        assertFalse(StorageCategories.matches("DIAMOND_SWORD",false,"","@tools"));assertTrue(StorageCategories.matches("DIAMOND_SWORD",false,"","@weapons"));
        for(String type:new String[]{"BOW","CROSSBOW","TRIDENT","MACE"})assertTrue(StorageCategories.matches(type,false,"","@weapons"));
        for(String type:new String[]{"COOKED_BEEF","GOLDEN_CARROT","BREAD","HONEY_BOTTLE"})assertTrue(StorageCategories.matches(type,false,"","@food"));
        assertFalse(StorageCategories.matches("RAW_IRON",false,"","@food"));
        for(String type:new String[]{"OAK_SAPLING","WHEAT_SEEDS","POPPY","OAK_LEAVES","MANGROVE_PROPAGULE"})assertTrue(StorageCategories.matches(type,true,"","@plants"));
        assertFalse(StorageCategories.matches("IRON_BLOCK",true,"","@plants"));
        for(String type:new String[]{"HOPPER","REDSTONE","OBSERVER","OAK_BUTTON","POWERED_RAIL"})assertTrue(StorageCategories.matches(type,true,"","@redstone"));
        assertFalse(StorageCategories.matches("DIAMOND",false,"","@redstone"));
        for(String type:new String[]{"BONE","STRING","GUNPOWDER","BREEZE_ROD"})assertTrue(StorageCategories.matches(type,false,"","@mobdrops"));
    }
    @Test void defaultMigrationAddsCategoriesAndPreservesPersonalFilters(){
        var categories=new java.util.LinkedHashMap<String,String>();
        categories.put("Alle","*");categories.put("Rüstungen","@armor");categories.put("Holz","birke");categories.put("Bauprojekt","glas,stein");
        StorageCategories.addMissingDefaults(categories);
        assertEquals("birke",categories.get("Holz"));assertEquals("glas,stein",categories.get("Bauprojekt"));
        assertEquals("@redstone",categories.get("Redstone"));assertEquals("@plants",categories.get("Pflanzen"));
        int size=categories.size();StorageCategories.addMissingDefaults(categories);assertEquals(size,categories.size());
        assertEquals("Alle",categories.keySet().iterator().next());
    }
    @Test void builtInViewsIncludeArmorAndOreProducts(){
        assertTrue(StorageCategories.matches("DIAMOND_HELMET",false,"","@armor"));
        assertFalse(StorageCategories.matches("DIAMOND_PICKAXE",false,"","@armor"));
        for(String type:new String[]{"DEEPSLATE_DIAMOND_ORE","RAW_IRON","IRON_INGOT","LAPIS_LAZULI"})assertTrue(StorageCategories.matches(type,true,"","@ores"));
        assertFalse(StorageCategories.matches("DIAMOND_HELMET",false,"","@ores"));
        assertTrue(StorageCategories.matches("STONE",true,"","@blocks"));
        assertFalse(StorageCategories.matches("STICK",false,"","@blocks"));
    }
    @Test void customFiltersUseAlternativeSearchTermsAndNames(){
        assertTrue(StorageCategories.matches("OAK_PLANKS",true,"","holz,eisen"));
        assertTrue(StorageCategories.matches("IRON_INGOT",false,"","holz,eisen"));
        assertTrue(StorageCategories.matches("PAPER",false,"Bauplan","bauplan"));
        assertFalse(StorageCategories.matches("DIRT",true,"","holz,eisen"));
        assertFalse(StorageCategories.matches("DIRT",true,"",",,"));
    }
    @Test void normalClickTakesOneAndShiftRespectsAvailableAndStackLimits(){
        assertEquals(1,StorageLayout.withdrawal(1024,64,false));
        assertEquals(64,StorageLayout.withdrawal(1024,64,true));
        assertEquals(9,StorageLayout.withdrawal(9,64,true));
        assertEquals(1,StorageLayout.withdrawal(10,1,true));
        assertEquals(16,StorageLayout.withdrawal(100,16,true));
        assertEquals(0,StorageLayout.withdrawal(0,64,true));
    }
}
