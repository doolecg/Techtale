package com.doolecg.techtale.hytale.command;

import com.doolecg.techtale.hytale.debug.ChemicalSelfTest;
import com.doolecg.techtale.hytale.debug.SelfTest;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * /techtale selftest: builds a generator, cable and machine rig and checks it processes ore. A player gets the rig
 * at their feet two blocks to the east (it clears a small 8x2x3 space there); the console builds it high above 0,0.
 */
public class SelfTestCommand extends AbstractAsyncCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public SelfTestCommand() {
        super("selftest", "Builds a small Techtale test rig next to you (or above 0,0 from the console) and checks power, cables and processing.");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        if (playerRef != null && playerRef.isValid()) {
            World world = playerRef.getStore().getExternalData().getWorld();
            // Read the position on the world thread, then build there.
            return CompletableFuture.supplyAsync(() -> {
                TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
                return transform == null ? null : transform.getPosition();
            }, world).thenCompose(pos -> {
                if (pos == null) {
                    context.sendMessage(Message.raw("Could not read your position."));
                    return CompletableFuture.completedFuture(null);
                }
                return start(world, (int) Math.floor(pos.x) + 2, (int) Math.floor(pos.y), (int) Math.floor(pos.z), context);
            });
        }
        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            context.sendMessage(Message.raw("No world is loaded."));
            return CompletableFuture.completedFuture(null);
        }
        return start(world, SelfTest.CONSOLE_X, SelfTest.CONSOLE_Y, SelfTest.CONSOLE_Z, context);
    }

    private static CompletableFuture<Void> start(World world, int x, int y, int z, CommandContext context) {
        return SelfTest.run(world, x, y, z, line -> {
            LOGGER.atInfo().log("%s", line);
            context.sendMessage(Message.raw(line));
        }).thenCompose(passed -> {
            if (!passed) {
                return CompletableFuture.completedFuture(false);
            }
            // The chemical rig sits 6 blocks south of the base rig and 2 east, clear of its footprint.
            return ChemicalSelfTest.run(world, x + 2, y, z + 6, line -> {
                LOGGER.atInfo().log("%s", line);
                context.sendMessage(Message.raw(line));
            });
        }).thenAccept(passed -> {
        });
    }
}
