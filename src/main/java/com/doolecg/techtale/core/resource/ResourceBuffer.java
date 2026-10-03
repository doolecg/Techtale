package com.doolecg.techtale.core.resource;

/** A single-type tank with a capacity for a specific resource. Type clears to null when amount reaches 0. */
public class ResourceBuffer {
    private String type;
    private long amount;
    private long capacity;

    public ResourceBuffer(long capacity) {
        this.capacity = capacity;
    }

    /** @return the amount accepted */
    public long insert(String type, long amount, boolean simulate) {
        if (amount <= 0 || type == null) {
            return 0;
        }
        // If tank has content of a different type, reject
        if (this.type != null && !this.type.equals(type)) {
            return 0;
        }
        long accepted = Math.min(amount, capacity - this.amount);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            this.type = type;
            this.amount += accepted;
        }
        return accepted;
    }

    /** @return the amount extracted */
    public long extract(long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long taken = Math.min(amount, this.amount);
        if (!simulate) {
            this.amount -= taken;
            if (this.amount == 0) {
                this.type = null;
            }
        }
        return taken;
    }

    public String getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(0, Math.min(amount, capacity));
        if (this.amount == 0) {
            this.type = null;
        }
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = Math.max(0, capacity);
        if (this.amount > this.capacity) {
            this.amount = this.capacity;
            if (this.amount == 0) {
                this.type = null;
            }
        }
    }

    public long getNeeded() {
        return capacity - amount;
    }

    public float getFillRatio() {
        return capacity <= 0 ? 0f : (float) ((double) amount / capacity);
    }
}
