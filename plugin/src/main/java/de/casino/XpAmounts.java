package de.casino;

/** Level buttons resolve to point amounts; transfers always conserve actual XP. */
final class XpAmounts {
    private XpAmounts() {}
    static int levelPoints(int level) {
        if (level < 0) throw new IllegalArgumentException("Negatives XP-Level");
        // Paper's player XP interface uses nonnegative ints.
        if (level >= 22_000) return Integer.MAX_VALUE;
        long l = level;
        long points = level <= 16 ? l * l + 6 * l
                : level <= 31 ? (5 * l * l - 81 * l + 720) / 2
                : (9 * l * l - 325 * l + 4440) / 2;
        return (int) Math.min(Integer.MAX_VALUE, points);
    }
    static long deposit(int playerPoints, int level, int levels, long stored) {
        validate(playerPoints, level, levels, stored);
        long requested = levels == 0 ? playerPoints : Math.max(0, playerPoints - levelPoints(Math.max(0, level - levels)));
        return Math.min(requested, Long.MAX_VALUE - stored);
    }
    static long withdraw(int playerPoints, int level, int levels, long stored) {
        validate(playerPoints, level, levels, stored);
        long room = Integer.MAX_VALUE - (long) playerPoints;
        long target = levels == 0 ? room : Math.max(0, levelPoints((int) Math.min(Integer.MAX_VALUE, (long) level + levels)) - (long) playerPoints);
        return Math.min(stored, Math.min(room, target));
    }
    private static void validate(int points, int level, int levels, long stored) {
        if (points < 0 || level < 0 || levels < 0 || stored < 0) throw new IllegalArgumentException("Ungültiger XP-Bestand");
    }
}
