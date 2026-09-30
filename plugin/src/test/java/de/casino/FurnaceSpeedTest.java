package de.casino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FurnaceSpeedTest {
    @Test void higherTiersShortenRecipeDurationInPaper263(){
        // Local server: internal speed = 1 / API multiplier; duration = ceil(recipe / speed).
        int[] expected={50,25,13};
        for(int tier=2;tier<=4;tier++){
            double internalSpeed=1.0/FurnaceSpeed.multiplier(tier);
            assertEquals(expected[tier-2],(int)Math.ceil(200/internalSpeed));
        }
    }
    @Test void rejectsUnknownTiers(){assertThrows(IllegalArgumentException.class,()->FurnaceSpeed.multiplier(1));}
}
