package de.casino;

record MiningBotLighting(boolean enabled, int lastDistance) {
    static MiningBotLighting off() { return new MiningBotLighting(false, 0); }
    MiningBotLighting { if (lastDistance < 0) throw new IllegalArgumentException("Negative Fackelposition"); }
    MiningBotLighting toggle() { return new MiningBotLighting(!enabled, lastDistance); }
    boolean due(int distance) { return enabled && distance >= lastDistance + 8; }
    MiningBotLighting placed(int distance) { return new MiningBotLighting(enabled, distance); }
}
