package com.doolecg.techtale.hytale;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Block lookups that never load or generate chunks. All calls must run on the world thread. */
public final class BlockAccess {
    private BlockAccess() {
    }

    /** World position of a block entity, packed, or Long.MIN_VALUE if its section is gone. */
    public static long posOf(BlockModule.BlockStateInfo info, ComponentAccessor<ChunkStore> accessor) {
        Ref<ChunkStore> sectionRef = info.getSectionRef();
        if (sectionRef == null || !sectionRef.isValid()) {
            return Long.MIN_VALUE;
        }
        ChunkSection section = accessor.getComponent(sectionRef, ChunkSection.getComponentType());
        if (section == null) {
            return Long.MIN_VALUE;
        }
        int index = info.getIndex();
        return BlockPos.pack(
            ChunkUtil.worldCoordFromLocalCoord(section.getX(), ChunkUtil.xFromIndex(index)),
            ChunkUtil.worldCoordFromLocalCoord(section.getY(), ChunkUtil.yFromIndex(index)),
            ChunkUtil.worldCoordFromLocalCoord(section.getZ(), ChunkUtil.zFromIndex(index)));
    }

    /** Rotation index of a block entity's block, or 0. */
    public static int rotationOf(BlockModule.BlockStateInfo info, ComponentAccessor<ChunkStore> accessor) {
        Ref<ChunkStore> sectionRef = info.getSectionRef();
        if (sectionRef == null || !sectionRef.isValid()) {
            return 0;
        }
        BlockSection blocks = accessor.getComponent(sectionRef, BlockSection.getComponentType());
        if (blocks == null) {
            return 0;
        }
        int index = info.getIndex();
        return blocks.getRotationIndex(ChunkUtil.xFromIndex(index), ChunkUtil.yFromIndex(index), ChunkUtil.zFromIndex(index));
    }

    /** The front of a block placed with NESW rotation: the face that carries the "North" texture. */
    public static Direction frontOf(int rotationIndex) {
        Rotation yaw = RotationTuple.get(rotationIndex).yaw();
        return switch (yaw) {
            case None -> Direction.NORTH;
            case Ninety -> Direction.WEST;
            case OneEighty -> Direction.SOUTH;
            case TwoSeventy -> Direction.EAST;
        };
    }

    @Nullable
    public static BlockType blockTypeAt(World world, long pos) {
        int x = BlockPos.x(pos);
        int y = BlockPos.y(pos);
        int z = BlockPos.z(pos);
        if (y < 0 || y >= 320) {
            return null;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        BlockSection blocks = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        return blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
    }

    /** Swaps a block to one of its State.Definitions variants ("default" = base block), keeping its block entity. */
    public static void setState(World world, long pos, String state) {
        BlockType type = blockTypeAt(world, pos);
        if (type == null) {
            return;
        }
        String current = type.getCurrentInteractionState();
        if (state.equals(current) || (current == null && "default".equals(state))) {
            return;
        }
        world.setBlockInteractionState(new Vector3i(BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos)), type, state);
    }

    public static long neighbour(long pos, Direction d) {
        return BlockPos.offset(pos, d);
    }
}
