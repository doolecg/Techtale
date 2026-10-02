package com.doolecg.techtale.core.machine;

import com.doolecg.techtale.core.energy.EnergyBuffer;
import javax.annotation.Nullable;

/** The saved, changing part of a machine. */
public class MachineState {
    public final EnergyBuffer energy;
    /** Processors: ticks spent on the current operation. */
    public int progress;
    /** Processors: id of the recipe being worked on (progress resets when it changes). */
    @Nullable
    public String recipeId;
    @Nullable
    public String secondaryType;
    public int secondaryStored;
    /** Heat generator: logic ticks of fuel left and the length of the current fuel item. */
    public int burnTicks;
    public int burnTotal;
    /** Set by the last tick: whether the machine did work (drives the active block state). */
    public boolean active;
    /** Set by the last tick: ticks needed for the current operation, for the progress bar. */
    public int ticksRequired;

    public MachineState(long capacity, long maxInsert, long maxExtract) {
        this.energy = new EnergyBuffer(capacity, maxInsert, maxExtract);
    }

    public static MachineState forDefinition(MachineDefinition def) {
        long io = def.kind() == MachineDefinition.Kind.ENERGY_CUBE ? def.outputRate() : Long.MAX_VALUE;
        return new MachineState(def.energyCapacity(), io, io);
    }

    public float progressRatio() {
        return ticksRequired <= 0 ? 0f : Math.min(1f, (float) progress / ticksRequired);
    }
}
