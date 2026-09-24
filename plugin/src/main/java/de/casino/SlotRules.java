package de.casino;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

final class SlotRules {
    static final int TOTAL_WEIGHT = 1000; // Ein Gewichtspunkt entspricht 0,1 Prozent.
    enum Outcome {
        REDSTONE(Material.REDSTONE, 125, 0), COPPER(Material.COPPER_INGOT, 150, 5),
        IRON(Material.IRON_INGOT, 100, 12), GOLD(Material.GOLD_INGOT, 70, 15),
        DIAMOND(Material.DIAMOND, 50, 20), EMERALD(Material.EMERALD, 40, 25),
        NETHERITE(Material.NETHERITE_INGOT, 20, 30), JACKPOT(Material.GOLD_BLOCK, 10, 50),
        LOSS(null, 435, 0);
        final Material symbol;
        final int weight, tenths;
        Outcome(Material symbol, int weight, int tenths) { this.symbol = symbol; this.weight = weight; this.tenths = tenths; }
        long payout(long bet) { return (Math.multiplyExact(bet, tenths) + 5) / 10; }
    }
    record Round(Outcome outcome, List<Material> reels) {}
    record Play(List<Round> rounds, long payout) {}
    static Outcome select(int roll) {
        if (roll < 0 || roll >= TOTAL_WEIGHT) throw new IllegalArgumentException("Ungültige Ziehung");
        for (Outcome outcome : Outcome.values()) { if (roll < outcome.weight) return outcome; roll -= outcome.weight; }
        throw new AssertionError();
    }
    static Play draw(long bet, RandomGenerator random) {
        if (!Money.validSpin(bet)) throw new IllegalArgumentException("Ungültiger Einsatz");
        List<Round> rounds = new ArrayList<>();
        Outcome outcome;
        do {
            outcome = select(random.nextInt(TOTAL_WEIGHT));
            List<Material> reels;
            if (outcome == Outcome.LOSS) {
                int a = random.nextInt(8), b = random.nextInt(8), c = random.nextInt(8);
                if (a == b && b == c) c = (c + 1 + random.nextInt(7)) % 8;
                reels = List.of(Outcome.values()[a].symbol, Outcome.values()[b].symbol, Outcome.values()[c].symbol);
            } else reels = List.of(outcome.symbol, outcome.symbol, outcome.symbol);
            rounds.add(new Round(outcome, reels));
        } while (outcome == Outcome.REDSTONE);
        return new Play(List.copyOf(rounds), outcome.payout(bet));
    }
    static long settledBalance(long balance, long bet, long payout) {
        if (!Money.validSpin(bet) || payout < 0) throw new IllegalArgumentException();
        if (balance < bet) throw new IllegalStateException("Nicht genug Guthaben.");
        return Math.addExact(balance - bet, payout);
    }
}
