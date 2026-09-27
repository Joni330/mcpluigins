package de.casino;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.function.Predicate;

final class StorageRecipes {
    record Entry(Recipe recipe, List<RecipeChoice> choices) {
        String key() { return recipe instanceof Keyed keyed ? keyed.getKey().toString() : recipe.getResult().getType().name(); }
        Selection select(VirtualStorage<ItemStack> storage) {
            List<Predicate<ItemStack>> tests = new ArrayList<>();
            for (RecipeChoice choice : choices) tests.add(choice == null ? null : choice::test);
            int[] slots = RecipeAllocation.match(storage, tests);
            ItemStack[] matrix = new ItemStack[9];
            List<ItemStack> missing = new ArrayList<>();
            for (int i = 0; i < 9; i++) if (choices.get(i) != null) {
                matrix[i] = (slots[i] >= 0 ? storage.get(slots[i]).item() : choices.get(i).getItemStack()).clone();
                matrix[i].setAmount(1);
                if (slots[i] < 0) missing.add(matrix[i].clone());
            }
            return new Selection(matrix, missing);
        }
    }
    record Selection(ItemStack[] matrix, List<ItemStack> missing) { boolean available() { return missing.isEmpty(); } }
    static List<Entry> all(String query) {
        List<Entry> entries = new ArrayList<>();
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            List<RecipeChoice> choices = new ArrayList<>(Collections.nCopies(9, null));
            if (recipe instanceof ShapedRecipe shaped) {
                String[] rows = shaped.getShape();
                var mapping = shaped.getChoiceMap();
                for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++)
                    choices.set(y * 3 + x, mapping.get(rows[y].charAt(x)));
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                var list = shapeless.getChoiceList();
                for (int i = 0; i < list.size(); i++) choices.set(i, list.get(i));
            } else return; // Dynamic recipes have no fixed ingredient pattern; manual crafting remains available.
            ItemStack result = recipe.getResult();
            if (result.getType().isAir() || choices.stream().allMatch(Objects::isNull)) return;
            String name = result.hasItemMeta() && result.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName()) : "";
            if (!query.isBlank() && !StorageSearch.matches(result.getType().name(), name, query)) return;
            entries.add(new Entry(recipe, choices));
        });
        entries.sort(Comparator.comparing((Entry e) -> e.recipe().getResult().getType().name()).thenComparing(Entry::key));
        return entries;
    }
}
