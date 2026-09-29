package de.casino;

import java.util.*;
import java.util.function.Function;

record MiningBotVeins(boolean enabled, int checkedDepth, List<Pos> remaining) {
    record Pos(int x, int y, int z) {
        List<Pos> neighbours() { return List.of(new Pos(x+1,y,z), new Pos(x-1,y,z), new Pos(x,y+1,z), new Pos(x,y-1,z), new Pos(x,y,z+1), new Pos(x,y,z-1)); }
    }
    MiningBotVeins { remaining = List.copyOf(remaining); }
    static MiningBotVeins off() { return new MiningBotVeins(false, 0, List.of()); }
    MiningBotVeins toggle() { return new MiningBotVeins(!enabled, checkedDepth, remaining); }
    MiningBotVeins done() { return new MiningBotVeins(enabled, checkedDepth, remaining.subList(1, remaining.size())); }
    static String ore(String name) {
        String normalized = name.startsWith("DEEPSLATE_") ? name.substring(10) : name;
        return switch (normalized) {
            case "COAL_ORE", "COPPER_ORE", "IRON_ORE", "GOLD_ORE", "REDSTONE_ORE", "LAPIS_ORE", "DIAMOND_ORE", "EMERALD_ORE",
                 "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE", "ANCIENT_DEBRIS" -> normalized;
            default -> "";
        };
    }
    // Chebyshev distance from the newly opened 3x3 face, not from the previous ore.
    static boolean inRange(Pos p, Pos centre, int dx, int dz) {
        int forward = Math.abs((p.x-centre.x)*dx + (p.z-centre.z)*dz);
        int side = Math.max(0, Math.abs((p.x-centre.x)*dz - (p.z-centre.z)*dx)-1);
        int vertical = Math.max(0, Math.abs(p.y-centre.y)-1);
        return Math.max(forward, Math.max(side, vertical)) <= 8;
    }
    static List<Pos> find(List<Pos> seeds, Pos centre, int dx, int dz, Function<Pos,String> block) {
        List<Pos> result = new ArrayList<>(); Set<Pos> claimed = new HashSet<>();
        for (Pos seed : seeds) {
            String kind = ore(block.apply(seed));
            if (kind.isEmpty() || claimed.contains(seed)) continue;
            ArrayDeque<Pos> queue = new ArrayDeque<>(); queue.add(seed); Set<Pos> visited = new HashSet<>();
            int count = 0;
            while (!queue.isEmpty() && count < 64) {
                Pos pos = queue.removeFirst();
                if (!visited.add(pos) || claimed.contains(pos) || !inRange(pos, centre, dx, dz) || !ore(block.apply(pos)).equals(kind)) continue;
                claimed.add(pos); result.add(pos); count++;
                queue.addAll(pos.neighbours());
            }
            // Other probe points already reached by this bounded search do not start the same vein again.
            for (Pos p : queue) if (inRange(p, centre, dx, dz) && ore(block.apply(p)).equals(kind)) claimed.add(p);
        }
        return List.copyOf(result);
    }
}
