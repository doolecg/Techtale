package com.doolecg.techtale.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.doolecg.techtale.core.Direction;
import org.junit.jupiter.api.Test;

class RelativeSideTest {
    @Test
    void frontResolvesToFront() {
        assertEquals(Direction.NORTH, RelativeSide.FRONT.resolve(Direction.NORTH));
        assertEquals(Direction.SOUTH, RelativeSide.FRONT.resolve(Direction.SOUTH));
        assertEquals(Direction.EAST, RelativeSide.FRONT.resolve(Direction.EAST));
        assertEquals(Direction.WEST, RelativeSide.FRONT.resolve(Direction.WEST));
    }

    @Test
    void backResolvesToOpposite() {
        assertEquals(Direction.SOUTH, RelativeSide.BACK.resolve(Direction.NORTH));
        assertEquals(Direction.NORTH, RelativeSide.BACK.resolve(Direction.SOUTH));
        assertEquals(Direction.WEST, RelativeSide.BACK.resolve(Direction.EAST));
        assertEquals(Direction.EAST, RelativeSide.BACK.resolve(Direction.WEST));
    }

    @Test
    void topAlwaysUp() {
        assertEquals(Direction.UP, RelativeSide.TOP.resolve(Direction.NORTH));
        assertEquals(Direction.UP, RelativeSide.TOP.resolve(Direction.SOUTH));
        assertEquals(Direction.UP, RelativeSide.TOP.resolve(Direction.EAST));
        assertEquals(Direction.UP, RelativeSide.TOP.resolve(Direction.WEST));
    }

    @Test
    void bottomAlwaysDown() {
        assertEquals(Direction.DOWN, RelativeSide.BOTTOM.resolve(Direction.NORTH));
        assertEquals(Direction.DOWN, RelativeSide.BOTTOM.resolve(Direction.SOUTH));
        assertEquals(Direction.DOWN, RelativeSide.BOTTOM.resolve(Direction.EAST));
        assertEquals(Direction.DOWN, RelativeSide.BOTTOM.resolve(Direction.WEST));
    }

    @Test
    void leftCounterClockwiseFromFront() {
        // Standing at center facing NORTH, left is WEST
        assertEquals(Direction.WEST, RelativeSide.LEFT.resolve(Direction.NORTH));
        // Standing at center facing EAST, left is NORTH
        assertEquals(Direction.NORTH, RelativeSide.LEFT.resolve(Direction.EAST));
        // Standing at center facing SOUTH, left is EAST
        assertEquals(Direction.EAST, RelativeSide.LEFT.resolve(Direction.SOUTH));
        // Standing at center facing WEST, left is SOUTH
        assertEquals(Direction.SOUTH, RelativeSide.LEFT.resolve(Direction.WEST));
    }

    @Test
    void rightClockwiseFromFront() {
        // Standing at center facing NORTH, right is EAST
        assertEquals(Direction.EAST, RelativeSide.RIGHT.resolve(Direction.NORTH));
        // Standing at center facing EAST, right is SOUTH
        assertEquals(Direction.SOUTH, RelativeSide.RIGHT.resolve(Direction.EAST));
        // Standing at center facing SOUTH, right is WEST
        assertEquals(Direction.WEST, RelativeSide.RIGHT.resolve(Direction.SOUTH));
        // Standing at center facing WEST, right is NORTH
        assertEquals(Direction.NORTH, RelativeSide.RIGHT.resolve(Direction.WEST));
    }

    @Test
    void leftRejectsVerticalFront() {
        assertThrows(IllegalArgumentException.class, () -> RelativeSide.LEFT.resolve(Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> RelativeSide.LEFT.resolve(Direction.DOWN));
    }

    @Test
    void rightRejectsVerticalFront() {
        assertThrows(IllegalArgumentException.class, () -> RelativeSide.RIGHT.resolve(Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> RelativeSide.RIGHT.resolve(Direction.DOWN));
    }
}
