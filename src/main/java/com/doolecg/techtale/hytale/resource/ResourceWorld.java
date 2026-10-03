package com.doolecg.techtale.hytale.resource;

import com.doolecg.techtale.TechtalePlugin;
import com.doolecg.techtale.core.network.NetworkGraph;
import com.doolecg.techtale.core.network.ResourceNetwork;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.hytale.energy.LogicClock;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumMap;
import java.util.Map;

/** Per-world pipe state: one graph per resource kind. Rebuilt from loaded blocks, never saved. */
public class ResourceWorld implements Resource<ChunkStore> {
    public final Map<ResourceKind, NetworkGraph<ResourceNetwork>> graphs = new EnumMap<>(ResourceKind.class);
    /** Pipes whose connection shape needs recomputing. */
    public final LongOpenHashSet visualDirty = new LongOpenHashSet();
    public final LogicClock networkClock = new LogicClock();

    public ResourceWorld() {
        for (ResourceKind kind : ResourceKind.values()) {
            graphs.put(kind, new NetworkGraph<>(() -> new ResourceNetwork(kind)));
        }
    }

    public static ResourceType<ChunkStore, ResourceWorld> getResourceType() {
        return TechtalePlugin.get().getResourceWorldType();
    }

    public static ResourceWorld of(Store<ChunkStore> store) {
        return store.getResource(getResourceType());
    }

    public NetworkGraph<ResourceNetwork> graph(ResourceKind kind) {
        return graphs.get(kind);
    }

    @Override
    public Resource<ChunkStore> clone() {
        return new ResourceWorld();
    }
}
