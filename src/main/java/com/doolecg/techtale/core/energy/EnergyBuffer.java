package com.doolecg.techtale.core.energy;

/** A store of Joules with a capacity and per-call insert/extract limits. */
public class EnergyBuffer {
    private long stored;
    private long capacity;
    private long maxInsert;
    private long maxExtract;

    public EnergyBuffer(long capacity) {
        this(capacity, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    public EnergyBuffer(long capacity, long maxInsert, long maxExtract) {
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
    }

    /** @return the amount accepted */
    public long insert(long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long accepted = Math.min(Math.min(amount, maxInsert), capacity - stored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            stored += accepted;
        }
        return accepted;
    }

    /** @return the amount removed */
    public long extract(long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long removed = Math.min(Math.min(amount, maxExtract), stored);
        if (!simulate) {
            stored -= removed;
        }
        return removed;
    }

    public long getStored() {
        return stored;
    }

    public void setStored(long stored) {
        this.stored = Math.max(0, Math.min(stored, capacity));
    }

    public long getCapacity() {
        return capacity;
    }

    /** Changes the capacity (e.g. energy upgrades); stored energy above the new capacity is lost. */
    public void setCapacity(long capacity) {
        this.capacity = Math.max(0, capacity);
        if (stored > this.capacity) {
            stored = this.capacity;
        }
    }

    public long getNeeded() {
        return capacity - stored;
    }

    public void setMaxInsert(long maxInsert) {
        this.maxInsert = maxInsert;
    }

    public void setMaxExtract(long maxExtract) {
        this.maxExtract = maxExtract;
    }

    public long getMaxInsert() {
        return maxInsert;
    }

    public long getMaxExtract() {
        return maxExtract;
    }

    public float getFillRatio() {
        return capacity <= 0 ? 0f : (float) ((double) stored / capacity);
    }
}
