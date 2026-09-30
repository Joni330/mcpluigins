package de.casino;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnvilMaterialRepairTest {
    @Test void previousWorkPenaltyDoesNotMakeMaterialRepairExpensive(){
        assertEquals(1,AnvilImprovements.materialCost(80,1,false));
        assertEquals(4,AnvilImprovements.materialCost(1000,4,false));
        assertEquals(5,AnvilImprovements.materialCost(1000,4,true));
    }
    @Test void cheaperExistingCostIsNotIncreased(){
        assertEquals(1,AnvilImprovements.materialCost(1,4,true));
        assertEquals(0,AnvilImprovements.materialCost(0,1,false));
    }
}
