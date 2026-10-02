package com.doolecg.techtale.hytale.debug;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.machine.MachineState;
import com.doolecg.techtale.hytale.energy.EnergyWorld;
import com.doolecg.techtale.core.network.EnergyNetwork;
import com.doolecg.techtale.hytale.machine.MachineBlock;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Builds a small test rig high above (0, 0) and checks it works end to end, on the real server, without a player:
 * heat generator -> two basic cables -> enrichment chamber turning raw osmium into dust, with an energy cube on the
 * first cable charging from the same network. Run with {@code /techtale selftest}.
 */
public final class SelfTest {
    /** Where the console runs it: high above the origin, clear of terrain. */
    public static final int CONSOLE_X = 4;
    public static final int CONSOLE_Y = 250;
    public static final int CONSOLE_Z = 4;
    private static final int SECONDS = 20;

    private final int x;
    private final int y;
    private final int z;

    private SelfTest(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Builds the rig with the generator at {@code x, y, z}, running east (+x), and checks it after a while. */
    public static CompletableFuture<Boolean> run(World world, int x, int y, int z, Consumer<String> log) {
        return new SelfTest(x, y, z).run(world, log);
    }

    private CompletableFuture<Boolean> run(World world, Consumer<String> log) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        world.getChunkAsync(ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z)).thenAcceptAsync(chunk -> {
            try {
                chunk.addKeepLoaded();
                EnergyWorld.of(world.getChunkStore().getStore()).forcedChunks.add(chunk.getIndex());
                build(world);
                log.accept("Self-test rig placed at " + x + "," + y + "," + z + "; waiting " + SECONDS + " s");
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, world).execute(() -> fill(world, log));
                CompletableFuture.delayedExecutor(SECONDS, TimeUnit.SECONDS, world).execute(() -> {
                    try {
                        result.complete(check(world, log));
                    } catch (Throwable t) {
                        log.accept("Self-test crashed: " + t);
                        result.complete(false);
                    } finally {
                        EnergyWorld.of(world.getChunkStore().getStore()).forcedChunks.remove(chunk.getIndex());
                        chunk.removeKeepLoaded();
                    }
                });
            } catch (Throwable t) {
                log.accept("Self-test crashed: " + t);
                chunk.removeKeepLoaded();
                result.complete(false);
            }
        }, world).exceptionally(t -> {
            log.accept("Self-test could not load its chunk: " + t);
            result.complete(false);
            return null;
        });
        return result;
    }

    private void build(World world) {
        for (int dx = -1; dx <= 6; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    world.setBlock(x + dx, y + dy, z + dz, "Empty");
                }
            }
        }
        world.setBlock(x, y, z, "Techtale_Heat_Generator");
        world.setBlock(x + 1, y, z, "Techtale_Cable_Basic");
        world.setBlock(x + 2, y, z, "Techtale_Cable_Basic");
        world.setBlock(x + 3, y, z, "Techtale_Enrichment_Chamber");
        // The cube sits on the first cable and takes energy through its bottom face.
        world.setBlock(x + 1, y + 1, z, "Techtale_Energy_Cube_Basic");
    }

    private static MachineBlock machine(World world, int x, int y, int z) {
        return BlockModule.getComponent(MachineBlock.getComponentType(), world, x, y, z);
    }

    private void fill(World world, Consumer<String> log) {
        MachineBlock gen = machine(world, x, y, z);
        MachineBlock chamber = machine(world, x + 3, y, z);
        if (gen == null || chamber == null) {
            log.accept("Self-test: machine block entities missing (gen=" + gen + ", chamber=" + chamber + ")");
            return;
        }
        gen.getItems().setItemStackForSlot((short) gen.getDefinition().slot(com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.FUEL),
            new ItemStack("Ingredient_Charcoal", 16), false);
        chamber.getItems().setItemStackForSlot((short) chamber.getDefinition().slot(com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.INPUT),
            new ItemStack("Techtale_Raw_Osmium", 8), false);
        log.accept("Self-test: fuel and ore inserted");
    }

    private boolean check(World world, Consumer<String> log) {
        List<String> failures = new ArrayList<>();
        MachineBlock gen = machine(world, x, y, z);
        MachineBlock chamber = machine(world, x + 3, y, z);
        MachineBlock cube = machine(world, x + 1, y + 1, z);
        if (gen == null || chamber == null || cube == null) {
            log.accept("Self-test FAILED: block entities missing (gen=" + gen + ", chamber=" + chamber + ", cube=" + cube + ")");
            return false;
        }
        MachineState gs = gen.getState();
        MachineState cs = chamber.getState();
        log.accept(String.format("generator: energy %d, burn %d/%d, active %s", gs.energy.getStored(), gs.burnTicks, gs.burnTotal, gs.active));
        int outSlot = chamber.getDefinition().slot(com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.OUTPUT);
        int inSlot = chamber.getDefinition().slot(com.doolecg.techtale.core.machine.MachineDefinition.SlotRole.INPUT);
        ItemStack out = chamber.getItems().getItemStack((short) outSlot);
        ItemStack in = chamber.getItems().getItemStack((short) inSlot);
        log.accept(String.format("chamber: energy %d, progress %d/%d, input %s, output %s", cs.energy.getStored(), cs.progress, cs.ticksRequired, in, out));
        log.accept(String.format("cube: energy %d", cube.getState().energy.getStored()));
        EnergyWorld ew = EnergyWorld.of(world.getChunkStore().getStore());
        EnergyNetwork net = ew.cables.networkAt(BlockPos.pack(x + 1, y, z));
        log.accept("cable network: " + (net == null ? "none" : net.size() + " cables, " + net.getStored() + "/" + net.getCapacity() + " J"));
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        log.accept("chunk ticking: " + (chunk != null && chunk.is(com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag.TICKING)));

        if (gs.burnTotal <= 0) {
            failures.add("generator never burned fuel");
        }
        if (net == null || net.size() != 2) {
            failures.add("cables X+1..X+2 did not form one 2-cable network");
        }
        if (ItemStack.isEmpty(out) || !"Techtale_Dust_Osmium".equals(out.getItemId()) || out.getQuantity() < 2) {
            failures.add("chamber produced no osmium dust");
        }
        if (cube.getState().energy.getStored() <= 0) {
            failures.add("energy cube received no energy");
        }
        if (failures.isEmpty()) {
            log.accept("Self-test PASSED");
            return true;
        }
        log.accept("Self-test FAILED: " + String.join("; ", failures));
        return false;
    }
}
