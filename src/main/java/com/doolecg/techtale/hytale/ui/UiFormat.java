package com.doolecg.techtale.hytale.ui;

/** Text for the machine screen. */
public final class UiFormat {
    private static final String[] UNITS = {"J", "kJ", "MJ", "GJ"};

    private UiFormat() {
    }

    /** Joules as {@code 999 J}, {@code 1.2 kJ}, {@code 20 kJ}, {@code 4 MJ}: one decimal at most, no trailing ".0". */
    public static String energy(long joules) {
        if (joules < 1000) {
            return joules + " " + UNITS[0];
        }
        int unit = 0;
        double scaled = joules;
        long tenths;
        while (true) {
            scaled /= 1000.0;
            unit++;
            tenths = Math.round(scaled * 10);
            if (tenths < 10_000 || unit == UNITS.length - 1) {
                break;
            }
        }
        return tenths % 10 == 0 ? (tenths / 10) + " " + UNITS[unit] : (tenths / 10) + "." + (tenths % 10) + " " + UNITS[unit];
    }

    /** Logic ticks (20 per second) as seconds with one decimal, e.g. {@code 4.5 s}. */
    public static String seconds(int ticks) {
        long tenths = Math.round(ticks / 2.0);
        return (tenths / 10) + "." + (tenths % 10) + " s";
    }
}
