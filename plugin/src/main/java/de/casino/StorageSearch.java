package de.casino;

import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

final class StorageSearch {
    private StorageSearch() {}
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("eisen", "iron"), Map.entry("kupfer", "copper"), Map.entry("diamant", "diamond"),
            Map.entry("smaragd", "emerald"), Map.entry("kohle", "coal"), Map.entry("stein", "stone"),
            Map.entry("erde", "dirt"), Map.entry("sand", "sand"), Map.entry("glas", "glass"),
            Map.entry("holz", "log planks wood"), Map.entry("bretter", "planks"), Map.entry("wolle", "wool"),
            Map.entry("apfel", "apple"), Map.entry("papier", "paper"), Map.entry("barren", "ingot"),
            Map.entry("schwert", "sword"), Map.entry("spitzhacke", "pickaxe"), Map.entry("schaufel", "shovel"),
            Map.entry("axt", "axe"), Map.entry("bogen", "bow"), Map.entry("pfeil", "arrow"),
            Map.entry("truhe", "chest"), Map.entry("fass", "barrel"), Map.entry("fackel", "torch"),
            Map.entry("fichte", "spruce"), Map.entry("eiche", "oak"), Map.entry("birke", "birch"));
    private static final Map<String, String> COMPOUNDS = Map.ofEntries(
            Map.entry("diamant", "diamond"), Map.entry("eisen", "iron"), Map.entry("gold", "gold"),
            Map.entry("netherit", "netherite"), Map.entry("leder", "leather"), Map.entry("stein", "stone"),
            Map.entry("helm", "helmet"), Map.entry("brustpanzer", "chestplate"), Map.entry("hose", "leggings"),
            Map.entry("stiefel", "boots"), Map.entry("schwert", "sword"), Map.entry("spitzhacke", "pickaxe"),
            Map.entry("schaufel", "shovel"), Map.entry("axt", "axe"), Map.entry("hacke", "hoe"),
            Map.entry("werkbank", "crafting table"), Map.entry("ofen", "furnace"), Map.entry("leiter", "ladder"),
            Map.entry("eimer", "bucket"), Map.entry("schild", "shield"));
    static boolean matches(String material, String customName, String query) {
        String needle = query.toLowerCase(Locale.ROOT).strip();
        String type = material.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (type.contains(needle.replace('_', ' ')) || customName.toLowerCase(Locale.ROOT).contains(needle)) return true;
        String aliases = ALIASES.get(needle);
        if (aliases != null) for (String alias : aliases.split(" ")) if (type.contains(alias)) return true;
        String translated = needle;
        for (String word : COMPOUNDS.keySet().stream().sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList())
            translated = translated.replace(word, " " + COMPOUNDS.get(word) + " ");
        translated = translated.strip().replaceAll("\\s+", " ");
        if (!translated.equals(needle) && type.contains(translated)) return true;
        return false;
    }
    static int next(int size, int after, IntPredicate matches) {
        for (int offset = 1; offset <= size; offset++) {
            int index = Math.floorMod(after + offset, size);
            if (matches.test(index)) return index;
        }
        return -1;
    }
}
