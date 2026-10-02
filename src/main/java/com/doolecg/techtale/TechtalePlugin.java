package com.doolecg.techtale;

import com.doolecg.techtale.core.recipe.RecipeLoader;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import com.doolecg.techtale.hytale.SmeltingRecipes;
import com.doolecg.techtale.hytale.energy.CableBlock;
import com.doolecg.techtale.hytale.energy.EnergySystems;
import com.doolecg.techtale.hytale.energy.EnergyWorld;
import com.doolecg.techtale.hytale.machine.MachineBlock;
import com.doolecg.techtale.hytale.machine.MachineSystems;
import com.doolecg.techtale.hytale.machine.OpenMachineInteraction;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.io.IOException;
import javax.annotation.Nonnull;

public class TechtalePlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static TechtalePlugin instance;

    private ComponentType<ChunkStore, MachineBlock> machineComponentType;
    private ComponentType<ChunkStore, CableBlock> cableComponentType;
    private ResourceType<ChunkStore, EnergyWorld> energyWorldType;
    private RecipeRegistry recipes;

    public TechtalePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    public static TechtalePlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        instance = this;
        try {
            recipes = RecipeLoader.loadBuiltIn(getClass().getClassLoader());
        } catch (IOException e) {
            throw new IllegalStateException("Could not load Techtale recipes", e);
        }
        LOGGER.atInfo().log("Techtale %s: %d machine recipes", getManifest().getVersion(), recipes.size());

        var chunkRegistry = getChunkStoreRegistry();
        machineComponentType = chunkRegistry.registerComponent(MachineBlock.class, MachineBlock.ID, MachineBlock.CODEC);
        cableComponentType = chunkRegistry.registerComponent(CableBlock.class, CableBlock.ID, CableBlock.CODEC);
        energyWorldType = chunkRegistry.registerResource(EnergyWorld.class, EnergyWorld::new);

        chunkRegistry.registerSystem(new MachineSystems.Lifecycle(machineComponentType));
        chunkRegistry.registerSystem(new MachineSystems.Tick(machineComponentType));
        chunkRegistry.registerSystem(new EnergySystems.CableLifecycle(cableComponentType));
        chunkRegistry.registerSystem(new EnergySystems.NetworkTick());

        getCommandRegistry().registerCommand(new com.doolecg.techtale.hytale.command.TechtaleCommand());
        getCodecRegistry(Interaction.CODEC).register(OpenMachineInteraction.TYPE, OpenMachineInteraction.class, OpenMachineInteraction.CODEC);
    }

    @Override
    protected void start() {
        SmeltingRecipes.importFurnaceRecipes(recipes);
        LOGGER.atInfo().log("Techtale started: %d machine recipes including smelting", recipes.size());
    }

    public ComponentType<ChunkStore, MachineBlock> getMachineComponentType() {
        return machineComponentType;
    }

    public ComponentType<ChunkStore, CableBlock> getCableComponentType() {
        return cableComponentType;
    }

    public ResourceType<ChunkStore, EnergyWorld> getEnergyWorldType() {
        return energyWorldType;
    }

    public RecipeRegistry getRecipes() {
        return recipes;
    }
}
