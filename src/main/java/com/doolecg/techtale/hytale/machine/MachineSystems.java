package com.doolecg.techtale.hytale.machine;

import com.doolecg.techtale.TechtalePlugin;
import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.energy.EnergyHandler;
import com.doolecg.techtale.core.energy.EnergySplitter;
import com.doolecg.techtale.core.machine.MachineDefinition;
import com.doolecg.techtale.core.machine.MachineLogic;
import com.doolecg.techtale.core.machine.MachineState;
import com.doolecg.techtale.hytale.BlockAccess;
import com.doolecg.techtale.hytale.energy.EnergySystems;
import com.doolecg.techtale.hytale.energy.EnergyWorld;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public final class MachineSystems {
    /** Block state shown while a machine works (defined in each machine's block JSON). */
    public static final String ACTIVE_STATE = "Active";

    private MachineSystems() {
    }

    /** Sets machines up when they appear, drops their items when broken. */
    public static class Lifecycle extends RefSystem<ChunkStore> {
        private final ComponentType<ChunkStore, MachineBlock> machineType;
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> infoType = BlockModule.BlockStateInfo.getComponentType();
        private final Query<ChunkStore> query;

        public Lifecycle(ComponentType<ChunkStore, MachineBlock> machineType) {
            this.machineType = machineType;
            this.query = Query.and(machineType, infoType);
        }

        @Override
        public Query<ChunkStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            MachineBlock machine = cb.getComponent(ref, machineType);
            BlockModule.BlockStateInfo info = cb.getComponent(ref, infoType);
            if (machine == null || info == null) {
                return;
            }
            if (!machine.initialize()) {
                TechtalePlugin.get().getLogger().atWarning().log("Unknown Techtale machine type '%s'", machine.getMachineType());
                return;
            }
            machine.setFront(BlockAccess.frontOf(BlockAccess.rotationOf(info, cb)));
            long pos = BlockAccess.posOf(info, cb);
            if (pos == Long.MIN_VALUE) {
                return;
            }
            EnergyWorld ew = EnergyWorld.of(store);
            ew.machines.put(pos, ref);
            EnergySystems.markAround(ew, pos);
            machine.getItems().registerChangeEvent(e -> info.markNeedsSaving());
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            MachineBlock machine = cb.getComponent(ref, machineType);
            BlockModule.BlockStateInfo info = cb.getComponent(ref, infoType);
            if (machine == null || info == null) {
                return;
            }
            long pos = BlockAccess.posOf(info, cb);
            EnergyWorld ew = EnergyWorld.of(store);
            if (pos != Long.MIN_VALUE) {
                ew.machines.remove(pos);
                EnergySystems.markAround(ew, pos);
            }
            for (MachinePage page : machine.getOpenPages()) {
                page.closeFromMachine();
            }
            machine.getOpenPages().clear();
            if (reason == RemoveReason.UNLOAD) {
                info.markNeedsSaving();
                return;
            }
            if (pos != Long.MIN_VALUE && machine.getItems() != null) {
                List<ItemStack> drops = new ArrayList<>(machine.getItems().dropAllItemStacks());
                if (!drops.isEmpty()) {
                    World world = store.getExternalData().getWorld();
                    Store<EntityStore> entities = world.getEntityStore().getStore();
                    Vector3d at = new Vector3d(BlockPos.x(pos) + 0.5, BlockPos.y(pos) + 0.5, BlockPos.z(pos) + 0.5);
                    Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(entities, drops, at, Rotation3f.IDENTITY);
                    if (holders.length > 0) {
                        world.execute(() -> entities.addEntities(holders, AddReason.SPAWN));
                    }
                }
            }
        }
    }

    /** Runs machine logic at 20 Hz, pushes energy out of generators and cubes, and updates visuals and pages. */
    public static class Tick extends EntityTickingSystem<ChunkStore> {
        private final ComponentType<ChunkStore, MachineBlock> machineType;
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> infoType = BlockModule.BlockStateInfo.getComponentType();
        private final Query<ChunkStore> query;
        private int steps;

        public Tick(ComponentType<ChunkStore, MachineBlock> machineType) {
            this.machineType = machineType;
            this.query = Query.and(machineType, infoType);
        }

        @Override
        public Query<ChunkStore> getQuery() {
            return query;
        }

        @Override
        public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
            steps = EnergyWorld.of(store).machineClock.advance(dt);
            if (steps > 0) {
                super.tick(dt, systemIndex, store);
            }
        }

        @Override
        public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> chunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> cb) {
            MachineBlock machine = chunk.getComponent(index, machineType);
            BlockModule.BlockStateInfo info = chunk.getComponent(index, infoType);
            if (machine == null || info == null || !machine.isInitialized()) {
                return;
            }
            long pos = BlockAccess.posOf(info, store);
            if (pos == Long.MIN_VALUE) {
                return;
            }
            MachineDefinition def = machine.getDefinition();
            MachineState state = machine.getState();
            World world = store.getExternalData().getWorld();
            EnergyWorld ew = EnergyWorld.of(store);
            MachineLogic.Environment env = new MachineLogic.Environment(
                def.kind() == MachineDefinition.Kind.SOLAR_GENERATOR && canSeeSun(world, store, info, pos),
                MachineSystems::fuelTicks);
            ContainerInventory inv = new ContainerInventory(machine.getItems());
            boolean wasActive = state.active;
            for (int i = 0; i < steps; i++) {
                MachineLogic.tick(def, state, inv, TechtalePlugin.get().getRecipes(), env);
                if (def.outputRate() > 0) {
                    pushEnergy(machine, state, ew, store, pos);
                }
            }
            info.markNeedsSaving();
            if (state.active != wasActive) {
                BlockAccess.setState(world, pos, state.active ? ACTIVE_STATE : "default");
            }
            for (MachinePage page : machine.getOpenPages()) {
                page.onMachineTick();
            }
        }
    }

    /** Fuel item burn time in logic ticks. Hytale's FuelQuality is seconds of furnace fuel. */
    static int fuelTicks(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return 0;
        }
        return (int) Math.round(item.getFuelQuality() * 20);
    }

    /** Day time and nothing above the block. */
    static boolean canSeeSun(World world, Store<ChunkStore> store, BlockModule.BlockStateInfo info, long pos) {
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        if (time == null || time.getSunlightFactor() < 0.5) {
            return false;
        }
        ChunkSection section = store.getComponent(info.getSectionRef(), ChunkSection.getComponentType());
        if (section == null) {
            return false;
        }
        BlockChunk column = store.getComponent(section.getChunkColumnReference(), BlockChunk.getComponentType());
        return column == null || BlockPos.y(pos) >= column.getHeight(BlockPos.x(pos) & 31, BlockPos.z(pos) & 31);
    }

    /** Splits this tick's output between the neighbours on the output faces (cables count as one handler each). */
    static void pushEnergy(MachineBlock machine, MachineState state, EnergyWorld ew, Store<ChunkStore> store, long pos) {
        long available = Math.min(state.energy.getStored(), machine.getDefinition().outputRate());
        if (available <= 0) {
            return;
        }
        List<EnergySplitter.Target> targets = new ArrayList<>(6);
        for (Direction d : Direction.ALL) {
            if (!machine.outputsEnergy(d)) {
                continue;
            }
            long n = BlockAccess.neighbour(pos, d);
            EnergyHandler handler = ew.cables.contains(n) ? EnergySystems.networkHandler(ew, n) : ew.machineAt(store, n);
            if (handler != null && handler.canConnectEnergy(d.opposite())) {
                targets.add(new EnergySplitter.Target(handler, d.opposite()));
            }
        }
        long sent = EnergySplitter.distribute(available, targets);
        state.energy.extract(sent, false);
    }
}
