package de.casino;

import java.util.UUID;
import java.util.List;

/** Persisted navigation and energy, independent of the visual entities. */
record MiningBotWork(Phase phase, int distance, int energy, boolean resume, UUID operator, Pending pending) {
    enum Phase { IDLE, MINING, RETURNING, UNLOADING }
    record Pending(int x, int y, int z, String blockData, List<Pending> surrounding) {
        Pending { surrounding = List.copyOf(surrounding); }
        Pending(int x, int y, int z, String blockData) { this(x, y, z, blockData, List.of()); }
        List<Pending> blocks() {
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(new Pending(x, y, z, blockData)), surrounding.stream()).toList();
        }
    }
    MiningBotWork {
        if (distance < 0 || energy < 0) throw new IllegalArgumentException("Ungültiger Bot-Fortschritt");
    }
    static MiningBotWork idle() { return new MiningBotWork(Phase.IDLE, 0, 0, false, null, null); }
    MiningBotWork start(UUID player) { return new MiningBotWork(Phase.MINING, distance, energy, true, player, pending); }
    MiningBotWork recall(boolean resumeAfter) { return new MiningBotWork(Phase.RETURNING, distance, energy, resumeAfter, operator, pending); }
    MiningBotWork recallNow() {
        if (pending != null) throw new IllegalStateException("Abbau muss vor dem Rückruf abgeschlossen sein");
        return new MiningBotWork(Phase.UNLOADING, 0, energy, false, operator, null);
    }
    MiningBotWork homeStep() { return new MiningBotWork(distance <= 1 ? Phase.UNLOADING : Phase.RETURNING, Math.max(0, distance - 1), energy, resume, operator, pending); }
    MiningBotWork emptied() { return new MiningBotWork(resume ? Phase.MINING : Phase.IDLE, 0, energy, resume, operator, pending); }
    MiningBotWork moved() { return new MiningBotWork(phase, distance + 1, energy, resume, operator, pending); }
    MiningBotWork energy(int value) { return new MiningBotWork(phase, distance, value, resume, operator, pending); }
    MiningBotWork pending(Pending value) { return new MiningBotWork(phase, distance, energy, resume, operator, value); }
    static int dx(float yaw) { return switch (Math.floorMod(Math.round(yaw / 90), 4)) { case 1 -> -1; case 3 -> 1; default -> 0; }; }
    static int dz(float yaw) { return switch (Math.floorMod(Math.round(yaw / 90), 4)) { case 0 -> 1; case 2 -> -1; default -> 0; }; }
}
