package com.doolecg.techtale.core.network;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.resource.ResourceHandler;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.core.resource.ResourceSplitter;
import java.util.ArrayList;
import java.util.List;

/** A network of pipes carrying one kind of resource, holding one content type at a time. */
public class ResourceNetwork extends Network {
    /** Looks up the resource handler at a block, or null. */
    @FunctionalInterface
    public interface HandlerLookup {
        ResourceHandler get(long pos);
    }

    private final ResourceKind kind;
    private long[] facePositions;
    private Direction[] faceSides;
    private long lastTransfer;

    public ResourceNetwork(ResourceKind kind) {
        this.kind = kind;
    }

    public ResourceKind getKind() {
        return kind;
    }

    /** Untyped inserts are refused: a resource network has to know what it carries. */
    @Override
    public long insert(long amount, boolean simulate) {
        return 0;
    }

    /** Adds to the buffer if {@code type} matches the content or the buffer is empty. @return the amount accepted */
    public long insert(String type, long amount, boolean simulate) {
        if (type == null) {
            return 0;
        }
        String current = getType();
        if (current != null && !current.equals(type)) {
            return 0;
        }
        long accepted = super.insert(amount, simulate);
        if (!simulate && accepted > 0) {
            setType(type);
        }
        return accepted;
    }

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
        String type = getType();
        if (stored <= 0 || type == null) {
            lastTransfer = 0;
            return 0;
        }
        List<ResourceSplitter.Target> targets = new ArrayList<>();
        for (int i = 0; i < facePositions.length; i++) {
            ResourceHandler handler = lookup.get(facePositions[i]);
            if (handler != null && handler.connects(kind, faceSides[i])
                    && handler.insert(kind, faceSides[i], type, 1, true) > 0) {
                targets.add(new ResourceSplitter.Target(handler, faceSides[i]));
            }
        }
        long sent = ResourceSplitter.distribute(kind, type, stored, targets);
        stored -= sent;
        if (stored == 0) {
            setType(null);
        }
        lastTransfer = sent;
        return sent;
    }

    /** Units moved out of the network in the last tick. */
    public long getLastTransfer() {
        return lastTransfer;
    }
}
