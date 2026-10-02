package com.doolecg.techtale.hytale.energy;

import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.energy.EnergyHandler;
import com.doolecg.techtale.core.network.EnergyNetwork;
import com.doolecg.techtale.hytale.BlockAccess;
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
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class EnergySystems {
    private EnergySystems() {
    }

    /** Wraps a cable network as a handler so generators and cubes can push into it like into a machine. */
    @Nullable
    public static EnergyHandler networkHandler(EnergyWorld ew, long cablePos) {
        EnergyNetwork network = ew.cables.networkAt(cablePos);
        if (network == null) {
            return null;
        }
        return new EnergyHandler() {
            @Override
            public boolean canConnectEnergy(Direction side) {
                return true;
            }

            @Override
            public long insertEnergy(Direction side, long amount, boolean simulate) {
                return network.insert(amount, simulate);
            }
        };
    }

    /** Keeps the cable graph in step with cable blocks appearing and disappearing. */
    public static class CableLifecycle extends RefSystem<ChunkStore> {
        private final ComponentType<ChunkStore, CableBlock> cableType;
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> infoType = BlockModule.BlockStateInfo.getComponentType();
        private final Query<ChunkStore> query;

        public CableLifecycle(ComponentType<ChunkStore, CableBlock> cableType) {
            this.cableType = cableType;
            this.query = Query.and(cableType, infoType);
        }

        @Override
        public Query<ChunkStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            CableBlock cable = cb.getComponent(ref, cableType);
            BlockModule.BlockStateInfo info = cb.getComponent(ref, infoType);
            if (cable == null || info == null) {
                return;
            }
            long pos = BlockAccess.posOf(info, cb);
            if (pos == Long.MIN_VALUE) {
                return;
            }
            EnergyWorld ew = EnergyWorld.of(store);
            ew.cables.add(pos, cable.getTier().capacity, cable.takeEnergy());
            markAround(ew, pos);
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            CableBlock cable = cb.getComponent(ref, cableType);
            BlockModule.BlockStateInfo info = cb.getComponent(ref, infoType);
            if (cable == null || info == null) {
                return;
            }
            long pos = BlockAccess.posOf(info, cb);
            if (pos == Long.MIN_VALUE) {
                return;
            }
            EnergyWorld ew = EnergyWorld.of(store);
            long share = ew.cables.remove(pos);
            ew.visualDirty.remove(pos);
            if (reason == RemoveReason.UNLOAD) {
                // Keep this cable's part of the buffer with the saved chunk.
                cable.setEnergy(share);
                info.markNeedsSaving();
            } else {
                markAround(ew, pos);
            }
        }
    }

    /** Marks the cables next to {@code pos} (and {@code pos} itself, if a cable) for a shape update. */
    public static void markAround(EnergyWorld ew, long pos) {
        if (ew.cables.contains(pos)) {
            ew.visualDirty.add(pos);
        }
        for (Direction d : Direction.ALL) {
            long n = BlockAccess.neighbour(pos, d);
            if (ew.cables.contains(n)) {
                ew.visualDirty.add(n);
            }
        }
    }

    /** Once per world tick: runs the networks at 20 Hz and refreshes cable shapes. */
    public static class NetworkTick extends TickingSystem<ChunkStore> {
        @Override
        public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
            EnergyWorld ew = EnergyWorld.of(store);
            if (!ew.forcedChunks.isEmpty()) {
                keepTicking(store.getExternalData().getWorld(), ew);
            }
            int steps = ew.networkClock.advance(dt);
            if (ew.cables.isDirty()) {
                ew.cables.rebuildIfDirty();
            }
            for (int i = 0; i < steps; i++) {
                for (EnergyNetwork network : ew.cables.networks()) {
                    network.tick(pos -> ew.machineAt(store, pos));
                }
            }
            if (!ew.visualDirty.isEmpty()) {
                World world = store.getExternalData().getWorld();
                long[] dirty = ew.visualDirty.toLongArray();
                ew.visualDirty.clear();
                for (long pos : dirty) {
                    updateShape(world, store, ew, pos);
                }
            }
        }

        /** Holds forced chunks hot: Hytale stops ticking a chunk with no player once its active timer runs out. */
        private static void keepTicking(World world, EnergyWorld ew) {
            for (long index : ew.forcedChunks) {
                WorldChunk chunk = world.getChunkIfInMemory(index);
                if (chunk != null) {
                    chunk.resetActiveTimer();
                }
            }
        }

        /** Cable models: state "C<mask>" where bit d is set when the cable connects towards direction d. */
        private static void updateShape(World world, Store<ChunkStore> store, EnergyWorld ew, long pos) {
            if (!ew.cables.contains(pos)) {
                return;
            }
            int mask = 0;
            for (Direction d : Direction.ALL) {
                long n = BlockAccess.neighbour(pos, d);
                if (ew.cables.contains(n)) {
                    mask |= d.bit();
                } else {
                    var machine = ew.machineAt(store, n);
                    if (machine != null && machine.canConnectEnergy(d.opposite())) {
                        mask |= d.bit();
                    }
                }
            }
            BlockAccess.setState(world, pos, mask == 0 ? "default" : "C" + mask);
        }
    }
}
