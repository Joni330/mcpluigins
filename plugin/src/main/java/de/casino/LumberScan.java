package de.casino;

import java.util.*;
import java.util.function.Function;
import de.casino.MiningBotVeins.Pos;

/** Read-only, bounded chunk scan. Tree recognition never follows blocks into another chunk. */
final class LumberScan {
    record Cell(String type, boolean persistentLeaves) {}
    private record Leaf(Pos position, int distance) {}
    private final int chunkX, chunkZ, minY, total;
    private final Function<Pos, Cell> read;
    private final Map<Pos, String> wood = new LinkedHashMap<>(), leaves = new HashMap<>();
    private final Set<Pos> soil = new HashSet<>();
    private Map<Pos, String> harvest;
    private int cursor, trees;

    LumberScan(int chunkX, int chunkZ, int minY, int maxY, Function<Pos, Cell> read) {
        this.chunkX = chunkX; this.chunkZ = chunkZ; this.minY = minY; this.read = read;
        if (maxY <= minY) throw new IllegalArgumentException("Invalid world height");
        total = Math.multiplyExact(maxY - minY, 256);
    }

    boolean step(int budget) {
        if (budget < 1) throw new IllegalArgumentException("Scan budget must be positive");
        int end = (int)Math.min(total, (long)cursor + budget);
        while (cursor < end) {
            Pos p = new Pos((chunkX << 4) + (cursor & 15), minY + cursor / 256,
                    (chunkZ << 4) + ((cursor >> 4) & 15));
            Cell cell = read.apply(p);
            if (LumberRules.soil(cell.type())) soil.add(p);
            else if (woodSpecies(cell.type()) != null) wood.put(p, cell.type());
            else if (cell.type().endsWith("_LEAVES") && !cell.persistentLeaves()) leaves.put(p, cell.type());
            cursor++;
        }
        if (cursor == total && harvest == null) {
            harvest = recognize();
            wood.clear(); leaves.clear(); soil.clear();
        }
        return harvest != null;
    }

    int percent() { return (int)((long)cursor * 100 / total); }
    int trees() { return trees; }
    Map<Pos, String> harvest() {
        if (harvest == null) throw new IllegalStateException("Scan not finished");
        return Collections.unmodifiableMap(harvest);
    }

    private static String woodSpecies(String type) {
        if (type.equals("MANGROVE_ROOTS") || type.equals("MUDDY_MANGROVE_ROOTS")) return "MANGROVE";
        if (!type.endsWith("_LOG") || type.startsWith("STRIPPED_")) return null;
        String species = type.substring(0, type.length() - 4);
        return species.equals("MANGROVE") || LumberRules.SAPLINGS.contains(species + "_SAPLING") ? species : null;
    }

    private Map<Pos, String> recognize() {
        Map<Pos, String> result = new LinkedHashMap<>();
        Set<Pos> remaining = new LinkedHashSet<>(wood.keySet());
        while (!remaining.isEmpty()) {
            Pos first = remaining.iterator().next(); remaining.remove(first);
            String species = woodSpecies(wood.get(first));
            List<Pos> trunk = new ArrayList<>(); ArrayDeque<Pos> queue = new ArrayDeque<>(); queue.add(first);
            boolean rooted = false, hasLog = false; int low = first.y(), high = first.y();
            while (!queue.isEmpty()) {
                Pos p = queue.removeFirst(); trunk.add(p);
                rooted |= soil.contains(new Pos(p.x(), p.y() - 1, p.z()));
                hasLog |= wood.get(p).endsWith("_LOG"); low = Math.min(low, p.y()); high = Math.max(high, p.y());
                // Diagonal branches (e.g. acacias) belong to the same trunk; leaves never join trunks.
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Pos next = new Pos(p.x() + dx, p.y() + dy, p.z() + dz);
                    String type = wood.get(next);
                    if (remaining.contains(next) && species.equals(woodSpecies(type)) && remaining.remove(next)) queue.addLast(next);
                }
            }
            if (!rooted || !hasLog || high == low || trunk.size() > 2048) continue;
            String leafType = species + "_LEAVES";
            Set<Pos> crown = new LinkedHashSet<>(); ArrayDeque<Leaf> pending = new ArrayDeque<>();
            for (Pos p : trunk) for (Pos neighbour : p.neighbours())
                if (leafType.equals(leaves.get(neighbour)) && crown.add(neighbour)) pending.addLast(new Leaf(neighbour, 1));
            while (!pending.isEmpty()) {
                Leaf leaf = pending.removeFirst();
                if (leaf.distance() == 6) continue;
                for (Pos neighbour : leaf.position().neighbours())
                    if (leafType.equals(leaves.get(neighbour)) && crown.add(neighbour)) pending.addLast(new Leaf(neighbour, leaf.distance() + 1));
            }
            // A bare log column or player-placed leaf decoration is not a mature tree.
            if (crown.size() < 4) continue;
            trees++;
            for (Pos p : trunk) result.put(p, wood.get(p));
            for (Pos p : crown) result.put(p, leaves.get(p));
        }
        return result;
    }
}
