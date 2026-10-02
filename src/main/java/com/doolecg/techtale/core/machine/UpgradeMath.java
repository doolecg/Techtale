package com.doolecg.techtale.core.machine;

/** Mekanism's upgrade formulas: each type stacks to 8 and scales by powers of 10. */
public final class UpgradeMath {
    public static final int MAX_UPGRADES = 8;
    private static final double MULTIPLIER = 10.0;

    private UpgradeMath() {
    }

    public static int ticksPerOperation(int baseTicks, int speed) {
        return Math.max(1, (int) (baseTicks * Math.pow(MULTIPLIER, -clamp(speed) / (double) MAX_UPGRADES)));
    }

    public static long energyPerTick(long base, int speed, int energy) {
        double exponent = (2.0 * clamp(speed) - clamp(energy)) / MAX_UPGRADES;
        return Math.max(1, Math.round(base * Math.pow(MULTIPLIER, exponent)));
    }

    public static long capacity(long base, int energy) {
        return Math.round(base * Math.pow(MULTIPLIER, clamp(energy) / (double) MAX_UPGRADES));
    }

    private static int clamp(int count) {
        return Math.max(0, Math.min(MAX_UPGRADES, count));
    }
}
