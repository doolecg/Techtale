package com.doolecg.techtale.hytale.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiFormatTest {
    @Test
    void energyUnits() {
        assertEquals("0 J", UiFormat.energy(0));
        assertEquals("999 J", UiFormat.energy(999));
        assertEquals("1 kJ", UiFormat.energy(1000));
        assertEquals("1.2 kJ", UiFormat.energy(1200));
        assertEquals("20 kJ", UiFormat.energy(20_000));
        assertEquals("160 kJ", UiFormat.energy(160_000));
        assertEquals("4 MJ", UiFormat.energy(4_000_000));
        assertEquals("1 MJ", UiFormat.energy(999_950));
        assertEquals("256 MJ", UiFormat.energy(256_000_000L));
        assertEquals("1.5 GJ", UiFormat.energy(1_500_000_000L));
    }

    @Test
    void amount() {
        assertEquals("0 / 4000 mB", UiFormat.amount(0, 4000));
        assertEquals("500 / 16000 mB", UiFormat.amount(500, 16000));
    }

    @Test
    void typeName() {
        assertEquals("Water", UiFormat.typeName("water"));
        assertEquals("Liquid Water", UiFormat.typeName("liquid_water"));
        assertEquals("Empty", UiFormat.typeName(null));
        assertEquals("Empty", UiFormat.typeName(""));
    }

    @Test
    void seconds() {
        assertEquals("0.0 s", UiFormat.seconds(0));
        assertEquals("4.5 s", UiFormat.seconds(90));
        assertEquals("10.0 s", UiFormat.seconds(200));
    }
}
