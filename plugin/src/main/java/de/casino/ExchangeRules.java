package de.casino;

import org.bukkit.Material;
import java.util.Set;

final class ExchangeRules {
    static final int INPUT = 11, OUTPUT = 15, CONFIRM = 24, CLOSE = 26;
    static final Set<Material> ACCEPTED = Set.of(Material.COPPER_INGOT, Material.IRON_INGOT,
            Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.NETHERITE_INGOT);
    static boolean accepts(Material material) { return ACCEPTED.contains(material); }
    static int payout(Material material, int amount) {
        int cents = switch (material) {
            case COPPER_INGOT -> 50;
            case IRON_INGOT -> 200;
            case GOLD_INGOT -> 300;
            case DIAMOND, EMERALD -> 500;
            case NETHERITE_INGOT -> 10000;
            default -> 0;
        };
        return amount > 0 ? Math.multiplyExact(amount, cents) : 0;
    }
    private ExchangeRules() {}
}
