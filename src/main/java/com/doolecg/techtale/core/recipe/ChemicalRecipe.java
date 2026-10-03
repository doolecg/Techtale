package com.doolecg.techtale.core.recipe;

import java.util.List;

/**
 * A chemical recipe for processing fluids. Input is a fluid type and amount; outputs are chemicals
 * assigned to output tanks in order.
 */
public record ChemicalRecipe(
    String id,
    String inputFluid,
    int inputAmount,
    List<ChemicalOutput> outputs
) {
    public ChemicalRecipe {
        outputs = List.copyOf(outputs);
        if (inputFluid == null || inputFluid.isEmpty()) {
            throw new IllegalArgumentException("Input fluid cannot be null or empty");
        }
        if (inputAmount <= 0) {
            throw new IllegalArgumentException("Input amount must be positive");
        }
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one output");
        }
    }
}
