package com.doolecg.techtale.core.resource;

import java.util.Set;

/** Specification for a resource tank: what kind, capacity, and which sides allow flow. */
public record TankSpec(
    ResourceKind kind,
    long capacity,
    Set<RelativeSide> inputSides,
    Set<RelativeSide> outputSides,
    String fixedType
) {
}
