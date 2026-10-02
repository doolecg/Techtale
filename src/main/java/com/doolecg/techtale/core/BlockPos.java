package com.doolecg.techtale.core;

/** Packs block coordinates into a long key: x and z in 26 bits each, y in 12 bits. */
public final class BlockPos {
    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;

    private BlockPos() {
    }

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << (XZ_BITS + Y_BITS)) | ((z & XZ_MASK) << Y_BITS) | (y & Y_MASK);
    }

    public static int x(long key) {
        return (int) (key >> (XZ_BITS + Y_BITS));
    }

    public static int y(long key) {
        return (int) (key << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long key) {
        return (int) (key << (64 - XZ_BITS - Y_BITS) >> (64 - XZ_BITS));
    }

    public static long offset(long key, Direction d) {
        return pack(x(key) + d.dx, y(key) + d.dy, z(key) + d.dz);
    }

    public static String toString(long key) {
        return x(key) + "," + y(key) + "," + z(key);
    }
}
