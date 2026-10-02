package com.doolecg.techtale.core.machine;

import com.doolecg.techtale.core.recipe.RecipeType;
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
    List<SlotRole> slots
) {
    public enum Kind {
        PROCESSOR,
        HEAT_GENERATOR,
        SOLAR_GENERATOR,
        ENERGY_CUBE
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
