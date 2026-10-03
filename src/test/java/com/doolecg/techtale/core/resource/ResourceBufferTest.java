package com.doolecg.techtale.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ResourceBufferTest {
    @Test
    void insertAcceptsResourcesWithinCapacity() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        assertEquals(500, tank.insert("water", 500, false));
        assertEquals(500, tank.getAmount());
        assertEquals("water", tank.getType());
    }

    @Test
    void insertIsLimitedByCapacity() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        assertEquals(600, tank.insert("water", 600, false));
        assertEquals(400, tank.insert("water", 500, false));
        assertEquals(1000, tank.getAmount());
        assertEquals(0, tank.insert("water", 1, false));
    }

    @Test
    void insertRejectsNullType() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        assertEquals(0, tank.insert(null, 500, false));
        assertEquals(0, tank.getAmount());
        assertNull(tank.getType());
    }

    @Test
    void insertRejectsDifferentType() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        assertEquals(500, tank.insert("water", 500, false));
        assertEquals(0, tank.insert("lava", 500, false));
        assertEquals(500, tank.getAmount());
        assertEquals("water", tank.getType());
    }

    @Test
    void insertRejectsNonPositiveAmounts() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        assertEquals(0, tank.insert("water", 0, false));
        assertEquals(0, tank.insert("water", -5, false));
        assertEquals(0, tank.getAmount());
    }

    @Test
    void extractRemovesResourcesWhenEmpty() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.insert("water", 500, false);
        assertEquals(300, tank.extract(300, false));
        assertEquals(200, tank.getAmount());
        assertEquals("water", tank.getType());
    }

    @Test
    void extractClearsTypeWhenReachesZero() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.insert("water", 500, false);
        assertEquals(500, tank.extract(500, false));
        assertEquals(0, tank.getAmount());
        assertNull(tank.getType());
    }

    @Test
    void extractRejectsNonPositiveAmounts() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.setAmount(500);
        assertEquals(0, tank.extract(0, false));
        assertEquals(0, tank.extract(-5, false));
        assertEquals(500, tank.getAmount());
    }

    @Test
    void simulateReportsButDoesNotChange() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        assertEquals(400, tank.insert("water", 400, true));
        assertEquals(0, tank.getAmount());
        assertNull(tank.getType());

        tank.insert("water", 300, false);
        assertEquals(200, tank.extract(200, true));
        assertEquals(300, tank.getAmount());
    }

    @Test
    void setAmountClamps() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.setAmount(5000);
        assertEquals(1000, tank.getAmount());
        tank.setAmount(-5);
        assertEquals(0, tank.getAmount());
        assertNull(tank.getType());
    }

    @Test
    void setAmountClearsTypeAtZero() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.insert("water", 100, false);
        tank.setAmount(0);
        assertEquals(0, tank.getAmount());
        assertNull(tank.getType());
    }

    @Test
    void setCapacityBelowAmountClamps() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.insert("water", 800, false);
        tank.setCapacity(300);
        assertEquals(300, tank.getCapacity());
        assertEquals(300, tank.getAmount());
        assertEquals(0, tank.getNeeded());
    }

    @Test
    void setCapacityAboveAmountKeepsAmount() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.insert("water", 800, false);
        tank.setCapacity(5000);
        assertEquals(800, tank.getAmount());
        assertEquals(4200, tank.getNeeded());
    }

    @Test
    void setCapacityNegativeBecomesZero() {
        ResourceBuffer tank = new ResourceBuffer(1000);
        tank.insert("water", 800, false);
        tank.setCapacity(-10);
        assertEquals(0, tank.getCapacity());
        assertEquals(0, tank.getAmount());
        // Note: type is cleared when amount reaches 0
        assertNull(tank.getType());
    }

    @Test
    void fillRatio() {
        ResourceBuffer tank = new ResourceBuffer(200);
        tank.insert("water", 50, false);
        assertEquals(0.25f, tank.getFillRatio());
    }

    @Test
    void fillRatioZeroWhenEmpty() {
        ResourceBuffer tank = new ResourceBuffer(200);
        assertEquals(0f, tank.getFillRatio());
    }
}
