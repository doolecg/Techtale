package com.doolecg.techtale.core.resource;

/** Codec for serializing and deserializing ResourceBuffer arrays. Format: "type:amount;type2:amount2" with empty segments for empty tanks. */
public final class TankSnapshot {
    private static final String TANK_SEPARATOR = ";";
    private static final String CONTENT_SEPARATOR = ":";

    private TankSnapshot() {
    }

    /**
     * Encodes an array of ResourceBuffer to a string.
     * Empty tanks (null type) are represented as empty segments.
     * Example: ["water:1000", "", "oxygen:500"] → "water:1000;;oxygen:500"
     */
    public static String encode(ResourceBuffer[] tanks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tanks.length; i++) {
            if (i > 0) {
                sb.append(TANK_SEPARATOR);
            }
            ResourceBuffer tank = tanks[i];
            if (tank.getType() != null) {
                sb.append(tank.getType())
                  .append(CONTENT_SEPARATOR)
                  .append(tank.getAmount());
            }
        }
        return sb.toString();
    }

    /**
     * Decodes a string back to an array of ResourceBuffer with specified capacities.
     * Empty segments represent empty tanks.
     * Example: "water:1000;;oxygen:500" with capacities [10000, 10000, 10000] → 3 tanks, first two populated
     */
    public static ResourceBuffer[] decode(String encoded, long[] capacities) {
        ResourceBuffer[] tanks = new ResourceBuffer[capacities.length];
        for (int i = 0; i < capacities.length; i++) {
            tanks[i] = new ResourceBuffer(capacities[i]);
        }

        if (encoded == null || encoded.isEmpty()) {
            return tanks;
        }

        String[] segments = encoded.split(TANK_SEPARATOR, -1); // -1 to keep trailing empty segments
        for (int i = 0; i < Math.min(segments.length, tanks.length); i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                // Empty segment means empty tank, skip
                continue;
            }
            String[] parts = segment.split(CONTENT_SEPARATOR, 2);
            if (parts.length == 2) {
                String type = parts[0];
                try {
                    long amount = Long.parseLong(parts[1]);
                    tanks[i].insert(type, amount, false);
                } catch (NumberFormatException e) {
                    // Malformed amount, skip this tank
                }
            }
        }

        return tanks;
    }
}
