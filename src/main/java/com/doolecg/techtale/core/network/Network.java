package com.doolecg.techtale.core.network;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A connected group of transmitters sharing one buffer. The buffer capacity is the sum of the members'
 * capacities (Mekanism: a cable's per-tick rate), so throughput is bounded by the buffer.
 */
public abstract class Network {
    private static long nextId = 1;

    private final long id = nextId++;
    protected final Long2LongOpenHashMap members = new Long2LongOpenHashMap();
    protected long capacity;
    protected long stored;
    private String type;

    public long getId() {
        return id;
    }

    void addMember(long pos, long memberCapacity) {
        members.put(pos, memberCapacity);
        capacity += memberCapacity;
    }

    public LongSet getMembers() {
        return members.keySet();
    }

    public boolean contains(long pos) {
        return members.containsKey(pos);
    }

    public int size() {
        return members.size();
    }

    public long getCapacity() {
        return capacity;
    }

    public long getStored() {
        return stored;
    }

    void setStored(long stored) {
        this.stored = Math.max(0, Math.min(stored, capacity));
        if (this.stored == 0) {
            type = null;
        }
    }

    /** What the buffer holds (e.g. a fluid or gas id), or null when untyped or empty. */
    public String getType() {
        if (stored == 0) {
            type = null;
        }
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    public long getNeeded() {
        return capacity - stored;
    }

    /** Adds to the shared buffer. @return the amount accepted */
    public long insert(long amount, boolean simulate) {
        long accepted = Math.max(0, Math.min(amount, capacity - stored));
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    /** This member's part of the buffer, proportional to its capacity (what it keeps if split off or saved). */
    public long shareOf(long pos) {
        long memberCapacity = members.get(pos);
        if (capacity <= 0 || memberCapacity <= 0) {
            return 0;
        }
        long product = stored * memberCapacity;
        if (Math.multiplyHigh(stored, memberCapacity) == 0 && product >= 0) {
            return product / capacity;
        }
        return (long) ((double) stored * memberCapacity / capacity);
    }
}
