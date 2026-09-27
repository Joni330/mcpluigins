package de.casino;

import java.util.*;

record SenderFilter(Mode mode, List<String> materials) {
    enum Mode { ALL, ALLOW, DENY }
    SenderFilter {
        materials = List.copyOf(materials);
        if (materials.size() > 9) throw new IllegalArgumentException("Maximal neun Filter");
    }
    boolean accepts(String material) {
        return switch (mode) {
            case ALL -> true;
            case ALLOW -> materials.contains(material);
            case DENY -> !materials.contains(material);
        };
    }
    SenderFilter add(String material) {
        if (materials.contains(material) || materials.size() == 9) return this;
        var next = new ArrayList<>(materials); next.add(material); return new SenderFilter(mode, next);
    }
    SenderFilter remove(int index) {
        var next = new ArrayList<>(materials);
        if (index >= 0 && index < next.size()) next.remove(index);
        return new SenderFilter(mode, next);
    }
    SenderFilter cycle() { return new SenderFilter(Mode.values()[(mode.ordinal() + 1) % 3], materials); }
}
