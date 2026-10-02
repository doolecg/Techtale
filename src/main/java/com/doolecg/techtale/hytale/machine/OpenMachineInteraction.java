package com.doolecg.techtale.hytale.machine;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Block "Use" interaction that opens a machine's page. Registered as interaction type "Techtale_OpenMachine". */
public class OpenMachineInteraction extends SimpleBlockInteraction {
    public static final String TYPE = "Techtale_OpenMachine";

    public static final BuilderCodec<OpenMachineInteraction> CODEC = BuilderCodec.builder(
            OpenMachineInteraction.class, OpenMachineInteraction::new, SimpleBlockInteraction.CODEC)
        .documentation("Opens a Techtale machine's page.")
        .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i pos,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return;
        }
        MachineBlock machine = world.getChunkStore().getStore().getComponent(blockRef, MachineBlock.getComponentType());
        if (machine == null || !machine.isInitialized()) {
            return;
        }
        MachinePage page = new MachinePage(playerRef, blockRef, machine);
        machine.getOpenPages().add(page);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock
    ) {
    }
}
