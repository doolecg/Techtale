package com.doolecg.techtale.core.resource;

import com.doolecg.techtale.core.Direction;

/** Block face relative to a facing direction. Uses right-hand rule: thumb=front, fingers curl right. */
public enum RelativeSide {
    FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM;

    /**
     * Resolves this relative side to an absolute direction given a facing direction.
     * LEFT/RIGHT use quarter turns around the vertical axis (right-hand rule).
     * TOP/BOTTOM are always UP/DOWN.
     * BACK is opposite of front.
     */
    public Direction resolve(Direction front) {
        return switch (this) {
            case FRONT -> front;
            case BACK -> front.opposite();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case LEFT -> {
                // Counter-clockwise quarter turn on horizontal plane from front
                yield switch (front) {
                    case NORTH -> Direction.WEST;
                    case SOUTH -> Direction.EAST;
                    case EAST -> Direction.NORTH;
                    case WEST -> Direction.SOUTH;
                    default -> throw new IllegalArgumentException("front must be horizontal: " + front);
                };
            }
            case RIGHT -> {
                // Clockwise quarter turn on horizontal plane from front
                yield switch (front) {
                    case NORTH -> Direction.EAST;
                    case SOUTH -> Direction.WEST;
                    case EAST -> Direction.SOUTH;
                    case WEST -> Direction.NORTH;
                    default -> throw new IllegalArgumentException("front must be horizontal: " + front);
                };
            }
        };
    }
}
