package com.doolecg.techtale.core.machine;

import com.doolecg.techtale.core.recipe.ChemicalOutput;
import com.doolecg.techtale.core.recipe.ChemicalRecipe;
import com.doolecg.techtale.core.resource.ResourceBuffer;
import com.doolecg.techtale.core.resource.ResourceStack;

/** One logic tick of the fluid machines: pumps and chemical processors. */
final class ChemicalLogic {
    private static final long PUMP_BATCH_MB = 1000;

    private ChemicalLogic() {
    }

    static void tick(MachineDefinition def, MachineState state, MachineLogic.Environment env) {
        switch (def.kind()) {
            case PUMP -> pump(def, state, env);
            case CHEMICAL_PROCESSOR -> process(def, state, env);
            default -> state.active = false;
        }
    }

    private static boolean accepts(MachineDefinition def, int tank, String type) {
        String fixed = def.tanks().get(tank).fixedType();
        return fixed == null || fixed.equals(type);
    }

    private static void pump(MachineDefinition def, MachineState state, MachineLogic.Environment env) {
        state.ticksRequired = def.ticksPerOperation();
        if (state.tanks.length == 0 || state.energy.getStored() < def.energyPerTick()) {
            state.active = false;
            return;
        }
        ResourceBuffer tank = state.tanks[0];
        if (tank.getNeeded() <= 0) {
            state.active = false;
            return;
        }
        state.energy.extract(def.energyPerTick(), false);
        state.active = true;
        state.progress++;
        if (state.progress < state.ticksRequired) {
            return;
        }
        state.progress = 0;
        ResourceStack drained = env.pump().drain(Math.min(PUMP_BATCH_MB, tank.getNeeded()), true);
        if (drained == null || drained.amount() <= 0 || !accepts(def, 0, drained.type())) {
            return;
        }
        long fits = tank.insert(drained.type(), drained.amount(), true);
        if (fits <= 0) {
            return;
        }
        ResourceStack taken = env.pump().drain(fits, false);
        if (taken != null && taken.amount() > 0) {
            tank.insert(taken.type(), Math.min(taken.amount(), fits), false);
        }
    }

    private static void process(MachineDefinition def, MachineState state, MachineLogic.Environment env) {
        state.ticksRequired = 1;
        if (state.tanks.length == 0) {
            state.active = false;
            return;
        }
        ResourceBuffer in = state.tanks[0];
        ChemicalRecipe recipe = env.chemicalRecipes().find(in.getType());
        if (recipe == null || in.getAmount() < recipe.inputAmount()
            || state.energy.getStored() < def.energyPerTick() || !outputsFit(def, state, recipe)) {
            state.active = false;
            return;
        }
        state.energy.extract(def.energyPerTick(), false);
        in.extract(recipe.inputAmount(), false);
        for (int i = 0; i < recipe.outputs().size(); i++) {
            ChemicalOutput out = recipe.outputs().get(i);
            state.tanks[i + 1].insert(out.chemical(), out.amount(), false);
        }
        state.active = true;
    }

    private static boolean outputsFit(MachineDefinition def, MachineState state, ChemicalRecipe recipe) {
        if (recipe.outputs().size() + 1 > state.tanks.length) {
            return false;
        }
        for (int i = 0; i < recipe.outputs().size(); i++) {
            ChemicalOutput out = recipe.outputs().get(i);
            if (!accepts(def, i + 1, out.chemical())
                || state.tanks[i + 1].insert(out.chemical(), out.amount(), true) < out.amount()) {
                return false;
            }
        }
        return true;
    }
}
