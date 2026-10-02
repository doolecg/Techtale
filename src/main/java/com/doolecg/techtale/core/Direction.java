package com.doolecg.techtale.core;

/** The six block faces. NORTH is -Z, EAST is +X, UP is +Y. The ordinal is the bit index in connection masks. */
public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public static final Direction[] ALL = values();
    public static final Direction[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};

    public final int dx;
    public final int dy;
    public final int dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Horizontal facing for a yaw in quarter turns, matching {@link #HORIZONTAL} order. */
    public static Direction fromQuarterTurns(int quarterTurns) {
        return HORIZONTAL[Math.floorMod(quarterTurns, 4)];
    }
}
