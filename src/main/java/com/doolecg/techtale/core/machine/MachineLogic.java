package com.doolecg.techtale.core.machine;

import com.doolecg.techtale.core.machine.MachineDefinition.SlotRole;
import com.doolecg.techtale.core.recipe.ChemicalRecipes;
import com.doolecg.techtale.core.recipe.MachineRecipe;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import com.doolecg.techtale.core.recipe.SecondaryConversion;
import com.doolecg.techtale.core.resource.ResourceStack;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/** One logic tick (1/20 s) of a machine. Energy output to neighbours is done by the caller. */
public final class MachineLogic {
    private MachineLogic() {
    }

    /** What the world tells a machine each tick. */
    public record Environment(boolean canSeeSun, ToIntFunction<String> fuelTicks, PumpSource pump, ChemicalRecipes chemicalRecipes) {
        public static final Environment NONE = new Environment(false, id -> 0);

        public Environment(boolean canSeeSun, ToIntFunction<String> fuelTicks) {
            this(canSeeSun, fuelTicks, PumpSource.NONE, new ChemicalRecipes());
        }
    }

    /** The fluid a pump can pull from the world below it. */
    public interface PumpSource {
        PumpSource NONE = (maxMb, simulate) -> null;

        /** @return the fluid drained (at most {@code maxMb}), or null when there is none */
        @Nullable
        ResourceStack drain(long maxMb, boolean simulate);
    }

    public static void tick(MachineDefinition def, MachineState state, MachineInventory inv, RecipeRegistry recipes, Environment env) {
        int speed = countUpgrades(def, inv, Machines.SPEED_UPGRADE);
        int energyUpgrades = countUpgrades(def, inv, Machines.ENERGY_UPGRADE);
        switch (def.kind()) {
            case PROCESSOR -> {
                state.energy.setCapacity(UpgradeMath.capacity(def.energyCapacity(), energyUpgrades));
                process(def, state, inv, recipes, speed, energyUpgrades);
            }
            case HEAT_GENERATOR -> burn(def, state, inv, env);
            case SOLAR_GENERATOR -> {
                state.active = env.canSeeSun() && state.energy.getNeeded() > 0;
                if (state.active) {
                    state.energy.insert(def.energyPerTick(), false);
                }
            }
            case ENERGY_CUBE -> state.active = state.energy.getStored() > 0;
            case TANK -> state.active = false;
            case PUMP, CHEMICAL_PROCESSOR -> ChemicalLogic.tick(def, state, env);
        }
    }

    public static int countUpgrades(MachineDefinition def, MachineInventory inv, String upgradeId) {
        int n = 0;
        for (int slot : def.slotsOf(SlotRole.UPGRADE)) {
            if (upgradeId.equals(inv.itemId(slot))) {
                n += inv.count(slot);
            }
        }
        return Math.min(n, UpgradeMath.MAX_UPGRADES);
    }

    private static void process(MachineDefinition def, MachineState state, MachineInventory inv, RecipeRegistry recipes, int speed, int energyUpgrades) {
        int in = def.slot(SlotRole.INPUT);
        int extra = def.slot(SlotRole.EXTRA);
        int out = def.slot(SlotRole.OUTPUT);
        if (def.usesSecondary() && extra >= 0) {
            fillSecondary(def, state, inv, recipes, extra);
        }

        MachineRecipe recipe = findRecipe(def, state, inv, recipes, in, extra);
        String recipeId = recipe == null ? null : recipe.id();
        if (recipeId == null || !recipeId.equals(state.recipeId)) {
            state.progress = 0;
            state.recipeId = recipeId;
        }
        state.ticksRequired = UpgradeMath.ticksPerOperation(def.ticksPerOperation(), speed);
        long perTick = UpgradeMath.energyPerTick(def.energyPerTick(), speed, energyUpgrades);

        if (recipe == null || !canOperate(recipe, state, inv, in, extra, out) || state.energy.getStored() < perTick) {
            state.active = false;
            return;
        }
        state.energy.extract(perTick, false);
        state.progress++;
        state.active = true;
        if (state.progress >= state.ticksRequired) {
            inv.remove(in, recipe.input().count());
            if (recipe.extra() != null) {
                inv.remove(extra, recipe.extra().count());
            }
            if (recipe.usesSecondary()) {
                state.secondaryStored -= recipe.secondaryAmount();
                if (state.secondaryStored <= 0) {
                    state.secondaryStored = 0;
                    state.secondaryType = null;
                }
            }
            inv.add(out, recipe.output().itemId(), recipe.output().count());
            state.progress = 0;
        }
    }

    @Nullable
    private static MachineRecipe findRecipe(MachineDefinition def, MachineState state, MachineInventory inv, RecipeRegistry recipes, int in, int extra) {
        String inputId = inv.itemId(in);
        if (inputId == null) {
            return null;
        }
        if (def.usesSecondary()) {
            if (state.secondaryType == null) {
                return null;
            }
            MachineRecipe r = recipes.find(def.recipeType(), inputId, null, state.secondaryType);
            return r != null && state.secondaryType.equals(r.secondaryType()) ? r : null;
        }
        String extraId = extra >= 0 ? inv.itemId(extra) : null;
        return recipes.find(def.recipeType(), inputId, extraId, null);
    }

    private static boolean canOperate(MachineRecipe recipe, MachineState state, MachineInventory inv, int in, int extra, int out) {
        if (inv.count(in) < recipe.input().count()) {
            return false;
        }
        if (recipe.extra() != null && (extra < 0 || !recipe.extra().matches(inv.itemId(extra), inv.count(extra)))) {
            return false;
        }
        if (recipe.usesSecondary() && state.secondaryStored < recipe.secondaryAmount()) {
            return false;
        }
        return inv.roomFor(out, recipe.output().itemId()) >= recipe.output().count();
    }

    /** Converts one item from the extra slot into the secondary buffer when it fits. */
    private static void fillSecondary(MachineDefinition def, MachineState state, MachineInventory inv, RecipeRegistry recipes, int extra) {
        SecondaryConversion conv = recipes.conversionFor(inv.itemId(extra));
        if (conv == null || inv.count(extra) <= 0) {
            return;
        }
        if (def.secondaryType() != null && !def.secondaryType().equals(conv.type())) {
            return;
        }
        if (state.secondaryType != null && !state.secondaryType.equals(conv.type())) {
            return;
        }
        if (state.secondaryStored + conv.amount() > def.secondaryCapacity()) {
            return;
        }
        inv.remove(extra, 1);
        state.secondaryType = conv.type();
        state.secondaryStored += conv.amount();
    }

    private static void burn(MachineDefinition def, MachineState state, MachineInventory inv, Environment env) {
        long gen = def.energyPerTick();
        if (state.burnTicks <= 0 && state.energy.getNeeded() >= gen) {
            int fuel = def.slot(SlotRole.FUEL);
            String fuelId = inv.itemId(fuel);
            int ticks = fuelId == null ? 0 : env.fuelTicks().applyAsInt(fuelId);
            if (ticks > 0 && inv.count(fuel) > 0) {
                inv.remove(fuel, 1);
                state.burnTicks = ticks;
                state.burnTotal = ticks;
            }
        }
        state.active = state.burnTicks > 0;
        if (state.active) {
            state.energy.insert(gen, false);
            state.burnTicks--;
        }
    }
}
