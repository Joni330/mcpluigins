package de.casino;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class ElevatorRulesTest {
    @Test void nearestFloorInEachDirection() {
        var floors = Set.of(-20, 64, 100, 150);
        assertEquals(100, ElevatorRules.next(64, -64, 320, true, floors::contains));
        assertEquals(-20, ElevatorRules.next(64, -64, 320, false, floors::contains));
        assertEquals(Integer.MIN_VALUE, ElevatorRules.next(150, -64, 320, true, floors::contains));
    }
    @Test void respectsWorldLimits() {
        assertEquals(Integer.MIN_VALUE, ElevatorRules.next(319, -64, 320, true, y -> true));
        assertEquals(Integer.MIN_VALUE, ElevatorRules.next(-64, -64, 320, false, y -> true));
        assertEquals(-64, ElevatorRules.next(-63, -64, 320, false, y -> true));
    }
}
