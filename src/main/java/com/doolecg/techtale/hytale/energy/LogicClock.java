package com.doolecg.techtale.hytale.energy;

/** Turns world-tick time into a whole number of 1/20 s logic ticks, carrying the remainder. */
public final class LogicClock {
    public static final float STEP = 0.05f;
    /** Cap so a long stall does not run hundreds of ticks at once. */
    private static final int MAX_STEPS = 10;

    private float accumulated;

    public int advance(float dt) {
        accumulated += dt;
        int steps = (int) (accumulated / STEP);
        accumulated -= steps * STEP;
        return Math.min(steps, MAX_STEPS);
    }
}
