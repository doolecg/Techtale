package com.doolecg.techtale.core.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** All machine recipes and secondary-buffer conversions, looked up by input item. */
public final class RecipeRegistry {
    private final Map<RecipeType, List<MachineRecipe>> byType = new EnumMap<>(RecipeType.class);
    private final Map<String, SecondaryConversion> conversions = new HashMap<>();

    public RecipeRegistry() {
        for (RecipeType t : RecipeType.values()) {
            byType.put(t, new ArrayList<>());
        }
    }

    public void add(MachineRecipe recipe) {
        byType.get(recipe.type()).add(recipe);
    }

    public void addConversion(SecondaryConversion conversion) {
        conversions.put(conversion.itemId(), conversion);
    }

    public void clear(RecipeType type) {
        byType.get(type).clear();
    }

    public List<MachineRecipe> get(RecipeType type) {
        return Collections.unmodifiableList(byType.get(type));
    }

    @Nullable
    public SecondaryConversion conversionFor(@Nullable String itemId) {
        return itemId == null ? null : conversions.get(itemId);
    }

    /** First recipe of {@code type} whose input (and extra / secondary type, when given) match. */
    @Nullable
    public MachineRecipe find(RecipeType type, @Nullable String inputId, @Nullable String extraId, @Nullable String secondaryType) {
        if (inputId == null) {
            return null;
        }
        for (MachineRecipe r : byType.get(type)) {
            if (!r.input().matches(inputId)) {
                continue;
            }
            if (r.extra() != null && !r.extra().matches(extraId)) {
                continue;
            }
            if (secondaryType != null && r.usesSecondary() && !r.secondaryType().equals(secondaryType)) {
                continue;
            }
            return r;
        }
        return null;
    }

    /** Whether {@code itemId} is the input of any recipe of {@code type} (for slot filters). */
    public boolean isInput(RecipeType type, String itemId) {
        for (MachineRecipe r : byType.get(type)) {
            if (r.input().matches(itemId)) {
                return true;
            }
        }
        return false;
    }

    public boolean isExtra(RecipeType type, String itemId) {
        for (MachineRecipe r : byType.get(type)) {
            if (r.extra() != null && r.extra().matches(itemId)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return byType.values().stream().mapToInt(List::size).sum();
    }
}
