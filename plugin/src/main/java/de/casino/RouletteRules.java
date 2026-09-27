package de.casino;

final class RouletteRules {
    enum Color { RED, BLACK, GREEN }
    static Color color(int number) {
        if (number < 0 || number > 36) throw new IllegalArgumentException("Ungültige Zahl");
        if (number == 0) return Color.GREEN;
        return switch(number) {
            case 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36 -> Color.RED;
            default -> Color.BLACK;
        };
    }
    static long payout(long bet, Color selected, int number) {
        if (bet < Money.MIN_SPIN_CENTS || bet > Money.MAX_SPIN_CENTS || selected == null) throw new IllegalArgumentException("Ungültiger Einsatz");
        return color(number) == selected ? Math.multiplyExact(bet, selected == Color.GREEN ? 36 : 2) : 0;
    }
}
