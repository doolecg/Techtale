package com.doolecg.techtale.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doolecg.techtale.core.machine.MachineDefinition.Kind;
import com.doolecg.techtale.core.machine.MachineLogic.Environment;
import com.doolecg.techtale.core.machine.MachineLogic.PumpSource;
import com.doolecg.techtale.core.recipe.ChemicalOutput;
import com.doolecg.techtale.core.recipe.ChemicalRecipe;
import com.doolecg.techtale.core.recipe.ChemicalRecipes;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import com.doolecg.techtale.core.resource.RelativeSide;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.core.resource.ResourceStack;
import com.doolecg.techtale.core.resource.TankSpec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChemicalLogicTest {
    private static final MachineInventory NO_ITEMS = new MachineInventory() {
        public String itemId(int slot) {
            return null;
        }

        public int count(int slot) {
            return 0;
        }

        public int roomFor(int slot, String itemId) {
            return 0;
        }

        public void remove(int slot, int count) {
        }

        public void add(int slot, String itemId, int count) {
        }
    };

    private static TankSpec tank(long capacity, String fixedType) {
        return new TankSpec(ResourceKind.CHEMICAL, capacity, Set.<RelativeSide>of(), Set.<RelativeSide>of(), fixedType);
    }

    private static MachineDefinition separator(long outCapacity) {
        return new MachineDefinition("Sep", Kind.CHEMICAL_PROCESSOR, null, 10, 1, 10_000, 0, 0, null, List.of(),
            List.of(tank(10_000, null), tank(outCapacity, "hydrogen"), tank(outCapacity, "oxygen")), null);
    }

    private static MachineDefinition pumpDef(long capacity) {
        return new MachineDefinition("Pump", Kind.PUMP, null, 5, 20, 10_000, 0, 0, null, List.of(),
            List.of(tank(capacity, null)), null);
    }

    private static Environment env(PumpSource pump) {
        ChemicalRecipes recipes = new ChemicalRecipes();
        recipes.add(new ChemicalRecipe("electrolysis", "water", 10,
            List.of(new ChemicalOutput("hydrogen", 20), new ChemicalOutput("oxygen", 10))));
        return new Environment(false, id -> 0, pump, recipes);
    }

    private static void tick(MachineDefinition def, MachineState state, Environment env, int times) {
        for (int i = 0; i < times; i++) {
            MachineLogic.tick(def, state, NO_ITEMS, new RecipeRegistry(), env);
        }
    }

    private static PumpSource infiniteWater() {
        return (max, simulate) -> new ResourceStack(ResourceKind.FLUID, "water", max);
    }

    @Test
    void noEnergyNoWork() {
        MachineDefinition def = separator(10_000);
        MachineState state = MachineState.forDefinition(def);
        state.tanks[0].insert("water", 1000, false);
        tick(def, state, env(PumpSource.NONE), 5);
        assertEquals(1000, state.tanks[0].getAmount());
        assertEquals(0, state.tanks[1].getAmount());
        assertFalse(state.active);

        MachineDefinition pump = pumpDef(10_000);
        MachineState ps = MachineState.forDefinition(pump);
        tick(pump, ps, env(infiniteWater()), 60);
        assertEquals(0, ps.tanks[0].getAmount());
    }

    @Test
    void splitsWaterTwoToOne() {
        MachineDefinition def = separator(10_000);
        MachineState state = MachineState.forDefinition(def);
        state.energy.insert(10_000, false);
        state.tanks[0].insert("water", 1000, false);
        tick(def, state, env(PumpSource.NONE), 30);
        assertEquals(700, state.tanks[0].getAmount());
        assertEquals(600, state.tanks[1].getAmount());
        assertEquals(300, state.tanks[2].getAmount());
        assertEquals("hydrogen", state.tanks[1].getType());
        assertEquals(10_000 - 300, state.energy.getStored());
        assertTrue(state.active);
    }

    @Test
    void stallsWhenAnOutputIsFull() {
        MachineDefinition def = separator(30);
        MachineState state = MachineState.forDefinition(def);
        state.energy.insert(10_000, false);
        state.tanks[0].insert("water", 1000, false);
        tick(def, state, env(PumpSource.NONE), 10);
        assertEquals(20, state.tanks[1].getAmount());
        assertEquals(10, state.tanks[2].getAmount());
        assertEquals(990, state.tanks[0].getAmount());
        assertFalse(state.active);
    }

    @Test
    void stopsWithoutInputOrRecipe() {
        MachineDefinition def = separator(10_000);
        MachineState state = MachineState.forDefinition(def);
        state.energy.insert(10_000, false);
        tick(def, state, env(PumpSource.NONE), 3);
        assertNull(state.tanks[1].getType());
        state.tanks[0].insert("lava", 100, false);
        tick(def, state, env(PumpSource.NONE), 3);
        assertEquals(100, state.tanks[0].getAmount());
    }

    @Test
    void pumpFillsEveryTwentyTicks() {
        MachineDefinition def = pumpDef(10_000);
        MachineState state = MachineState.forDefinition(def);
        state.energy.insert(10_000, false);
        tick(def, state, env(infiniteWater()), 19);
        assertEquals(0, state.tanks[0].getAmount());
        tick(def, state, env(infiniteWater()), 1);
        assertEquals(1000, state.tanks[0].getAmount());
        assertEquals("water", state.tanks[0].getType());
        tick(def, state, env(infiniteWater()), 20);
        assertEquals(2000, state.tanks[0].getAmount());
        assertEquals(10_000 - 200, state.energy.getStored());
    }

    @Test
    void pumpRespectsTankRoom() {
        MachineDefinition def = pumpDef(1500);
        MachineState state = MachineState.forDefinition(def);
        state.energy.insert(10_000, false);
        tick(def, state, env(infiniteWater()), 20);
        tick(def, state, env(infiniteWater()), 20);
        assertEquals(1500, state.tanks[0].getAmount());
        long energy = state.energy.getStored();
        tick(def, state, env(infiniteWater()), 40);
        assertEquals(1500, state.tanks[0].getAmount());
        assertEquals(energy, state.energy.getStored());
        assertFalse(state.active);
    }
}
