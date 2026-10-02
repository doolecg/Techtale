package com.doolecg.techtale.hytale.energy;

import com.doolecg.techtale.TechtalePlugin;
import com.doolecg.techtale.core.network.EnergyNetwork;
import com.doolecg.techtale.core.network.NetworkGraph;
import com.doolecg.techtale.hytale.machine.MachineBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import javax.annotation.Nullable;

/** Per-world energy state: the cable graph and where machines are. Rebuilt from loaded blocks, never saved. */
public class EnergyWorld implements Resource<ChunkStore> {
    public final NetworkGraph<EnergyNetwork> cables = new NetworkGraph<>(EnergyNetwork::new);
    public final Long2ObjectOpenHashMap<Ref<ChunkStore>> machines = new Long2ObjectOpenHashMap<>();
    /** Cables whose connection shape needs recomputing. */
    public final LongOpenHashSet visualDirty = new LongOpenHashSet();
    /** Chunks kept ticking without a player nearby (self-test now, chunk anchors later). */
    public final LongOpenHashSet forcedChunks = new LongOpenHashSet();
    /** Logic runs at 20 Hz while the world ticks at 30; each system keeps its own accumulator. */
    public final LogicClock machineClock = new LogicClock();
    public final LogicClock networkClock = new LogicClock();

    public static ResourceType<ChunkStore, EnergyWorld> getResourceType() {
        return TechtalePlugin.get().getEnergyWorldType();
    }

    public static EnergyWorld of(Store<ChunkStore> store) {
        return store.getResource(getResourceType());
    }

    @Nullable
    public MachineBlock machineAt(Store<ChunkStore> store, long pos) {
        Ref<ChunkStore> ref = machines.get(pos);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return store.getComponent(ref, MachineBlock.getComponentType());
    }

    @Override
    public Resource<ChunkStore> clone() {
        return new EnergyWorld();
    }
}
