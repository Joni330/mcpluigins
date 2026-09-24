package de.casino;

final class Money {
    static long parsePositive(String text) {
        if (!text.matches("[0-9]+([.,][0-9]{1,2})?")) throw new IllegalArgumentException("Ungültiger Betrag");
        long cents = new java.math.BigDecimal(text.replace(',', '.')).movePointRight(2).longValueExact();
        if (cents <= 0) throw new IllegalArgumentException("Betrag muss positiv sein");
        return cents;
    }
    static final long MIN_SPIN_CENTS = 10;
    static final long MAX_SPIN_CENTS = 1000;
    static String format(long cents) {
        if (cents < 0) throw new IllegalArgumentException("Negatives Guthaben");
        return (cents / 100) + "," + (cents % 100 < 10 ? "0" : "") + (cents % 100) + "€";
    }
    static boolean validSpin(long cents) { return cents >= MIN_SPIN_CENTS && cents <= MAX_SPIN_CENTS; }
    private Money() {}
}
