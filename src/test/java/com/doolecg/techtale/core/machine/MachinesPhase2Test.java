package com.doolecg.techtale.core.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doolecg.techtale.core.resource.RelativeSide;
import com.doolecg.techtale.core.resource.ResourceKind;
import org.junit.jupiter.api.Test;

class MachinesPhase2Test {
    @Test
    void fluidTankRegisteredWithOneTank() {
        MachineDefinition def = Machines.get("Techtale_Fluid_Tank");
        assertNotNull(def);
        assertEquals(MachineDefinition.Kind.TANK, def.kind());
        assertEquals(1, def.tanks().size());
        assertEquals(ResourceKind.FLUID, def.tanks().get(0).kind());
        assertEquals(14_000, def.tanks().get(0).capacity());
        assertTrue(def.tanks().get(0).outputSides().contains(RelativeSide.BOTTOM));
        assertTrue(def.slots().isEmpty());
        MachineState state = MachineState.forDefinition(def);
        assertEquals(1, state.tanks.length);
        assertEquals(14_000, state.tanks[0].getCapacity());
    }

    @Test
    void chemicalTankAndPump() {
        assertEquals(64_000, Machines.get("Techtale_Chemical_Tank").tanks().get(0).capacity());
        MachineDefinition pump = Machines.get("Techtale_Electric_Pump");
        assertEquals(MachineDefinition.Kind.PUMP, pump.kind());
        assertEquals(60, pump.energyPerTick());
        assertEquals(20, pump.ticksPerOperation());
        assertEquals(20_000, pump.energyCapacity());
        assertEquals(10_000, MachineState.forDefinition(pump).tanks[0].getCapacity());
        assertFalse(pump.tanks().get(0).outputSides().contains(RelativeSide.BOTTOM));
    }

    @Test
    void separatorHasThreeTanks() {
        MachineDefinition def = Machines.get("Techtale_Electrolytic_Separator");
        assertNotNull(def);
        assertEquals(MachineDefinition.Kind.CHEMICAL_PROCESSOR, def.kind());
        assertEquals("electrolysis", def.chemicalRecipe());
        assertEquals(3, MachineState.forDefinition(def).tanks.length);
        assertEquals("hydrogen", def.tanks().get(1).fixedType());
        assertEquals("oxygen", def.tanks().get(2).fixedType());
        assertTrue(def.tanks().get(1).outputSides().contains(RelativeSide.LEFT));
        assertTrue(def.tanks().get(2).outputSides().contains(RelativeSide.RIGHT));
    }

    @Test
    void oldConstructorHasNoTanks() {
        assertTrue(Machines.CRUSHER.tanks().isEmpty());
        assertNull(Machines.CRUSHER.chemicalRecipe());
        assertEquals(0, MachineState.forDefinition(Machines.CRUSHER).tanks.length);
    }
}
