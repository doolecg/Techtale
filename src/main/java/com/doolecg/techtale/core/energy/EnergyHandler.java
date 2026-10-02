package com.doolecg.techtale.core.energy;

import com.doolecg.techtale.core.Direction;

/** Something that can take or give energy through one of its faces. */
public interface EnergyHandler {
    /** Whether energy can pass through this face at all (used for cable connections). */
    boolean canConnectEnergy(Direction side);

    /** @return the amount accepted through {@code side} */
    long insertEnergy(Direction side, long amount, boolean simulate);

    /** @return the amount removed through {@code side} */
    default long extractEnergy(Direction side, long amount, boolean simulate) {
        return 0;
    }

    /** How much this handler would accept through {@code side} right now. */
    default long getEnergyNeeded(Direction side) {
        return insertEnergy(side, Long.MAX_VALUE, true);
    }
}
