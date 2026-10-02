package com.doolecg.techtale.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnergyBufferTest {
    @Test
    void insertIsLimitedByCapacity() {
        EnergyBuffer b = new EnergyBuffer(1000);
        assertEquals(800, b.insert(800, false));
        assertEquals(200, b.insert(500, false));
        assertEquals(1000, b.getStored());
        assertEquals(0, b.insert(1, false));
    }

    @Test
    void insertIsLimitedByMaxInsert() {
        EnergyBuffer b = new EnergyBuffer(1000, 100, 100);
        assertEquals(100, b.insert(500, false));
        assertEquals(100, b.getStored());
    }

    @Test
    void extractIsLimitedByStoredAndMaxExtract() {
        EnergyBuffer b = new EnergyBuffer(1000, 1000, 30);
        b.setStored(100);
        assertEquals(30, b.extract(500, false));
        assertEquals(70, b.getStored());
        b.setMaxExtract(Long.MAX_VALUE);
        assertEquals(70, b.extract(500, false));
        assertEquals(0, b.getStored());
        assertEquals(0, b.extract(1, false));
    }

    @Test
    void simulateReportsButDoesNotChange() {
        EnergyBuffer b = new EnergyBuffer(1000);
        assertEquals(400, b.insert(400, true));
        assertEquals(0, b.getStored());
        b.setStored(300);
        assertEquals(200, b.extract(200, true));
        assertEquals(300, b.getStored());
    }

    @Test
    void nonPositiveAmountsDoNothing() {
        EnergyBuffer b = new EnergyBuffer(1000);
        b.setStored(500);
        assertEquals(0, b.insert(0, false));
        assertEquals(0, b.insert(-5, false));
        assertEquals(0, b.extract(0, false));
        assertEquals(0, b.extract(-5, false));
        assertEquals(500, b.getStored());
    }

    @Test
    void setStoredClamps() {
        EnergyBuffer b = new EnergyBuffer(1000);
        b.setStored(5000);
        assertEquals(1000, b.getStored());
        b.setStored(-5);
        assertEquals(0, b.getStored());
    }

    @Test
    void setCapacityBelowStoredClampsStored() {
        EnergyBuffer b = new EnergyBuffer(1000);
        b.setStored(800);
        b.setCapacity(300);
        assertEquals(300, b.getCapacity());
        assertEquals(300, b.getStored());
        assertEquals(0, b.getNeeded());
    }

    @Test
    void setCapacityAboveStoredKeepsStoredAndNegativeBecomesZero() {
        EnergyBuffer b = new EnergyBuffer(1000);
        b.setStored(800);
        b.setCapacity(5000);
        assertEquals(800, b.getStored());
        assertEquals(4200, b.getNeeded());
        b.setCapacity(-10);
        assertEquals(0, b.getCapacity());
        assertEquals(0, b.getStored());
        assertEquals(0f, b.getFillRatio());
    }

    @Test
    void fillRatio() {
        EnergyBuffer b = new EnergyBuffer(200);
        b.setStored(50);
        assertEquals(0.25f, b.getFillRatio());
    }
}
