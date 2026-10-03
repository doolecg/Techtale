package com.doolecg.techtale.hytale.resource;

import com.doolecg.techtale.TechtalePlugin;
import com.doolecg.techtale.core.resource.ResourceKind;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nullable;

/**
 * Pipe block entity. JSON: {@code "Techtale_Pipe": {"Kind": "Fluid", "Tier": "Basic"}}. While loaded, the content
 * lives in the network buffer; {@code Type} and {@code Amount} hold this pipe's share only while its chunk is unloaded.
 */
public class PipeBlock implements Component<ChunkStore> {
    public static final String ID = "Techtale_Pipe";

    public enum Tier {
        Basic(1_000),
        Advanced(4_000),
        Elite(16_000),
        Ultimate(64_000);

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

    public static final BuilderCodec<PipeBlock> CODEC = BuilderCodec.builder(PipeBlock.class, PipeBlock::new)
        .append(new KeyedCodec<>("Kind", Codec.STRING), (p, v) -> p.kind = parseKind(v), p -> p.kind.name()).add()
        .append(new KeyedCodec<>("Tier", Codec.STRING), (p, v) -> p.tier = Tier.parse(v), p -> p.tier.name()).add()
        .append(new KeyedCodec<>("Type", Codec.STRING), (p, v) -> p.type = v, p -> p.type).add()
        .append(new KeyedCodec<>("Amount", Codec.LONG), (p, v) -> p.amount = v, p -> p.amount).add()
        .build();

    private ResourceKind kind = ResourceKind.FLUID;
    private Tier tier = Tier.Basic;
    @Nullable
    private String type;
    private long amount;

    public static ComponentType<ChunkStore, PipeBlock> getComponentType() {
        return TechtalePlugin.get().getPipeComponentType();
    }

    private static ResourceKind parseKind(String s) {
        for (ResourceKind k : ResourceKind.values()) {
            if (k.name().equalsIgnoreCase(s)) {
                return k;
            }
        }
        return ResourceKind.FLUID;
    }

    public ResourceKind getKind() {
        return kind;
    }

    public Tier getTier() {
        return tier;
    }

    @Nullable
    public String getType() {
        return type;
    }

    /** Returns the saved share and clears it. */
    public long takeAmount() {
        long a = amount;
        amount = 0;
        return a;
    }

    public void setContent(@Nullable String type, long amount) {
        this.type = amount > 0 ? type : null;
        this.amount = amount;
    }

    @Override
    public Component<ChunkStore> clone() {
        PipeBlock c = new PipeBlock();
        c.kind = kind;
        c.tier = tier;
        c.type = type;
        c.amount = amount;
        return c;
    }
}
