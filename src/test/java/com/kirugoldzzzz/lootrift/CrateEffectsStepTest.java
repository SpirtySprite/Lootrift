package com.kirugoldzzzz.lootrift;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateEffectsStepTest {

    @Test
    void steppingDownReachesTheMinimumDespiteRoundingDrift() {
        double value = 0.7D;
        for (int step = 0; step < 5; step++) {
            value = CrateEffectsMenu.stepped(value, 0.1D, true, CrateBlockEffects.MIN_RADIUS,
                    CrateBlockEffects.MAX_RADIUS);
        }
        assertEquals(CrateBlockEffects.MIN_RADIUS, value);
        assertTrue(Double.isNaN(CrateEffectsMenu.stepped(value, 0.1D, true, CrateBlockEffects.MIN_RADIUS,
                CrateBlockEffects.MAX_RADIUS)));
    }

    @Test
    void driftedStoredValuesStillStepCleanly() {
        assertEquals(0.2D, CrateEffectsMenu.stepped(0.29999999999999993D, 0.1D, true, 0.2D, 3.0D));
        assertEquals(3.0D, CrateEffectsMenu.stepped(2.5000000000000004D, 0.5D, false, 0.2D, 3.0D));
        assertTrue(Double.isNaN(CrateEffectsMenu.stepped(2.9D, 0.5D, false, 0.2D, 3.0D)));
    }
}
