package com.doolecg.techtale.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doolecg.techtale.core.machine.MachineLogic.Environment;
import com.doolecg.techtale.core.recipe.RecipeLoader;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MachineLogicTest {
    private static final int MAX_STACK = 64;
    private static final String RAW_OSMIUM = "Techtale_Raw_Osmium";
    private static final String RAW_TIN = "Techtale_Raw_Tin";
    private static final String DUST_OSMIUM = "Techtale_Dust_Osmium";

    /** In-memory slots with a 64-item stack limit. */
    private static final class FakeInventory implements MachineInventory {
        private final String[] items;
        private final int[] counts;

        FakeInventory(int size) {
            items = new String[size];
            counts = new int[size];
        }

        void set(int slot, String id, int count) {
            items[slot] = count > 0 ? id : null;
            counts[slot] = count;
        }

        @Override
        public String itemId(int slot) {
            return items[slot];
        }

        @Override
        public int count(int slot) {
            return counts[slot];
        }

        @Override
        public int roomFor(int slot, String itemId) {
            if (items[slot] == null) {
                return MAX_STACK;
            }
            return items[slot].equals(itemId) ? MAX_STACK - counts[slot] : 0;
        }

        @Override
        public void remove(int slot, int count) {
            counts[slot] -= count;
            if (counts[slot] <= 0) {
                counts[slot] = 0;
                items[slot] = null;
            }
        }

        @Override
        public void add(int slot, String itemId, int count) {
            items[slot] = itemId;
            counts[slot] += count;
        }
    }

    private static RecipeRegistry recipes;

    private MachineDefinition def;
    private MachineState state;
    private FakeInventory inv;

    @BeforeAll
    static void loadRecipes() throws IOException {
        recipes = RecipeLoader.loadBuiltIn(MachineLogicTest.class.getClassLoader());
    }

    private void machine(MachineDefinition definition) {
        def = definition;
        state = MachineState.forDefinition(def);
        inv = new FakeInventory(def.slots().size());
    }

    private void tick(int times, Environment env) {
        for (int i = 0; i < times; i++) {
            MachineLogic.tick(def, state, inv, recipes, env);
        }
    }

    private void tick(int times) {
        tick(times, Environment.NONE);
    }

    @Nested
    class Enrichment {
        @BeforeEach
        void setUp() {
            machine(Machines.ENRICHMENT_CHAMBER);
            state.energy.setStored(20_000);
        }

        @Test
        void completesAfter200TicksUsing50JPerTick() {
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 1);
            int out = def.slot(MachineDefinition.SlotRole.OUTPUT);

            tick(199);
            assertEquals(0, inv.count(out));
            assertEquals(199, state.progress);
            assertTrue(state.active);

            tick(1);
            assertEquals(2, inv.count(out));
            assertEquals(DUST_OSMIUM, inv.itemId(out));
            assertEquals(0, inv.count(def.slot(MachineDefinition.SlotRole.INPUT)));
            assertEquals(0, state.progress);
            assertEquals(20_000 - 200 * 50, state.energy.getStored());
            assertEquals(200, state.ticksRequired);
        }

        @Test
        void consumesOneInputPerOperationAndKeepsGoing() {
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 2);
            tick(400);
            assertEquals(4, inv.count(def.slot(MachineDefinition.SlotRole.OUTPUT)));
            assertEquals(0, inv.count(def.slot(MachineDefinition.SlotRole.INPUT)));
        }

        @Test
        void doesNothingWithoutEnergy() {
            state.energy.setStored(0);
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 1);
            tick(50);
            assertEquals(0, state.progress);
            assertFalse(state.active);
        }

        @Test
        void pausesWhenEnergyRunsOutAndResumes() {
            state.energy.setStored(50 * 10);
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 1);
            tick(30);
            assertEquals(10, state.progress);
            assertFalse(state.active);
            state.energy.setStored(20_000);
            tick(1);
            assertEquals(11, state.progress);
            assertTrue(state.active);
        }

        @Test
        void doesNothingWithoutInput() {
            tick(10);
            assertEquals(0, state.progress);
            assertFalse(state.active);
            assertEquals(20_000, state.energy.getStored());
        }

        @Test
        void stopsWhenTheOutputIsFull() {
            int out = def.slot(MachineDefinition.SlotRole.OUTPUT);
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 5);
            inv.set(out, DUST_OSMIUM, MAX_STACK - 1);

            tick(200);
            assertEquals(MAX_STACK - 1, inv.count(out), "needs room for 2, only 1 left");
            assertEquals(0, state.progress);
            assertFalse(state.active);
            assertEquals(20_000, state.energy.getStored(), "no energy used while blocked");

            inv.set(out, DUST_OSMIUM, MAX_STACK - 2);
            tick(200);
            assertEquals(MAX_STACK, inv.count(out));
        }

        @Test
        void stopsWhenTheOutputHoldsADifferentItem() {
            int out = def.slot(MachineDefinition.SlotRole.OUTPUT);
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 1);
            inv.set(out, "Something_Else", 1);
            tick(250);
            assertEquals(1, inv.count(out));
            assertEquals(1, inv.count(def.slot(MachineDefinition.SlotRole.INPUT)));
        }

        @Test
        void progressResetsWhenTheInputChangesToAnotherRecipe() {
            int in = def.slot(MachineDefinition.SlotRole.INPUT);
            inv.set(in, RAW_OSMIUM, 1);
            tick(50);
            assertEquals(50, state.progress);

            inv.set(in, RAW_TIN, 1);
            tick(1);
            assertEquals(1, state.progress, "reset to 0 on the change, then one tick of the new recipe");
            assertEquals("raw_tin_to_dust", state.recipeId);
        }

        @Test
        void progressResetsWhenTheInputIsRemoved() {
            int in = def.slot(MachineDefinition.SlotRole.INPUT);
            inv.set(in, RAW_OSMIUM, 1);
            tick(50);
            inv.set(in, null, 0);
            tick(1);
            assertEquals(0, state.progress);
            assertNull(state.recipeId);
        }

        @Test
        void eightSpeedUpgradesFinishIn20Ticks() {
            int[] upgrades = def.slotsOf(MachineDefinition.SlotRole.UPGRADE);
            inv.set(upgrades[0], Machines.SPEED_UPGRADE, 8);
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 1);
            int out = def.slot(MachineDefinition.SlotRole.OUTPUT);

            // 50 * 10^2 = 5000 J/t, more than the 20 kJ buffer lasts: refill every tick
            for (int i = 0; i < 19; i++) {
                state.energy.setStored(20_000);
                tick(1);
            }
            assertEquals(0, inv.count(out));
            assertEquals(20, state.ticksRequired);

            state.energy.setStored(20_000);
            tick(1);
            assertEquals(2, inv.count(out));
            assertEquals(20_000 - 5000, state.energy.getStored());
        }

        @Test
        void speedAndEnergyUpgradesTogetherUse500JPerTickFromABiggerBuffer() {
            int[] upgrades = def.slotsOf(MachineDefinition.SlotRole.UPGRADE);
            inv.set(upgrades[0], Machines.SPEED_UPGRADE, 8);
            inv.set(upgrades[1], Machines.ENERGY_UPGRADE, 8);
            inv.set(def.slot(MachineDefinition.SlotRole.INPUT), RAW_OSMIUM, 1);
            state.energy.setCapacity(200_000);
            state.energy.setStored(200_000);

            tick(20);

            assertEquals(2, inv.count(def.slot(MachineDefinition.SlotRole.OUTPUT)));
            assertEquals(200_000, state.energy.getCapacity());
            assertEquals(200_000 - 20 * 500, state.energy.getStored());
        }

        @Test
        void upgradeCountsAreCappedAt8() {
            int[] upgrades = def.slotsOf(MachineDefinition.SlotRole.UPGRADE);
            inv.set(upgrades[0], Machines.SPEED_UPGRADE, 8);
            inv.set(upgrades[1], Machines.SPEED_UPGRADE, 8);
            assertEquals(8, MachineLogic.countUpgrades(def, inv, Machines.SPEED_UPGRADE));
            assertEquals(0, MachineLogic.countUpgrades(def, inv, Machines.ENERGY_UPGRADE));
        }

        @Test
        void energyUpgradesRaiseTheBufferCapacityOnTick() {
            inv.set(def.slotsOf(MachineDefinition.SlotRole.UPGRADE)[0], Machines.ENERGY_UPGRADE, 8);
            tick(1);
            assertEquals(200_000, state.energy.getCapacity());
        }
    }

    @Nested
    class Infuser {
        private int in;
        private int extra;
        private int out;

        @BeforeEach
        void setUp() {
            machine(Machines.METALLURGIC_INFUSER);
            state.energy.setStored(20_000);
            in = def.slot(MachineDefinition.SlotRole.INPUT);
            extra = def.slot(MachineDefinition.SlotRole.EXTRA);
            out = def.slot(MachineDefinition.SlotRole.OUTPUT);
        }

        @Test
        void charcoalInTheExtraSlotFillsTheCarbonBuffer() {
            inv.set(extra, "Ingredient_Charcoal", 3);

            tick(1);
            assertEquals("carbon", state.secondaryType);
            assertEquals(10, state.secondaryStored);
            assertEquals(2, inv.count(extra));

            tick(2);
            assertEquals(30, state.secondaryStored);
            assertEquals(0, inv.count(extra));
        }

        @Test
        void stopsFillingAtTheBufferCapacity() {
            state.secondaryType = "carbon";
            state.secondaryStored = def.secondaryCapacity() - 5;
            inv.set(extra, "Ingredient_Charcoal", 2);
            tick(5);
            assertEquals(def.secondaryCapacity() - 5, state.secondaryStored);
            assertEquals(2, inv.count(extra));
        }

        @Test
        void refusesASecondInfuseTypeWhileOneIsStored() {
            inv.set(extra, "Ingredient_Charcoal", 1);
            tick(1);
            assertEquals("carbon", state.secondaryType);

            inv.set(extra, "Techtale_Dust_Copper", 4);
            tick(5);
            assertEquals("carbon", state.secondaryType);
            assertEquals(10, state.secondaryStored);
            assertEquals(4, inv.count(extra), "copper stays in the slot");
        }

        @Test
        void acceptsAnotherTypeOnceTheBufferIsEmpty() {
            state.secondaryType = null;
            state.secondaryStored = 0;
            inv.set(extra, "Techtale_Dust_Copper", 1);
            tick(1);
            assertEquals("copper", state.secondaryType);
            assertEquals(10, state.secondaryStored);
        }

        @Test
        void infusesIronWithCarbonIntoEnrichedIron() {
            state.secondaryType = "carbon";
            state.secondaryStored = 25;
            inv.set(in, "Ingredient_Bar_Iron", 1);

            tick(200);

            assertEquals("Techtale_Enriched_Iron", inv.itemId(out));
            assertEquals(1, inv.count(out));
            assertEquals(15, state.secondaryStored);
            assertEquals("carbon", state.secondaryType);
        }

        @Test
        void infusesIronWithCopperIntoInfusedAlloyAndClearsTheTypeWhenEmpty() {
            state.secondaryType = "copper";
            state.secondaryStored = 10;
            inv.set(in, "Ingredient_Bar_Iron", 1);

            tick(200);

            assertEquals("Techtale_Alloy_Infused", inv.itemId(out));
            assertEquals(0, state.secondaryStored);
            assertNull(state.secondaryType);
        }

        @Test
        void waitsWhenTheBufferHoldsLessThanTheRecipeNeeds() {
            state.secondaryType = "carbon";
            state.secondaryStored = 5;
            inv.set(in, "Ingredient_Bar_Iron", 1);
            tick(250);
            assertEquals(0, inv.count(out));
            assertEquals(0, state.progress);
        }

        @Test
        void doesNothingWithoutAStoredInfuseType() {
            inv.set(in, "Ingredient_Bar_Iron", 1);
            tick(250);
            assertEquals(0, inv.count(out));
        }
    }

    @Nested
    class HeatGenerator {
        private final Environment env = new Environment(false, id -> "Ingredient_Charcoal".equals(id) ? 100 : 0);
        private int fuel;

        @BeforeEach
        void setUp() {
            machine(Machines.HEAT_GENERATOR_DEF);
            fuel = def.slot(MachineDefinition.SlotRole.FUEL);
        }

        @Test
        void burnsFuelForTheTicksTheEnvironmentReports() {
            inv.set(fuel, "Ingredient_Charcoal", 2);

            tick(1, env);
            assertEquals(1, inv.count(fuel));
            assertEquals(200, state.energy.getStored());
            assertEquals(99, state.burnTicks);
            assertEquals(100, state.burnTotal);
            assertTrue(state.active);

            tick(99, env);
            assertEquals(100 * 200, state.energy.getStored());
            assertEquals(1, inv.count(fuel), "second item not taken until the first has burnt out");
            assertEquals(0, state.burnTicks);

            tick(1, env);
            assertEquals(0, inv.count(fuel));
            assertEquals(101 * 200, state.energy.getStored());
        }

        @Test
        void goesIdleWhenTheFuelRunsOut() {
            inv.set(fuel, "Ingredient_Charcoal", 1);
            tick(100, env);
            tick(1, env);
            assertEquals(100 * 200, state.energy.getStored());
            assertFalse(state.active);
        }

        @Test
        void ignoresItemsThatAreNotFuel() {
            inv.set(fuel, "Rock_Stone", 5);
            tick(10, env);
            assertEquals(0, state.energy.getStored());
            assertEquals(5, inv.count(fuel));
            assertFalse(state.active);
        }

        @Test
        void doesNotTakeFuelWhenThereIsNoRoomForAFullTick() {
            state.energy.setStored(state.energy.getCapacity() - 100);
            inv.set(fuel, "Ingredient_Charcoal", 1);
            tick(5, env);
            assertEquals(1, inv.count(fuel));
            assertFalse(state.active);
            assertEquals(state.energy.getCapacity() - 100, state.energy.getStored());
        }

        @Test
        void stopsAddingEnergyWhenFull() {
            inv.set(fuel, "Ingredient_Charcoal", 1);
            tick(1, env);
            state.energy.setStored(state.energy.getCapacity());

            tick(10, env);

            assertEquals(state.energy.getCapacity(), state.energy.getStored());
        }
    }

    @Nested
    class Solar {
        @BeforeEach
        void setUp() {
            machine(Machines.SOLAR_GENERATOR_DEF);
        }

        @Test
        void producesOnlyWhenTheSunIsVisible() {
            tick(10, new Environment(false, id -> 0));
            assertEquals(0, state.energy.getStored());
            assertFalse(state.active);

            tick(10, new Environment(true, id -> 0));
            assertEquals(10 * 50, state.energy.getStored());
            assertTrue(state.active);

            tick(1, new Environment(false, id -> 0));
            assertEquals(10 * 50, state.energy.getStored());
            assertFalse(state.active);
        }

        @Test
        void stopsWhenFull() {
            state.energy.setStored(state.energy.getCapacity() - 30);
            Environment sun = new Environment(true, id -> 0);

            tick(1, sun);
            assertEquals(state.energy.getCapacity(), state.energy.getStored());

            tick(1, sun);
            assertEquals(state.energy.getCapacity(), state.energy.getStored());
            assertFalse(state.active);
        }
    }
}
