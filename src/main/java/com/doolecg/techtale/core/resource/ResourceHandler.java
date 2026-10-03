package com.doolecg.techtale.core.resource;

import com.doolecg.techtale.core.Direction;

/** Something that can take or give resources through one of its faces. */
public interface ResourceHandler {
    /** Whether a specific kind of resource can pass through this face at all (used for pipe connections). */
    boolean connects(ResourceKind kind, Direction side);

    /** @return the amount accepted through {@code side} */
    long insert(ResourceKind kind, Direction side, String type, long amount, boolean simulate);
}
