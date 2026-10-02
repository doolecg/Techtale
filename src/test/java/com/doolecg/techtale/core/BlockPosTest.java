package com.doolecg.techtale.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class BlockPosTest {
    private static void assertRoundTrip(int x, int y, int z) {
        long key = BlockPos.pack(x, y, z);
        assertEquals(x, BlockPos.x(key), "x of " + x + "," + y + "," + z);
        assertEquals(y, BlockPos.y(key), "y of " + x + "," + y + "," + z);
        assertEquals(z, BlockPos.z(key), "z of " + x + "," + y + "," + z);
    }

    @Test
    void roundTripsPositiveCoordinates() {
        assertRoundTrip(0, 0, 0);
        assertRoundTrip(1, 1, 1);
        assertRoundTrip(12345, 64, 67890);
    }

    @Test
    void roundTripsNegativeXAndZ() {
        assertRoundTrip(-1, 0, -1);
        assertRoundTrip(-1, 100, 5);
        assertRoundTrip(5, 100, -1);
        assertRoundTrip(-12345, 200, -67890);
    }

    @Test
    void roundTripsExtremesOfThe26BitRange() {
        int min = -(1 << 25);
        int max = (1 << 25) - 1;
        assertRoundTrip(min, 0, min);
        assertRoundTrip(max, 319, max);
        assertRoundTrip(min, 319, max);
        assertRoundTrip(max, 0, min);
    }

    @Test
    void roundTripsEveryHeightFrom0To319() {
        for (int y = 0; y <= 319; y++) {
            assertRoundTrip(-7, y, 13);
            assertRoundTrip(7, y, -13);
        }
    }

    @Test
    void roundTripsNegativeY() {
        assertRoundTrip(3, -1, 4);
        assertRoundTrip(-3, -64, -4);
    }

    @Test
    void differentPositionsGetDifferentKeys() {
        assertNotEquals(BlockPos.pack(1, 2, 3), BlockPos.pack(3, 2, 1));
        assertNotEquals(BlockPos.pack(-1, 0, 0), BlockPos.pack(0, 0, -1));
    }

    @Test
    void offsetMovesOneBlockInEachDirection() {
        long key = BlockPos.pack(10, 20, 30);
        for (Direction d : Direction.ALL) {
            long moved = BlockPos.offset(key, d);
            assertEquals(10 + d.dx, BlockPos.x(moved));
            assertEquals(20 + d.dy, BlockPos.y(moved));
            assertEquals(30 + d.dz, BlockPos.z(moved));
        }
    }

    @Test
    void offsetCrossesZeroAndUndoesWithTheOpposite() {
        long key = BlockPos.pack(0, 0, 0);
        assertEquals(BlockPos.pack(-1, 0, 0), BlockPos.offset(key, Direction.WEST));
        assertEquals(BlockPos.pack(0, 0, -1), BlockPos.offset(key, Direction.NORTH));
        assertEquals(BlockPos.pack(0, -1, 0), BlockPos.offset(key, Direction.DOWN));
        for (Direction d : Direction.ALL) {
            assertEquals(key, BlockPos.offset(BlockPos.offset(key, d), d.opposite()));
        }
    }

    @Test
    void toStringListsCoordinates() {
        assertEquals("-3,64,9", BlockPos.toString(BlockPos.pack(-3, 64, 9)));
    }
}
