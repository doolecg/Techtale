package com.doolecg.techtale.core.recipe;

import javax.annotation.Nullable;

/**
 * A machine recipe. {@code extra} is the combiner's second item; {@code secondaryType}/{@code secondaryAmount}
 * is what the infuser (infuse type) or compressor (osmium) draws from its secondary buffer per operation.
 */
public record MachineRecipe(
    String id,
    RecipeType type,
    ItemIngredient input,
    @Nullable ItemIngredient extra,
    @Nullable String secondaryType,
    int secondaryAmount,
    ItemOutput output
) {
    public boolean usesSecondary() {
        return secondaryType != null && secondaryAmount > 0;
    }
}
