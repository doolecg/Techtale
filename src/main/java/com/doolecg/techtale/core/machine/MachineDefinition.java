package com.doolecg.techtale.core.machine;

import com.doolecg.techtale.core.recipe.RecipeType;
import com.doolecg.techtale.core.resource.TankSpec;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The fixed stats of a machine type. The block's id is the definition id, and its slots are laid out in
 * {@link #slots()} order in one item container.
 *
 * @param energyPerTick J consumed per logic tick (processors) or produced (generators)
 * @param outputRate J per tick pushed out of generators and energy cubes
 * @param secondaryCapacity size of the infuse/osmium buffer, 0 when unused
 * @param secondaryType the only secondary type accepted, or null for any (infuser)
 * @param tanks resource tanks (fluid/chemical), empty for most machines
 * @param chemicalRecipe name of the chemical recipe set a chemical processor uses, or null
 */
public record MachineDefinition(
    String id,
    Kind kind,
    @Nullable RecipeType recipeType,
    long energyPerTick,
    int ticksPerOperation,
    long energyCapacity,
    long outputRate,
    int secondaryCapacity,
    @Nullable String secondaryType,
    List<SlotRole> slots,
    List<TankSpec> tanks,
    @Nullable String chemicalRecipe
) {
    public enum Kind {
        PROCESSOR,
        HEAT_GENERATOR,
        SOLAR_GENERATOR,
        ENERGY_CUBE,
        TANK,
        PUMP,
        CHEMICAL_PROCESSOR
    }

    public enum SlotRole {
        INPUT,
        EXTRA,
        OUTPUT,
        FUEL,
        UPGRADE
    }

    public MachineDefinition {
        slots = List.copyOf(slots);
        tanks = List.copyOf(tanks);
    }

    public MachineDefinition(String id, Kind kind, @Nullable RecipeType recipeType, long energyPerTick,
                             int ticksPerOperation, long energyCapacity, long outputRate, int secondaryCapacity,
                             @Nullable String secondaryType, List<SlotRole> slots) {
        this(id, kind, recipeType, energyPerTick, ticksPerOperation, energyCapacity, outputRate, secondaryCapacity,
            secondaryType, slots, List.of(), null);
    }

    /** Index of the first slot with this role, or -1. */
    public int slot(SlotRole role) {
        return slots.indexOf(role);
    }

    public int[] slotsOf(SlotRole role) {
        int[] out = new int[(int) slots.stream().filter(r -> r == role).count()];
        int n = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == role) {
                out[n++] = i;
            }
        }
        return out;
    }

    public boolean usesSecondary() {
        return secondaryCapacity > 0;
    }

    public boolean isGenerator() {
        return kind == Kind.HEAT_GENERATOR || kind == Kind.SOLAR_GENERATOR;
    }
}
