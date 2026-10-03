package com.doolecg.techtale.core.recipe;

/** A chemical type and amount produced by a recipe. */
public record ChemicalOutput(String chemical, int amount) {
    public ChemicalOutput {
        if (chemical == null || chemical.isEmpty()) {
            throw new IllegalArgumentException("Chemical type cannot be null or empty");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
