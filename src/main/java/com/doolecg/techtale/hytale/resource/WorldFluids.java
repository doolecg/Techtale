package com.doolecg.techtale.hytale.resource;

import com.doolecg.techtale.core.machine.MachineLogic;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.doolecg.techtale.core.resource.ResourceStack;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Map;
import javax.annotation.Nullable;

/** A pump's view of the fluid source block directly below it. Must be used on the world thread. */
public final class WorldFluids implements MachineLogic.PumpSource {
    /** Fluid source asset id -> resource type. */
    private static final Map<String, String> SOURCES = Map.of("Water_Source", "water", "Lava_Source", "lava");
    /** One source block holds this much. */
    public static final long SOURCE_MB = 1000;

    private final Store<ChunkStore> store;
    private final ChunkStore chunkStore;
    private final int x;
    private final int y;
    private final int z;

    public WorldFluids(Store<ChunkStore> store, int pumpX, int pumpY, int pumpZ) {
        this.store = store;
        this.chunkStore = store.getExternalData();
        this.x = pumpX;
        this.y = pumpY - 1;
        this.z = pumpZ;
    }

    @Nullable
    @Override
    public ResourceStack drain(long maxMb, boolean simulate) {
        if (maxMb < SOURCE_MB || y < 0) {
            return null;
        }
        Ref<ChunkStore> ref = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        FluidSection section = store.getComponent(ref, FluidSection.getComponentType());
        if (section == null) {
            return null;
        }
        int id = section.getFluidId(x, y, z);
        if (id == Fluid.EMPTY_ID) {
            return null;
        }
        Fluid fluid = Fluid.getAssetMap().getAsset(id);
        String type = fluid == null ? null : SOURCES.get(fluid.getId());
        if (type == null) {
            return null;
        }
        if (!simulate) {
            section.setFluid(x, y, z, Fluid.EMPTY_ID, (byte) 0);
            FluidTicker.setTickingSurrounding(new SectionAccessor(), blocksAt(ref), x, y, z);
        }
        return new ResourceStack(ResourceKind.FLUID, type, SOURCE_MB);
    }

    @Nullable
    private BlockSection blocksAt(Ref<ChunkStore> sectionRef) {
        return store.getComponent(sectionRef, BlockSection.getComponentType());
    }

    /** Looks sections up by section coordinates; blocks are never changed by the ticker wake-up. */
    private final class SectionAccessor implements FluidTicker.Accessor {
        @Nullable
        @Override
        public FluidSection getFluidSection(int sx, int sy, int sz) {
            Ref<ChunkStore> ref = chunkStore.getChunkSectionReference(sx, sy, sz);
            return ref == null || !ref.isValid() ? null : store.getComponent(ref, FluidSection.getComponentType());
        }

        @Nullable
        @Override
        public BlockSection getBlockSection(int sx, int sy, int sz) {
            Ref<ChunkStore> ref = chunkStore.getChunkSectionReference(sx, sy, sz);
            return ref == null || !ref.isValid() ? null : store.getComponent(ref, BlockSection.getComponentType());
        }

        @Override
        public void setBlock(int bx, int by, int bz, int blockId) {
        }
    }
}
