package com.doolecg.techtale.core.recipe;

public record ItemOutput(String itemId, int count) {
    public ItemOutput {
        if (itemId == null || itemId.isEmpty() || count <= 0) {
            throw new IllegalArgumentException("Output needs an item and a positive count");
        }
    }
}
