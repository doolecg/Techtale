package com.doolecg.techtale.core.network;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.energy.EnergyHandler;
import com.doolecg.techtale.core.energy.EnergySplitter;
import java.util.ArrayList;
import java.util.List;

/** A network of universal cables. Each logic tick it empties its buffer into the handlers around it. */
public class EnergyNetwork extends Network {
    /** Looks up the energy handler at a block, or null. */
    @FunctionalInterface
    public interface HandlerLookup {
        EnergyHandler get(long pos);
    }

    private long[] facePositions;
    private Direction[] faceSides;
    private long lastTransfer;

    /**
     * Faces of non-member blocks that touch the network: the neighbour position and the side of that
     * neighbour facing the cable.
     */
    private void computeBoundary() {
        List<Long> positions = new ArrayList<>();
        List<Direction> sides = new ArrayList<>();
        for (long pos : members.keySet()) {
            for (Direction d : Direction.ALL) {
                long n = BlockPos.offset(pos, d);
                if (!members.containsKey(n)) {
                    positions.add(n);
                    sides.add(d.opposite());
                }
            }
        }
        facePositions = positions.stream().mapToLong(Long::longValue).toArray();
        faceSides = sides.toArray(Direction[]::new);
    }

    public long tick(HandlerLookup lookup) {
        if (facePositions == null) {
            computeBoundary();
        }
        if (stored <= 0) {
            lastTransfer = 0;
            return 0;
        }
        List<EnergySplitter.Target> targets = new ArrayList<>();
        for (int i = 0; i < facePositions.length; i++) {
            EnergyHandler handler = lookup.get(facePositions[i]);
            if (handler != null && handler.canConnectEnergy(faceSides[i])) {
                targets.add(new EnergySplitter.Target(handler, faceSides[i]));
            }
        }
        long sent = EnergySplitter.distribute(stored, targets);
        stored -= sent;
        lastTransfer = sent;
        return sent;
    }

    /** Joules moved out of the network in the last tick. */
    public long getLastTransfer() {
        return lastTransfer;
    }
}
