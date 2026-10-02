package com.doolecg.techtale.hytale.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doolecg.techtale.core.machine.MachineDefinition.SlotRole;
import com.doolecg.techtale.core.machine.Machines;
import com.doolecg.techtale.core.recipe.ItemIngredient;
import com.doolecg.techtale.core.recipe.ItemOutput;
import com.doolecg.techtale.core.recipe.MachineRecipe;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import com.doolecg.techtale.core.recipe.RecipeType;
import com.doolecg.techtale.core.recipe.SecondaryConversion;
import org.junit.jupiter.api.Test;

class MachineSlotRulesTest {
    private static RecipeRegistry registry() {
        RecipeRegistry r = new RecipeRegistry();
        r.add(new MachineRecipe("crush_ore", RecipeType.CRUSHING, ItemIngredient.of("Ore", 1), null, null, 0, new ItemOutput("Dust", 2)));
        r.add(new MachineRecipe("combine", RecipeType.COMBINING, ItemIngredient.of("Dust", 1), ItemIngredient.of("Cobble", 1), null, 0, new ItemOutput("Ore", 1)));
        r.addConversion(new SecondaryConversion("Charcoal", "carbon", 10));
        r.addConversion(new SecondaryConversion("Osmium_Dust", "osmium", 100));
        return r;
    }

    @Test
    void inputOnlyAcceptsRecipeInputs() {
        RecipeRegistry r = registry();
        assertTrue(MachineSlotRules.accepts(Machines.CRUSHER, SlotRole.INPUT, "Ore", r, id -> false));
        assertFalse(MachineSlotRules.accepts(Machines.CRUSHER, SlotRole.INPUT, "Dust", r, id -> false));
    }

    @Test
    void extraDependsOnMachine() {
        RecipeRegistry r = registry();
        assertTrue(MachineSlotRules.accepts(Machines.COMBINER, SlotRole.EXTRA, "Cobble", r, id -> false));
        assertFalse(MachineSlotRules.accepts(Machines.COMBINER, SlotRole.EXTRA, "Charcoal", r, id -> false));
        assertTrue(MachineSlotRules.accepts(Machines.METALLURGIC_INFUSER, SlotRole.EXTRA, "Charcoal", r, id -> false));
        assertTrue(MachineSlotRules.accepts(Machines.OSMIUM_COMPRESSOR, SlotRole.EXTRA, "Osmium_Dust", r, id -> false));
        assertFalse(MachineSlotRules.accepts(Machines.OSMIUM_COMPRESSOR, SlotRole.EXTRA, "Charcoal", r, id -> false));
    }

    @Test
    void fuelUpgradeAndOutput() {
        RecipeRegistry r = registry();
        assertTrue(MachineSlotRules.accepts(Machines.HEAT_GENERATOR_DEF, SlotRole.FUEL, "Coal", r, "Coal"::equals));
        assertFalse(MachineSlotRules.accepts(Machines.HEAT_GENERATOR_DEF, SlotRole.FUEL, "Stone", r, "Coal"::equals));
        assertTrue(MachineSlotRules.accepts(Machines.CRUSHER, SlotRole.UPGRADE, Machines.SPEED_UPGRADE, r, id -> false));
        assertTrue(MachineSlotRules.accepts(Machines.CRUSHER, SlotRole.UPGRADE, Machines.ENERGY_UPGRADE, r, id -> false));
        assertFalse(MachineSlotRules.accepts(Machines.CRUSHER, SlotRole.UPGRADE, "Ore", r, id -> false));
        assertFalse(MachineSlotRules.accepts(Machines.CRUSHER, SlotRole.OUTPUT, "Dust", r, id -> false));
    }
}
