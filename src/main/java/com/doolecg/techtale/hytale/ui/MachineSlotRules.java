package com.doolecg.techtale.hytale.ui;

import com.doolecg.techtale.core.machine.MachineDefinition;
import com.doolecg.techtale.core.machine.MachineDefinition.SlotRole;
import com.doolecg.techtale.core.machine.Machines;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import com.doolecg.techtale.core.recipe.SecondaryConversion;
import java.util.function.Predicate;

/** Which items a player may put into a machine slot by hand. Output slots are take-only. */
public final class MachineSlotRules {
    private MachineSlotRules() {
    }

    public static boolean accepts(MachineDefinition def, SlotRole role, String itemId, RecipeRegistry recipes, Predicate<String> isFuel) {
        if (itemId == null) {
            return false;
        }
        return switch (role) {
            case INPUT -> def.recipeType() != null && recipes.isInput(def.recipeType(), itemId);
            case EXTRA -> {
                if (def.usesSecondary()) {
                    SecondaryConversion conv = recipes.conversionFor(itemId);
                    yield conv != null && (def.secondaryType() == null || def.secondaryType().equals(conv.type()));
                }
                yield def.recipeType() != null && recipes.isExtra(def.recipeType(), itemId);
            }
            case OUTPUT -> false;
            case FUEL -> isFuel.test(itemId);
            case UPGRADE -> Machines.SPEED_UPGRADE.equals(itemId) || Machines.ENERGY_UPGRADE.equals(itemId);
        };
    }
}
