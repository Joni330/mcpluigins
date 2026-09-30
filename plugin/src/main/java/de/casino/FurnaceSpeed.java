package de.casino;

final class FurnaceSpeed {
    // Paper 26.3 divides its internal speed by this API multiplier.
    static double multiplier(int tier){
        if(tier<2||tier>4)throw new IllegalArgumentException("Unbekannte Ofenstufe");
        return 1.0/(1<<tier);
    }
}
