package com.doolecg.techtale.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TankSnapshotTest {
    @Test
    void encodeEmptyTanks() {
        ResourceBuffer[] tanks = {
            new ResourceBuffer(1000),
            new ResourceBuffer(1000),
            new ResourceBuffer(1000)
        };
        String encoded = TankSnapshot.encode(tanks);
        assertEquals(";;", encoded);
    }

    @Test
    void encodeSingleTank() {
        ResourceBuffer[] tanks = {new ResourceBuffer(1000)};
        tanks[0].insert("water", 500, false);
        String encoded = TankSnapshot.encode(tanks);
        assertEquals("water:500", encoded);
    }

    @Test
    void encodeMultipleTanks() {
        ResourceBuffer[] tanks = {
            new ResourceBuffer(1000),
            new ResourceBuffer(1000),
            new ResourceBuffer(1000)
        };
        tanks[0].insert("water", 1000, false);
        tanks[2].insert("oxygen", 500, false);
        String encoded = TankSnapshot.encode(tanks);
        assertEquals("water:1000;;oxygen:500", encoded);
    }

    @Test
    void encodeConsecutiveEmptyTanks() {
        ResourceBuffer[] tanks = {
            new ResourceBuffer(1000),
            new ResourceBuffer(1000),
            new ResourceBuffer(1000)
        };
        tanks[1].insert("water", 300, false);
        String encoded = TankSnapshot.encode(tanks);
        assertEquals(";water:300;", encoded);
    }

    @Test
    void decodeEmptyString() {
        long[] capacities = {1000, 1000, 1000};
        ResourceBuffer[] tanks = TankSnapshot.decode("", capacities);
        assertEquals(3, tanks.length);
        for (ResourceBuffer tank : tanks) {
            assertEquals(0, tank.getAmount());
            assertNull(tank.getType());
        }
    }

    @Test
    void decodeNull() {
        long[] capacities = {1000, 1000, 1000};
        ResourceBuffer[] tanks = TankSnapshot.decode(null, capacities);
        assertEquals(3, tanks.length);
        for (ResourceBuffer tank : tanks) {
            assertEquals(0, tank.getAmount());
            assertNull(tank.getType());
        }
    }

    @Test
    void decodeSingleTank() {
        long[] capacities = {1000};
        ResourceBuffer[] tanks = TankSnapshot.decode("water:500", capacities);
        assertEquals(1, tanks.length);
        assertEquals("water", tanks[0].getType());
        assertEquals(500, tanks[0].getAmount());
    }

    @Test
    void decodeMultipleTanks() {
        long[] capacities = {1000, 1000, 1000};
        ResourceBuffer[] tanks = TankSnapshot.decode("water:1000;;oxygen:500", capacities);
        assertEquals(3, tanks.length);
        assertEquals("water", tanks[0].getType());
        assertEquals(1000, tanks[0].getAmount());
        assertNull(tanks[1].getType());
        assertEquals(0, tanks[1].getAmount());
        assertEquals("oxygen", tanks[2].getType());
        assertEquals(500, tanks[2].getAmount());
    }

    @Test
    void decodeWithEmptySegments() {
        long[] capacities = {1000, 1000, 1000};
        ResourceBuffer[] tanks = TankSnapshot.decode(";water:300;", capacities);
        assertEquals(3, tanks.length);
        assertNull(tanks[0].getType());
        assertEquals(0, tanks[0].getAmount());
        assertEquals("water", tanks[1].getType());
        assertEquals(300, tanks[1].getAmount());
        assertNull(tanks[2].getType());
        assertEquals(0, tanks[2].getAmount());
    }

    @Test
    void encodeDecodeRoundtrip() {
        ResourceBuffer[] original = {
            new ResourceBuffer(1000),
            new ResourceBuffer(2000),
            new ResourceBuffer(1500)
        };
        original[0].insert("water", 750, false);
        original[2].insert("lava", 1200, false);

        String encoded = TankSnapshot.encode(original);
        long[] capacities = {1000, 2000, 1500};
        ResourceBuffer[] decoded = TankSnapshot.decode(encoded, capacities);

        assertEquals(3, decoded.length);
        assertEquals("water", decoded[0].getType());
        assertEquals(750, decoded[0].getAmount());
        assertNull(decoded[1].getType());
        assertEquals(0, decoded[1].getAmount());
        assertEquals("lava", decoded[2].getType());
        assertEquals(1200, decoded[2].getAmount());
    }

    @Test
    void decodeClampsToCapacity() {
        long[] capacities = {100, 100, 100};
        ResourceBuffer[] tanks = TankSnapshot.decode("water:5000;;oxygen:50", capacities);
        assertEquals(3, tanks.length);
        // First tank holds 5000 but capacity is 100, should be clamped
        assertEquals("water", tanks[0].getType());
        assertEquals(100, tanks[0].getAmount());
        // Middle tank is empty
        assertNull(tanks[1].getType());
        assertEquals(0, tanks[1].getAmount());
        // Last tank has oxygen
        assertEquals("oxygen", tanks[2].getType());
        assertEquals(50, tanks[2].getAmount());
    }

    @Test
    void decodeMalformedAmountSkipsSegment() {
        long[] capacities = {1000, 1000, 1000};
        ResourceBuffer[] tanks = TankSnapshot.decode("water:notanumber;;oxygen:500", capacities);
        assertEquals(3, tanks.length);
        // First segment is malformed, should be skipped
        assertNull(tanks[0].getType());
        assertEquals(0, tanks[0].getAmount());
        // Middle segment is empty
        assertNull(tanks[1].getType());
        assertEquals(0, tanks[1].getAmount());
        // Last segment is oxygen
        assertEquals("oxygen", tanks[2].getType());
        assertEquals(500, tanks[2].getAmount());
    }

    @Test
    void decodeLongerEncodedStringThanCapacitiesIgnoresExtra() {
        long[] capacities = {1000};
        ResourceBuffer[] tanks = TankSnapshot.decode("water:500;;oxygen:200", capacities);
        assertEquals(1, tanks.length);
        assertEquals("water", tanks[0].getType());
        assertEquals(500, tanks[0].getAmount());
    }
}
