package com.doolecg.techtale.hytale.machine;

import com.doolecg.techtale.TechtalePlugin;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.energy.EnergyHandler;
import com.doolecg.techtale.core.machine.MachineDefinition;
import com.doolecg.techtale.core.machine.MachineState;
import com.doolecg.techtale.core.machine.Machines;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.annotation.Nullable;

/**
 * The block entity of every Techtale machine, generator and energy cube. Block JSON attaches it with
 * {@code "BlockEntity": {"Components": {"Techtale_Machine": {"MachineType": "<definition id>"}}}}.
 */
public class MachineBlock implements Component<ChunkStore>, EnergyHandler {
    public static final String ID = "Techtale_Machine";

    public static final BuilderCodec<MachineBlock> CODEC = BuilderCodec.builder(MachineBlock.class, MachineBlock::new)
        .append(new KeyedCodec<>("MachineType", Codec.STRING), (m, v) -> m.machineType = v, m -> m.machineType).add()
        .append(new KeyedCodec<>("Energy", Codec.LONG), (m, v) -> m.energy = v, m -> m.state != null ? m.state.energy.getStored() : m.energy).add()
        .append(new KeyedCodec<>("Progress", Codec.INTEGER), (m, v) -> m.progress = v, m -> m.state != null ? m.state.progress : m.progress).add()
        .append(new KeyedCodec<>("RecipeId", Codec.STRING), (m, v) -> m.recipeId = v, m -> m.state != null ? m.state.recipeId : m.recipeId).add()
        .append(new KeyedCodec<>("SecondaryType", Codec.STRING), (m, v) -> m.secondaryType = v, m -> m.state != null ? m.state.secondaryType : m.secondaryType).add()
        .append(new KeyedCodec<>("SecondaryStored", Codec.INTEGER), (m, v) -> m.secondaryStored = v, m -> m.state != null ? m.state.secondaryStored : m.secondaryStored).add()
        .append(new KeyedCodec<>("BurnTicks", Codec.INTEGER), (m, v) -> m.burnTicks = v, m -> m.state != null ? m.state.burnTicks : m.burnTicks).add()
        .append(new KeyedCodec<>("BurnTotal", Codec.INTEGER), (m, v) -> m.burnTotal = v, m -> m.state != null ? m.state.burnTotal : m.burnTotal).add()
        .append(new KeyedCodec<>("Items", ItemContainer.CODEC), (m, v) -> m.items = v, m -> m.items).add()
        .build();

    private String machineType = "";
    // Saved values, used until the state is created on load.
    private long energy;
    private int progress;
    @Nullable
    private String recipeId;
    @Nullable
    private String secondaryType;
    private int secondaryStored;
    private int burnTicks;
    private int burnTotal;
    @Nullable
    private ItemContainer items;

    private transient MachineDefinition definition;
    private transient MachineState state;
    /** Facing of the block's front (from its rotation); energy cubes output through it. */
    private transient Direction front = Direction.NORTH;
    private final transient Set<MachinePage> openPages = new CopyOnWriteArraySet<>();

    public static ComponentType<ChunkStore, MachineBlock> getComponentType() {
        return TechtalePlugin.get().getMachineComponentType();
    }

    /** Builds the runtime state from the definition and saved fields. @return false for an unknown type */
    public boolean initialize() {
        if (state != null) {
            return true;
        }
        definition = Machines.get(machineType);
        if (definition == null) {
            return false;
        }
        state = MachineState.forDefinition(definition);
        state.energy.setStored(energy);
        state.progress = progress;
        state.recipeId = recipeId;
        state.secondaryType = secondaryType;
        state.secondaryStored = secondaryStored;
        state.burnTicks = burnTicks;
        state.burnTotal = burnTotal;
        short slots = (short) definition.slots().size();
        if (slots == 0) {
            // Hytale containers cannot have zero slots.
            items = EmptyItemContainer.INSTANCE;
        } else if (items == null || items.getCapacity() != slots) {
            ItemContainer fresh = new SimpleItemContainer(slots);
            if (items != null) {
                for (short i = 0; i < Math.min(slots, items.getCapacity()); i++) {
                    fresh.setItemStackForSlot(i, items.getItemStack(i), false);
                }
            }
            items = fresh;
        }
        return true;
    }

    public boolean isInitialized() {
        return state != null;
    }

    public MachineDefinition getDefinition() {
        return definition;
    }

    public MachineState getState() {
        return state;
    }

    public ItemContainer getItems() {
        return items;
    }

    public String getMachineType() {
        return machineType;
    }

    public Direction getFront() {
        return front;
    }

    public void setFront(Direction front) {
        this.front = front;
    }

    public Set<MachinePage> getOpenPages() {
        return openPages;
    }

    // Energy: processors and cubes accept on every face (cubes not through their front); generators never accept.

    @Override
    public boolean canConnectEnergy(Direction side) {
        return state != null;
    }

    @Override
    public long insertEnergy(Direction side, long amount, boolean simulate) {
        if (state == null || definition.isGenerator()) {
            return 0;
        }
        if (definition.kind() == MachineDefinition.Kind.ENERGY_CUBE && side == front) {
            return 0;
        }
        return state.energy.insert(amount, simulate);
    }

    /** Faces energy leaves through: the front for cubes, every face for generators, none for processors. */
    public boolean outputsEnergy(Direction side) {
        if (state == null) {
            return false;
        }
        return switch (definition.kind()) {
            case ENERGY_CUBE -> side == front;
            case HEAT_GENERATOR, SOLAR_GENERATOR -> true;
            case PROCESSOR -> false;
        };
    }

    @Override
    public Component<ChunkStore> clone() {
        MachineBlock c = new MachineBlock();
        c.machineType = machineType;
        c.energy = state != null ? state.energy.getStored() : energy;
        c.progress = state != null ? state.progress : progress;
        c.recipeId = state != null ? state.recipeId : recipeId;
        c.secondaryType = state != null ? state.secondaryType : secondaryType;
        c.secondaryStored = state != null ? state.secondaryStored : secondaryStored;
        c.burnTicks = state != null ? state.burnTicks : burnTicks;
        c.burnTotal = state != null ? state.burnTotal : burnTotal;
        c.items = items != null ? items.clone() : null;
        return c;
    }
}
