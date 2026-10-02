package com.doolecg.techtale.hytale;

import com.doolecg.techtale.core.recipe.ItemIngredient;
import com.doolecg.techtale.core.recipe.ItemOutput;
import com.doolecg.techtale.core.recipe.MachineRecipe;
import com.doolecg.techtale.core.recipe.RecipeRegistry;
import com.doolecg.techtale.core.recipe.RecipeType;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

/** The Energized Smelter uses every single-input recipe of Hytale's Furnace, like Mekanism uses vanilla smelting. */
public final class SmeltingRecipes {
    private SmeltingRecipes() {
    }

    public static int importFurnaceRecipes(RecipeRegistry registry) {
        registry.clear(RecipeType.SMELTING);
        int added = 0;
        for (CraftingRecipe recipe : CraftingPlugin.getBenchRecipes(BenchType.Processing, "Furnace")) {
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs == null || inputs.length != 1 || inputs[0].getItemId() == null) {
                continue;
            }
            MaterialQuantity out = recipe.getPrimaryOutput();
            if (out == null && recipe.getOutputs() != null && recipe.getOutputs().length > 0) {
                out = recipe.getOutputs()[0];
            }
            if (out == null || out.getItemId() == null) {
                continue;
            }
            registry.add(new MachineRecipe(
                recipe.getId(),
                RecipeType.SMELTING,
                ItemIngredient.of(inputs[0].getItemId(), Math.max(1, inputs[0].getQuantity())),
                null,
                null,
                0,
                new ItemOutput(out.getItemId(), Math.max(1, out.getQuantity()))));
            added++;
        }
        return added;
    }
}
