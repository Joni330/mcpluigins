package de.casino;

import java.util.*;

final class MiningBotFluids {
    record Offset(int side, int height) {}
    static List<Offset> shell() {
        List<Offset> result = new ArrayList<>();
        for (int side=-1; side<=1; side++) { result.add(new Offset(side,-1)); result.add(new Offset(side,3)); }
        for (int height=0; height<=2; height++) { result.add(new Offset(-2,height)); result.add(new Offset(2,height)); }
        return List.copyOf(result);
    }
}
