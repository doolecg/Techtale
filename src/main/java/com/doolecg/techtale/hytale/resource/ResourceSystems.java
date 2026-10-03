package com.doolecg.techtale.hytale.resource;

import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.machine.MachineDefinition;
import com.doolecg.techtale.core.network.NetworkGraph;
import com.doolecg.techtale.core.network.ResourceNetwork;
import com.doolecg.techtale.core.resource.ResourceBuffer;
import com.doolecg.techtale.core.resource.ResourceHandler;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.core.resource.TankSpec;
import com.doolecg.techtale.hytale.BlockAccess;
import com.doolecg.techtale.hytale.energy.EnergyWorld;
import com.doolecg.techtale.hytale.machine.MachineBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;

public final class ResourceSystems {
    private ResourceSystems() {
    }

    /** Keeps the pipe graphs in step with pipe blocks appearing and disappearing. */
    public static class PipeLifecycle extends RefSystem<ChunkStore> {
        private final ComponentType<ChunkStore, PipeBlock> pipeType;
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> infoType = BlockModule.BlockStateInfo.getComponentType();
        private final Query<ChunkStore> query;

        public PipeLifecycle(ComponentType<ChunkStore, PipeBlock> pipeType) {
            this.pipeType = pipeType;
            this.query = Query.and(pipeType, infoType);
        }

        @Override
        public Query<ChunkStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            PipeBlock pipe = cb.getComponent(ref, pipeType);
            BlockModule.BlockStateInfo info = cb.getComponent(ref, infoType);
            if (pipe == null || info == null) {
                return;
            }
            long pos = BlockAccess.posOf(info, cb);
            if (pos == Long.MIN_VALUE) {
                return;
            }
            ResourceWorld rw = ResourceWorld.of(store);
            String type = pipe.getType();
            rw.graph(pipe.getKind()).add(pos, pipe.getTier().capacity, pipe.takeAmount(), type);
            markAround(rw, pos);
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            PipeBlock pipe = cb.getComponent(ref, pipeType);
            BlockModule.BlockStateInfo info = cb.getComponent(ref, infoType);
            if (pipe == null || info == null) {
                return;
            }
            long pos = BlockAccess.posOf(info, cb);
            if (pos == Long.MIN_VALUE) {
                return;
            }
            ResourceWorld rw = ResourceWorld.of(store);
            NetworkGraph<ResourceNetwork> graph = rw.graph(pipe.getKind());
            ResourceNetwork network = graph.networkAt(pos);
            String type = network != null ? network.getType() : null;
            long share = graph.remove(pos);
            rw.visualDirty.remove(pos);
            if (reason == RemoveReason.UNLOAD) {
                // Keep this pipe's part of the buffer with the saved chunk.
                pipe.setContent(type, share);
                info.markNeedsSaving();
            } else {
                markAround(rw, pos);
            }
        }
    }

    private static boolean isPipe(ResourceWorld rw, long pos) {
        for (NetworkGraph<ResourceNetwork> graph : rw.graphs.values()) {
            if (graph.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Marks the pipes next to {@code pos} (and {@code pos} itself, if a pipe) for a shape update. */
    public static void markAround(ResourceWorld rw, long pos) {
        if (isPipe(rw, pos)) {
            rw.visualDirty.add(pos);
        }
        for (Direction d : Direction.ALL) {
            long n = BlockAccess.neighbour(pos, d);
            if (isPipe(rw, n)) {
                rw.visualDirty.add(n);
            }
        }
    }

    /**
     * Pushes each output tank of a machine into an adjacent pipe network of its kind, or straight into an adjacent
     * resource handler.
     */
    public static void pushOutputs(MachineBlock machine, long pos, ResourceWorld rw, ResourceNetwork.HandlerLookup lookup) {
        ResourceBuffer[] tanks = machine.tanks();
        MachineDefinition definition = machine.getDefinition();
        for (int i = 0; i < tanks.length && i < definition.tanks().size(); i++) {
            ResourceBuffer tank = tanks[i];
            TankSpec spec = definition.tanks().get(i);
            for (Direction side : machine.outputSides(i)) {
                String type = tank.getType();
                if (type == null || tank.getAmount() <= 0) {
                    break;
                }
                long n = BlockAccess.neighbour(pos, side);
                ResourceNetwork network = rw.graph(spec.kind()).networkAt(n);
                if (network != null) {
                    long accepted = network.insert(type, tank.getAmount(), true);
                    if (accepted > 0) {
                        network.insert(type, tank.extract(accepted, false), false);
                    }
                } else {
                    ResourceHandler handler = lookup.get(n);
                    Direction face = side.opposite();
                    if (handler == null || !handler.connects(spec.kind(), face)) {
                        continue;
                    }
                    long accepted = handler.insert(spec.kind(), face, type, tank.getAmount(), true);
                    if (accepted > 0) {
                        handler.insert(spec.kind(), face, type, tank.extract(accepted, false), false);
                    }
                }
            }
        }
    }

    /** Once per world tick: runs the pipe networks at 20 Hz and refreshes pipe shapes. */
    public static class NetworkTick extends TickingSystem<ChunkStore> {
        @Override
        public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
            ResourceWorld rw = ResourceWorld.of(store);
            EnergyWorld ew = EnergyWorld.of(store);
            int steps = rw.networkClock.advance(dt);
            for (NetworkGraph<ResourceNetwork> graph : rw.graphs.values()) {
                if (graph.isDirty()) {
                    graph.rebuildIfDirty();
                }
            }
            for (int i = 0; i < steps; i++) {
                for (NetworkGraph<ResourceNetwork> graph : rw.graphs.values()) {
                    for (ResourceNetwork network : graph.networks()) {
                        network.tick(pos -> ew.machineAt(store, pos));
                    }
                }
            }
            if (!rw.visualDirty.isEmpty()) {
                World world = store.getExternalData().getWorld();
                long[] dirty = rw.visualDirty.toLongArray();
                rw.visualDirty.clear();
                for (long pos : dirty) {
                    updateShape(world, store, rw, ew, pos);
                }
            }
        }

        /** Pipe models: state "C" plus a mask where bit d is set when the pipe connects towards direction d. */
        private static void updateShape(World world, Store<ChunkStore> store, ResourceWorld rw, EnergyWorld ew, long pos) {
            for (ResourceKind kind : ResourceKind.values()) {
                NetworkGraph<ResourceNetwork> graph = rw.graph(kind);
                if (!graph.contains(pos)) {
                    continue;
                }
                int mask = 0;
                for (Direction d : Direction.ALL) {
                    long n = BlockAccess.neighbour(pos, d);
                    if (graph.contains(n)) {
                        mask |= d.bit();
                    } else {
                        MachineBlock machine = ew.machineAt(store, n);
                        if (machine != null && machine.connects(kind, d.opposite())) {
                            mask |= d.bit();
                        }
                    }
                }
                BlockAccess.setState(world, pos, mask == 0 ? "default" : "C" + mask);
                return;
            }
        }
    }
}
