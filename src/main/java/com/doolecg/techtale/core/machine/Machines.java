package com.doolecg.techtale.core.machine;

import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.CHEMICAL_PROCESSOR;
import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.ENERGY_CUBE;
import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.PUMP;
import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.TANK;
import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.HEAT_GENERATOR;
import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.PROCESSOR;
import static com.doolecg.techtale.core.machine.MachineDefinition.Kind.SOLAR_GENERATOR;
import static com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.EXTRA;
import static com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.FUEL;
import static com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.INPUT;
import static com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.OUTPUT;
import static com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.UPGRADE;

import com.doolecg.techtale.core.recipe.RecipeType;
import com.doolecg.techtale.core.resource.RelativeSide;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.core.resource.TankSpec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Every machine type, keyed by block id. Numbers follow Mekanism's defaults (per 1/20 s tick). */
public final class Machines {
    public static final String SPEED_UPGRADE = "Techtale_Upgrade_Speed";
    public static final String ENERGY_UPGRADE = "Techtale_Upgrade_Energy";

    private static final Map<String, MachineDefinition> BY_ID = new LinkedHashMap<>();

    public static final MachineDefinition ENRICHMENT_CHAMBER = processor("Techtale_Enrichment_Chamber", RecipeType.ENRICHING, 50);
    public static final MachineDefinition CRUSHER = processor("Techtale_Crusher", RecipeType.CRUSHING, 50);
    public static final MachineDefinition ENERGIZED_SMELTER = processor("Techtale_Energized_Smelter", RecipeType.SMELTING, 50);
    public static final MachineDefinition OSMIUM_COMPRESSOR = register(new MachineDefinition(
        "Techtale_Osmium_Compressor", PROCESSOR, RecipeType.COMPRESSING, 100, 200, 20_000, 0, 400, "osmium",
        List.of(INPUT, EXTRA, OUTPUT, UPGRADE, UPGRADE)));
    public static final MachineDefinition COMBINER = register(new MachineDefinition(
        "Techtale_Combiner", PROCESSOR, RecipeType.COMBINING, 50, 200, 20_000, 0, 0, null,
        List.of(INPUT, EXTRA, OUTPUT, UPGRADE, UPGRADE)));
    public static final MachineDefinition METALLURGIC_INFUSER = register(new MachineDefinition(
        "Techtale_Metallurgic_Infuser", PROCESSOR, RecipeType.INFUSING, 50, 200, 20_000, 0, 1000, null,
        List.of(INPUT, EXTRA, OUTPUT, UPGRADE, UPGRADE)));

    public static final MachineDefinition HEAT_GENERATOR_DEF = register(new MachineDefinition(
        "Techtale_Heat_Generator", HEAT_GENERATOR, null, 200, 0, 160_000, 400, 0, null, List.of(FUEL)));
    public static final MachineDefinition SOLAR_GENERATOR_DEF = register(new MachineDefinition(
        "Techtale_Solar_Generator", SOLAR_GENERATOR, null, 50, 0, 96_000, 100, 0, null, List.of()));

    public static final MachineDefinition BASIC_ENERGY_CUBE = cube("Techtale_Energy_Cube_Basic", 4_000_000L, 4_000);
    public static final MachineDefinition ADVANCED_ENERGY_CUBE = cube("Techtale_Energy_Cube_Advanced", 16_000_000L, 16_000);
    public static final MachineDefinition ELITE_ENERGY_CUBE = cube("Techtale_Energy_Cube_Elite", 64_000_000L, 64_000);
    public static final MachineDefinition ULTIMATE_ENERGY_CUBE = cube("Techtale_Energy_Cube_Ultimate", 256_000_000L, 256_000);

    private static final Set<RelativeSide> ALL_SIDES = Set.of(RelativeSide.values());
    private static final Set<RelativeSide> NOT_BOTTOM = Set.of(
        RelativeSide.TOP, RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT);

    public static final MachineDefinition FLUID_TANK = register(new MachineDefinition(
        "Techtale_Fluid_Tank", TANK, null, 0, 0, 0, 0, 0, null, List.of(),
        List.of(new TankSpec(ResourceKind.FLUID, 14_000, NOT_BOTTOM, Set.of(RelativeSide.BOTTOM), null)), null));
    public static final MachineDefinition CHEMICAL_TANK = register(new MachineDefinition(
        "Techtale_Chemical_Tank", TANK, null, 0, 0, 0, 0, 0, null, List.of(),
        List.of(new TankSpec(ResourceKind.CHEMICAL, 64_000, NOT_BOTTOM, Set.of(RelativeSide.BOTTOM), null)), null));
    public static final MachineDefinition ELECTRIC_PUMP = register(new MachineDefinition(
        "Techtale_Electric_Pump", PUMP, null, 60, 20, 20_000, 0, 0, null, List.of(),
        List.of(new TankSpec(ResourceKind.FLUID, 10_000, Set.of(), NOT_BOTTOM, null)), null));
    public static final MachineDefinition ELECTROLYTIC_SEPARATOR = register(new MachineDefinition(
        "Techtale_Electrolytic_Separator", CHEMICAL_PROCESSOR, null, 200, 1, 20_000, 0, 0, null, List.of(),
        List.of(
            new TankSpec(ResourceKind.FLUID, 10_000, ALL_SIDES, Set.of(), null),
            new TankSpec(ResourceKind.CHEMICAL, 2_400, Set.of(), Set.of(RelativeSide.LEFT), "hydrogen"),
            new TankSpec(ResourceKind.CHEMICAL, 2_400, Set.of(), Set.of(RelativeSide.RIGHT), "oxygen")),
        "electrolysis"));

    private Machines() {
    }

    private static MachineDefinition processor(String id, RecipeType type, long energyPerTick) {
        return register(new MachineDefinition(id, PROCESSOR, type, energyPerTick, 200, 20_000, 0, 0, null,
            List.of(INPUT, OUTPUT, UPGRADE, UPGRADE)));
    }

    private static MachineDefinition cube(String id, long capacity, long output) {
        return register(new MachineDefinition(id, ENERGY_CUBE, null, 0, 0, capacity, output, 0, null, List.of()));
    }

    private static MachineDefinition register(MachineDefinition def) {
        BY_ID.put(def.id(), def);
        return def;
    }

    @Nullable
    public static MachineDefinition get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<MachineDefinition> all() {
        return BY_ID.values();
    }
}
