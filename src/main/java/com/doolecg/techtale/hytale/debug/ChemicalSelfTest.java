package com.doolecg.techtale.hytale.debug;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.machine.MachineDefinition;
import com.doolecg.techtale.core.network.ResourceNetwork;
import com.doolecg.techtale.core.resource.RelativeSide;
import com.doolecg.techtale.core.resource.ResourceBuffer;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.hytale.energy.EnergyWorld;
import com.doolecg.techtale.hytale.machine.MachineBlock;
import com.doolecg.techtale.hytale.resource.ResourceWorld;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Phase 2 rig: water tank -> two pipes -> electrolytic separator (heat generator beside it) -> a chemical tank on each
 * of its hydrogen and oxygen faces. Checks water drains and hydrogen and oxygen arrive at about 2:1.
 */
public final class ChemicalSelfTest {
    private static final int SECONDS = 20;
    private static final long WATER = 4000;

    private final int x;
    private final int y;
    private final int z;

    private ChemicalSelfTest(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Builds the rig with the separator at {@code x, y, z} and checks it after a while. */
    public static CompletableFuture<Boolean> run(World world, int x, int y, int z, Consumer<String> log) {
        return new ChemicalSelfTest(x, y, z).run(world, log);
    }

    private CompletableFuture<Boolean> run(World world, Consumer<String> log) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        world.getChunkAsync(ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z)).thenAcceptAsync(chunk -> {
            try {
                chunk.addKeepLoaded();
                EnergyWorld.of(world.getChunkStore().getStore()).forcedChunks.add(chunk.getIndex());
                build(world);
                log.accept("Chemical self-test rig placed at " + x + "," + y + "," + z + "; waiting " + SECONDS + " s");
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, world).execute(() -> fill(world, log));
                CompletableFuture.delayedExecutor(SECONDS, TimeUnit.SECONDS, world).execute(() -> {
                    try {
                        result.complete(check(world, log));
                    } catch (Throwable t) {
                        log.accept("Chemical self-test crashed: " + t);
                        result.complete(false);
                    } finally {
                        EnergyWorld.of(world.getChunkStore().getStore()).forcedChunks.remove(chunk.getIndex());
                        chunk.removeKeepLoaded();
                    }
                });
            } catch (Throwable t) {
                log.accept("Chemical self-test crashed: " + t);
                chunk.removeKeepLoaded();
                result.complete(false);
            }
        }, world).exceptionally(t -> {
            log.accept("Chemical self-test could not load its chunk: " + t);
            result.complete(false);
            return null;
        });
        return result;
    }

    // Blocks are placed unrotated, so every front is NORTH: LEFT is WEST (hydrogen), RIGHT is EAST (oxygen).
    private int hx() {
        return x + RelativeSide.LEFT.resolve(Direction.NORTH).dx;
    }

    private int ox() {
        return x + RelativeSide.RIGHT.resolve(Direction.NORTH).dx;
    }

    private void build(World world) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -3; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    world.setBlock(x + dx, y + dy, z + dz, "Empty");
                }
            }
        }
        world.setBlock(x, y, z, "Techtale_Electrolytic_Separator");
        world.setBlock(x, y, z + 1, "Techtale_Heat_Generator");
        world.setBlock(hx(), y, z, "Techtale_Chemical_Tank");
        world.setBlock(ox(), y, z, "Techtale_Chemical_Tank");
        world.setBlock(x, y, z - 1, "Techtale_Mechanical_Pipe");
        world.setBlock(x, y, z - 2, "Techtale_Mechanical_Pipe");
        world.setBlock(x, y + 1, z - 2, "Techtale_Fluid_Tank");
    }

    private static MachineBlock machine(World world, int x, int y, int z) {
        return BlockModule.getComponent(MachineBlock.getComponentType(), world, x, y, z);
    }

    private void fill(World world, Consumer<String> log) {
        MachineBlock gen = machine(world, x, y, z + 1);
        MachineBlock tank = machine(world, x, y + 1, z - 2);
        if (gen == null || tank == null || tank.tanks().length == 0) {
            log.accept("Chemical self-test: machine block entities missing (gen=" + gen + ", tank=" + tank + ")");
            return;
        }
        gen.getItems().setItemStackForSlot((short) gen.getDefinition().slot(MachineDefinition.SlotRole.FUEL),
            new ItemStack("Ingredient_Charcoal", 16), false);
        tank.tanks()[0].insert("water", WATER, false);
        log.accept("Chemical self-test: fuel and water inserted");
    }

    private static String describe(ResourceBuffer b) {
        return b.getAmount() + " " + b.getType();
    }

    private boolean check(World world, Consumer<String> log) {
        List<String> failures = new ArrayList<>();
        MachineBlock sep = machine(world, x, y, z);
        MachineBlock gen = machine(world, x, y, z + 1);
        MachineBlock tank = machine(world, x, y + 1, z - 2);
        MachineBlock h2 = machine(world, hx(), y, z);
        MachineBlock o2 = machine(world, ox(), y, z);
        if (sep == null || gen == null || tank == null || h2 == null || o2 == null) {
            log.accept("Chemical self-test FAILED: block entities missing (separator=" + sep + ", generator=" + gen + ", tank=" + tank
                + ", hydrogen=" + h2 + ", oxygen=" + o2 + ")");
            return false;
        }
        ResourceNetwork net = ResourceWorld.of(world.getChunkStore().getStore()).graph(ResourceKind.FLUID).networkAt(BlockPos.pack(x, y, z - 1));
        ResourceBuffer water = tank.tanks()[0];
        ResourceBuffer hydrogen = h2.tanks()[0];
        ResourceBuffer oxygen = o2.tanks()[0];
        log.accept("generator: burn " + gen.getState().burnTicks + "/" + gen.getState().burnTotal + ", energy " + gen.getState().energy.getStored());
        log.accept("separator: energy " + sep.getState().energy.getStored() + ", progress " + sep.getState().progress
            + ", water in " + describe(sep.tanks()[0]) + ", hydrogen " + describe(sep.tanks()[1]) + ", oxygen " + describe(sep.tanks()[2]));
        log.accept("pipes: " + (net == null ? "none" : net.size() + " pipes, " + net.getStored() + "/" + net.getCapacity() + " " + net.getType()));
        log.accept("water tank: " + describe(water) + " (started " + WATER + ")");
        log.accept("hydrogen tank: " + describe(hydrogen) + "; oxygen tank: " + describe(oxygen));

        if (gen.getState().burnTotal <= 0) {
            failures.add("generator never burned fuel");
        }
        if (net == null || net.size() != 2) {
            failures.add("pipes did not form one 2-pipe fluid network");
        }
        if (water.getAmount() >= WATER) {
            failures.add("water tank did not drop");
        }
        if (!"hydrogen".equals(hydrogen.getType()) || hydrogen.getAmount() <= 0) {
            failures.add("hydrogen tank holds no hydrogen");
        }
        if (!"oxygen".equals(oxygen.getType()) || oxygen.getAmount() <= 0) {
            failures.add("oxygen tank holds no oxygen");
        }
        if (oxygen.getAmount() > 0 && Math.abs(hydrogen.getAmount() - 2.0 * oxygen.getAmount()) > Math.max(2, 0.15 * hydrogen.getAmount())) {
            failures.add("hydrogen:oxygen is not about 2:1 (" + hydrogen.getAmount() + ":" + oxygen.getAmount() + ")");
        }
        if (failures.isEmpty()) {
            log.accept("Chemical self-test PASSED");
            return true;
        }
        log.accept("Chemical self-test FAILED: " + String.join("; ", failures));
        return false;
    }
}
