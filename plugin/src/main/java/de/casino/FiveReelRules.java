package de.casino;

import java.util.random.RandomGenerator;

final class FiveReelRules {
    // Rows: three, four, five matching symbols; columns: copper through gold block.
    private static final int[][] WEIGHTS = {{800,650,400,300,200,150,100}, {250,200,150,100,70,50,30}, {100,80,60,40,30,20,55}};
    private static final int[] HALF_MULTIPLIERS = {1,2,3,4,5,6,10};
    record Outcome(int symbol, int matches) {}
    record Play(int[] middle, long payout) { Play { middle = middle.clone(); } @Override public int[] middle() { return middle.clone(); } }
    static Outcome ticket(int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException();
        for (int row = 0; row < 3; row++) for (int symbol = 0; symbol < 7; symbol++) {
            if (ticket < WEIGHTS[row][symbol]) return new Outcome(symbol, row + 3);
            ticket -= WEIGHTS[row][symbol];
        }
        return new Outcome(-1, 0);
    }
    static long payout(long bet, int[] middle) {
        if (bet < 100 || bet > 1000 || bet % 100 != 0 || middle.length != 5) throw new IllegalArgumentException();
        for (int symbol : middle) if (symbol < 0 || symbol > 6) throw new IllegalArgumentException();
        int count = 1;
        while (count < 5 && middle[count] == middle[0]) count++;
        return count < 3 ? 0 : bet * HALF_MULTIPLIERS[middle[0]] * (1L << (count - 3)) / 2;
    }
    static Play draw(long bet, RandomGenerator random) {
        Outcome outcome = ticket(random.nextInt(10000));
        int[] middle = new int[5];
        for (int i = 0; i < 5; i++) middle[i] = random.nextInt(7);
        if (outcome.matches() > 0) {
            for (int i = 0; i < outcome.matches(); i++) middle[i] = outcome.symbol();
            if (outcome.matches() < 5) middle[outcome.matches()] = (outcome.symbol() + 1 + random.nextInt(6)) % 7;
        } else if (middle[0] == middle[1] && middle[0] == middle[2]) middle[2] = (middle[0] + 1 + random.nextInt(6)) % 7;
        return new Play(middle, payout(bet, middle));
    }
}
