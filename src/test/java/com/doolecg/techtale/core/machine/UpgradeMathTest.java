package com.doolecg.techtale.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpgradeMathTest {
    @Test
    void ticksFollowTenToTheMinusSpeedOverEight() {
        assertEquals(200, UpgradeMath.ticksPerOperation(200, 0));
        assertEquals(20, UpgradeMath.ticksPerOperation(200, 8));
        assertEquals(112, UpgradeMath.ticksPerOperation(200, 2));
    }

    @Test
    void ticksNeverDropBelowOne() {
        assertEquals(1, UpgradeMath.ticksPerOperation(1, 8));
        assertEquals(1, UpgradeMath.ticksPerOperation(5, 8));
        for (int base = 1; base <= 50; base++) {
            for (int speed = 0; speed <= 8; speed++) {
                assertTrue(UpgradeMath.ticksPerOperation(base, speed) >= 1);
            }
        }
    }

    @Test
    void energyPerTickFollowsTenToTheTwoSpeedMinusEnergyOverEight() {
        assertEquals(50, UpgradeMath.energyPerTick(50, 0, 0));
        assertEquals(5000, UpgradeMath.energyPerTick(50, 8, 0));
        assertEquals(5, UpgradeMath.energyPerTick(50, 0, 8));
        assertEquals(500, UpgradeMath.energyPerTick(50, 8, 8));
    }

    @Test
    void energyPerTickIsAtLeastOne() {
        assertEquals(1, UpgradeMath.energyPerTick(1, 0, 8));
    }

    @Test
    void capacityScalesWithEnergyUpgrades() {
        assertEquals(20_000, UpgradeMath.capacity(20_000, 0));
        assertEquals(200_000, UpgradeMath.capacity(20_000, 8));
    }

    @Test
    void upgradeCountsAreClampedToZeroAndEight() {
        assertEquals(UpgradeMath.ticksPerOperation(200, 8), UpgradeMath.ticksPerOperation(200, 20));
        assertEquals(UpgradeMath.ticksPerOperation(200, 0), UpgradeMath.ticksPerOperation(200, -3));
        assertEquals(UpgradeMath.capacity(20_000, 8), UpgradeMath.capacity(20_000, 99));
        assertEquals(UpgradeMath.energyPerTick(50, 8, 0), UpgradeMath.energyPerTick(50, 30, -2));
    }
}
