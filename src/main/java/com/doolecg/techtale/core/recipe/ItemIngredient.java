package com.doolecg.techtale.core.recipe;

import java.util.List;

/** One or more interchangeable item ids, and how many are used. */
public record ItemIngredient(List<String> itemIds, int count) {
    public ItemIngredient {
        itemIds = List.copyOf(itemIds);
        if (itemIds.isEmpty() || count <= 0) {
            throw new IllegalArgumentException("Ingredient needs at least one item and a positive count");
        }
    }

    public static ItemIngredient of(String itemId, int count) {
        return new ItemIngredient(List.of(itemId), count);
    }

    public boolean matches(String itemId) {
        return itemId != null && itemIds.contains(itemId);
    }

    public boolean matches(String itemId, int available) {
        return matches(itemId) && available >= count;
    }
}
