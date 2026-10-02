package com.doolecg.techtale.hytale.command;

import com.hypixel.hytale.builtin.hytalegenerator.plugin.HandleProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * /techtale ores [radius] [--x blockX] [--z blockZ]: counts Techtale ore blocks, and vanilla ores for comparison, in the
 * chunks around the sender, or around world spawn from the console. A player only sees chunks that are already loaded;
 * the console first loads (generates) the whole square. --x/--z move the centre (to probe other zones).
 */
public class OresCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int DEFAULT_RADIUS = 3;
    private static final int MAX_RADIUS = 8;
    private static final String TECHTALE = "Techtale_Ore_";
    private static final String[] TOTALS = {TECHTALE, "Ore_Copper_", "Ore_Iron_"};
    /** World structures of the new (V2) generator whose biomes carry Techtale ore; keep in step with tools/gen_v2_ores.py. */
    private static final Set<String> V2_STRUCTURES = Set.of(
        "Zone1_Plains1", "Zone2_Desert1", "Zone3_Taiga1", "Zone4_Volcanic1",
        "Portals_Hedera", "Portals_Henges", "Portals_Jungles", "Portals_Oasis", "Portals_Taiga");

    private final OptionalArg<Integer> xArg = withOptionalArg("x", "Block X of the centre (default: you, or world spawn)", ArgTypes.INTEGER);
    private final OptionalArg<Integer> zArg = withOptionalArg("z", "Block Z of the centre (default: you, or world spawn)", ArgTypes.INTEGER);

    public OresCommand() {
        super("ores", "Counts Techtale and vanilla ore blocks around you (or around spawn from the console). Optional radius in chunks.");
        addUsageVariant(new WithRadius());
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        return run(context, DEFAULT_RADIUS, xArg.provided(context) ? xArg.get(context) : null, zArg.provided(context) ? zArg.get(context) : null);
    }

    private static final class WithRadius extends AbstractAsyncCommand {
        private final RequiredArg<Integer> radius = withRequiredArg("radius", "Chunk radius around the centre (max " + MAX_RADIUS + ")", ArgTypes.INTEGER);
        private final OptionalArg<Integer> xArg = withOptionalArg("x", "Block X of the centre (default: you, or world spawn)", ArgTypes.INTEGER);
        private final OptionalArg<Integer> zArg = withOptionalArg("z", "Block Z of the centre (default: you, or world spawn)", ArgTypes.INTEGER);

        WithRadius() {
            super("Counts Techtale and vanilla ore blocks within a chunk radius.");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
            return run(context, radius.get(context), xArg.provided(context) ? xArg.get(context) : null, zArg.provided(context) ? zArg.get(context) : null);
        }
    }

    private static CompletableFuture<Void> run(CommandContext context, int requestedRadius, @Nullable Integer blockX, @Nullable Integer blockZ) {
        int radius = Math.max(0, Math.min(MAX_RADIUS, requestedRadius));
        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        boolean player = playerRef != null && playerRef.isValid();
        World world = player ? playerRef.getStore().getExternalData().getWorld() : Universe.get().getDefaultWorld();
        if (world == null) {
            context.sendMessage(Message.raw("No world is loaded."));
            return CompletableFuture.completedFuture(null);
        }
        boolean explicitCentre = blockX != null || blockZ != null;
        CompletableFuture<int[]> centre = CompletableFuture.supplyAsync(
            () -> explicitCentre
                ? new int[] {ChunkUtil.chunkCoordinate(blockX == null ? 0 : blockX), ChunkUtil.chunkCoordinate(blockZ == null ? 0 : blockZ)}
                : centreChunk(world, player ? playerRef : null),
            world);
        return centre.thenCompose(c -> {
            CompletableFuture<?> ready = CompletableFuture.completedFuture(null);
            if (!player) {
                List<CompletableFuture<WorldChunk>> loads = new ArrayList<>();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        loads.add(world.getNonTickingChunkAsync(ChunkUtil.indexChunk(c[0] + dx, c[1] + dz)));
                    }
                }
                ready = CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new));
            }
            return ready.thenApplyAsync(ignored -> count(world, c[0], c[1], radius), world);
        }).thenAccept(lines -> {
            if (!generatesOres(world)) {
                context.sendMessage(Message.raw("Note: this world's generator (" + world.getWorldConfig().getWorldGenProvider()
                    + ") does not place ores. Ores generate in the Default generator and in the Zone 1-4 worlds of the new generator."));
            }
            for (String line : lines) {
                context.sendMessage(Message.raw(line));
            }
        }).exceptionally(e -> {
            LOGGER.atSevere().withCause(e).log("techtale ores failed");
            context.sendMessage(Message.raw("techtale ores failed: " + e));
            return null;
        });
    }

    /** Ores are added to Hytale's Default (V1) generator and to the new generator's zone biomes; hubs, instances and flat worlds have none. */
    private static boolean generatesOres(World world) {
        Object provider = world.getWorldConfig().getWorldGenProvider();
        if (provider instanceof HandleProvider v2) {
            return V2_STRUCTURES.contains(v2.getWorldStructureName());
        }
        String generator = String.valueOf(provider);
        return generator.startsWith("HytaleWorldGenProvider") && generator.contains("name='Default'");
    }

    /** Runs on the world thread. */
    private static int[] centreChunk(World world, @Nullable Ref<EntityStore> playerRef) {
        Vector3d pos = null;
        if (playerRef != null) {
            Store<EntityStore> store = playerRef.getStore();
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform != null) {
                pos = transform.getPosition();
            }
        } else {
            ISpawnProvider provider = world.getWorldConfig().getSpawnProvider();
            if (provider != null) {
                pos = provider.getSpawnPoint(world, new UUID(0L, 0L)).getPosition();
            }
        }
        return pos == null ? new int[] {0, 0} : new int[] {ChunkUtil.chunkCoordinate(pos.x), ChunkUtil.chunkCoordinate(pos.z)};
    }

    /** Runs on the world thread. */
    private static List<String> count(World world, int cx, int cz, int radius) {
        Map<String, Integer> ids = new TreeMap<>();
        var blockTypes = BlockType.getAssetMap();
        for (String key : blockTypes.getAssetMap().keySet()) {
            if (key.startsWith(TECHTALE) || key.startsWith("Ore_")) {
                ids.put(key, blockTypes.getIndex(key));
            }
        }
        Map<String, Long> counts = new TreeMap<>();
        ChunkStore chunkStore = world.getChunkStore();
        int chunks = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Ref<ChunkStore> ref = chunkStore.getChunkReference(ChunkUtil.indexChunk(cx + dx, cz + dz));
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                WorldChunk chunk = chunkStore.getStore().getComponent(ref, WorldChunk.getComponentType());
                if (chunk == null) {
                    continue;
                }
                chunks++;
                for (Map.Entry<String, Integer> id : ids.entrySet()) {
                    int n = chunk.getBlockChunk().count(id.getValue());
                    if (n > 0) {
                        counts.merge(id.getKey(), (long) n, Long::sum);
                    }
                }
            }
        }
        int side = radius * 2 + 1;
        List<String> lines = new ArrayList<>();
        lines.add("Ore counts around chunk (" + cx + ", " + cz + "), radius " + radius + ": " + chunks + " of " + side * side + " chunks loaded");
        for (String prefix : TOTALS) {
            long total = 0;
            Map<String, Long> byMetal = new TreeMap<>();
            for (Map.Entry<String, Long> c : counts.entrySet()) {
                if (c.getKey().startsWith(prefix)) {
                    total += c.getValue();
                    String rest = c.getKey().substring(prefix.length());
                    byMetal.merge(rest.contains("_") ? rest.substring(0, rest.indexOf('_')) : rest, c.getValue(), Long::sum);
                }
            }
            lines.add(prefix + "* total: " + total + (prefix.equals(TECHTALE) ? " " + byMetal : ""));
        }
        for (Map.Entry<String, Long> c : counts.entrySet()) {
            lines.add("    " + c.getKey() + " " + c.getValue());
        }
        return lines;
    }
}
