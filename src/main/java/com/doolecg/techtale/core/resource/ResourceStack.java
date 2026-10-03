package com.doolecg.techtale.core.resource;

/** A specific resource and its quantity. */
public record ResourceStack(ResourceKind kind, String type, long amount) {
}
