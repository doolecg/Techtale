package com.doolecg.techtale.core.network;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tracks transmitter blocks of one kind (e.g. energy cables) in one world and groups them into networks.
 * Members connect to face-adjacent members. Changes are batched: add/remove mark the graph dirty and
 * {@link #rebuildIfDirty()} re-forms only the networks that were touched, conserving their stored amount.
 */
public final class NetworkGraph<N extends Network> {
    private final Supplier<N> factory;
    /** Member position -> its capacity. */
    private final Long2LongOpenHashMap capacities = new Long2LongOpenHashMap();
    private final Long2ObjectOpenHashMap<N> networkOf = new Long2ObjectOpenHashMap<>();
    private final Set<N> networks = new LinkedHashSet<>();
    /** Amount brought in by members added since the last rebuild (e.g. loaded from disk). */
    private final Long2LongOpenHashMap pendingShares = new Long2LongOpenHashMap();
    /** Positions whose neighbourhood changed since the last rebuild. */
    private final LongOpenHashSet dirty = new LongOpenHashSet();

    public NetworkGraph(Supplier<N> factory) {
        this.factory = factory;
    }

    /** Adds a member carrying {@code storedShare} into whatever network it ends up in. */
    public void add(long pos, long capacity, long storedShare) {
        capacities.put(pos, Math.max(0, capacity));
        if (storedShare > 0) {
            pendingShares.addTo(pos, storedShare);
        }
        dirty.add(pos);
    }

    /**
     * Removes a member. @return the part of its network's buffer it takes with it (save it when the
     * block is only unloading; it is lost when the block is broken).
     */
    public long remove(long pos) {
        if (!capacities.containsKey(pos)) {
            return 0;
        }
        N network = networkOf.get(pos);
        if (network != null) {
            dissolve(network);
        }
        long share = pendingShares.remove(pos);
        capacities.remove(pos);
        networkOf.remove(pos);
        dirty.remove(pos);
        for (Direction d : Direction.ALL) {
            long n = BlockPos.offset(pos, d);
            if (capacities.containsKey(n)) {
                dirty.add(n);
            }
        }
        return share;
    }

    public boolean contains(long pos) {
        return capacities.containsKey(pos);
    }

    /** The network at {@code pos}, rebuilding first if needed. */
    public N networkAt(long pos) {
        rebuildIfDirty();
        return networkOf.get(pos);
    }

    public Collection<N> networks() {
        rebuildIfDirty();
        return Collections.unmodifiableSet(networks);
    }

    public boolean isDirty() {
        return !dirty.isEmpty();
    }

    public void rebuildIfDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        // Every network touching a dirty position is dissolved; its members become seeds again.
        LongOpenHashSet seeds = new LongOpenHashSet(dirty);
        dirty.clear();
        for (long pos : seeds.toLongArray()) {
            N old = networkOf.get(pos);
            if (old != null) {
                seeds.addAll(old.getMembers());
                dissolve(old);
            }
        }
        LongSet visited = new LongOpenHashSet();
        for (long seed : seeds) {
            if (!capacities.containsKey(seed) || visited.contains(seed)) {
                continue;
            }
            N network = factory.get();
            long stored = 0;
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            queue.enqueue(seed);
            visited.add(seed);
            while (!queue.isEmpty()) {
                long pos = queue.dequeueLong();
                network.addMember(pos, capacities.get(pos));
                stored += pendingShares.remove(pos);
                N existing = networkOf.get(pos);
                if (existing != null && existing != network) {
                    // A neighbouring network we have not dissolved yet (not touched by a dirty position).
                    stored += existing.getStored();
                    for (long member : existing.getMembers().toLongArray()) {
                        networkOf.remove(member);
                        if (visited.add(member)) {
                            queue.enqueue(member);
                        }
                    }
                    networks.remove(existing);
                }
                networkOf.put(pos, network);
                for (Direction d : Direction.ALL) {
                    long n = BlockPos.offset(pos, d);
                    if (capacities.containsKey(n) && visited.add(n)) {
                        queue.enqueue(n);
                    }
                }
            }
            network.setStored(stored);
            networks.add(network);
        }
    }

    /** Breaks a network apart, parking each member's share so the next rebuild hands it on. */
    private void dissolve(N network) {
        if (!networks.remove(network)) {
            return;
        }
        long remaining = network.getStored();
        long[] members = network.getMembers().toLongArray();
        for (long member : members) {
            long part = Math.min(remaining, network.shareOf(member));
            if (part > 0) {
                pendingShares.addTo(member, part);
                remaining -= part;
            }
            networkOf.remove(member);
            dirty.add(member);
        }
        if (remaining > 0 && members.length > 0) {
            pendingShares.addTo(members[0], remaining);
        }
    }
}
