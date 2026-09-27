package de.casino;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class RecipeAllocationTest {
    @Test void flexibleIngredientDoesNotStealOnlyExactMatch() {
        VirtualStorage<String> stock = new VirtualStorage<>(2);
        stock.set(0, "oak", 1); stock.set(1, "birch", 1);
        var chosen = RecipeAllocation.match(stock, List.of(s -> true, s -> s.equals("oak")));
        assertArrayEquals(new int[]{1, 0}, chosen);
        assertEquals(1, stock.get(0).count());
    }
    @Test void repeatedCellsRespectAmountsAcrossPages() {
        VirtualStorage<String> stock = new VirtualStorage<>(450);
        stock.set(0, "diamond", 2); stock.set(449, "diamond", 3);
        List<Predicate<String>> ingredients = Collections.nCopies(5, "diamond"::equals);
        int[] chosen = RecipeAllocation.match(stock, ingredients);
        assertEquals(2, Arrays.stream(chosen).filter(i -> i == 0).count());
        assertEquals(3, Arrays.stream(chosen).filter(i -> i == 449).count());
    }
    @Test void reportsShortageWithoutChangingStorageAndPreservesEmptyCells() {
        VirtualStorage<String> stock = new VirtualStorage<>(1);
        stock.set(0, "iron", 1);
        List<Predicate<String>> cells = Arrays.asList("iron"::equals, null, "iron"::equals);
        int[] chosen = RecipeAllocation.match(stock, cells);
        assertEquals(-1, chosen[1]);
        assertEquals(1, Arrays.stream(chosen).filter(i -> i == 0).count());
        assertEquals(1, stock.get(0).count());
    }
    @Test void exactMetadataChoiceDoesNotConsumeDifferentItem() {
        VirtualStorage<String> stock = new VirtualStorage<>(2);
        stock.set(0, "named_iron", 64); stock.set(1, "plain_iron", 1);
        assertArrayEquals(new int[]{1}, RecipeAllocation.match(stock, List.of("plain_iron"::equals)));
        assertArrayEquals(new int[]{-1}, RecipeAllocation.match(stock, List.of("enchanted_iron"::equals)));
    }
    @Test void searchUnderstandsGermanEquipmentNames() {
        assertTrue(StorageSearch.matches("DIAMOND_HELMET", "", "Diamanthelm"));
        assertTrue(StorageSearch.matches("IRON_PICKAXE", "", "Eisenspitzhacke"));
        assertTrue(StorageSearch.matches("CRAFTING_TABLE", "", "Werkbank"));
        assertFalse(StorageSearch.matches("IRON_HELMET", "", "Diamanthelm"));
    }
}
