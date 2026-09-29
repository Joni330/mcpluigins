package de.casino;
import java.util.function.IntPredicate;
final class ElevatorRules {
    static int next(int current, int min, int max, boolean up, IntPredicate elevator) {
        int step = up ? 1 : -1;
        for (int y = current + step; y >= min && y < max; y += step) if (elevator.test(y)) return y;
        return Integer.MIN_VALUE;
    }
}
