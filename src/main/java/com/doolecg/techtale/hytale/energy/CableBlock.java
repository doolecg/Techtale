package com.doolecg.techtale.hytale.energy;

import com.doolecg.techtale.TechtalePlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Universal cable block entity. JSON: {@code "Techtale_Cable": {"Tier": "Basic"}}. While loaded, the energy lives in
 * the network buffer; {@code Energy} holds this cable's share only while its chunk is unloaded.
 */
public class CableBlock implements Component<ChunkStore> {
    public static final String ID = "Techtale_Cable";

    public enum Tier {
        Basic(8_000),
        Advanced(128_000),
        Elite(1_024_000),
        Ultimate(8_192_000);

        public final long capacity;

        Tier(long capacity) {
            this.capacity = capacity;
        }

        public static Tier parse(String s) {
            for (Tier t : values()) {
                if (t.name().equalsIgnoreCase(s)) {
                    return t;
                }
            }
            return Basic;
        }
    }

    public static final BuilderCodec<CableBlock> CODEC = BuilderCodec.builder(CableBlock.class, CableBlock::new)
        .append(new KeyedCodec<>("Tier", Codec.STRING), (c, v) -> c.tier = Tier.parse(v), c -> c.tier.name()).add()
        .append(new KeyedCodec<>("Energy", Codec.LONG), (c, v) -> c.energy = v, c -> c.energy).add()
        .build();

    private Tier tier = Tier.Basic;
    private long energy;

    public static ComponentType<ChunkStore, CableBlock> getComponentType() {
        return TechtalePlugin.get().getCableComponentType();
    }

    public Tier getTier() {
        return tier;
    }

    public long takeEnergy() {
        long e = energy;
        energy = 0;
        return e;
    }

    public void setEnergy(long energy) {
        this.energy = energy;
    }

    @Override
    public Component<ChunkStore> clone() {
        CableBlock c = new CableBlock();
        c.tier = tier;
        c.energy = energy;
        return c;
    }
}
