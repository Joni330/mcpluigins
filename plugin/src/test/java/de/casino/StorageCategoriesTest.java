package de.casino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StorageCategoriesTest {
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
