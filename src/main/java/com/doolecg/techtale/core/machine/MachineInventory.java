package com.doolecg.techtale.core.machine;

import javax.annotation.Nullable;

/** The machine's item slots as the logic sees them. The Hytale side adapts an ItemContainer to this. */
public interface MachineInventory {
    @Nullable
    String itemId(int slot);

    int count(int slot);

    /** How many more of {@code itemId} fit in {@code slot}. */
    int roomFor(int slot, String itemId);

    void remove(int slot, int count);

    void add(int slot, String itemId, int count);
}
