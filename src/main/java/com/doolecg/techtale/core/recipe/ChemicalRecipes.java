package com.doolecg.techtale.core.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Chemical recipes loaded from JSON files under {@code techtale/recipes/}.
 * Recipes are looked up by input fluid type.
 */
public final class ChemicalRecipes {
    private static final String ROOT = "techtale/recipes/";

    private final Map<String, List<ChemicalRecipe>> byFluid = new HashMap<>();

    public ChemicalRecipes() {
    }

    /** Loads all chemical recipes from the class path. */
    public static ChemicalRecipes loadBuiltIn(ClassLoader loader) throws IOException {
        ChemicalRecipes recipes = new ChemicalRecipes();
        try (InputStream in = loader.getResourceAsStream(ROOT + "electrolysis.json")) {
            if (in != null) {
                readRecipes(new InputStreamReader(in, StandardCharsets.UTF_8)).forEach(recipes::add);
            }
        }
        return recipes;
    }

    public void add(ChemicalRecipe recipe) {
        byFluid.computeIfAbsent(recipe.inputFluid(), k -> new ArrayList<>()).add(recipe);
    }

    /** First recipe with the given input fluid type. */
    @Nullable
    public ChemicalRecipe find(String inputFluid) {
        if (inputFluid == null) {
            return null;
        }
        List<ChemicalRecipe> recipes = byFluid.get(inputFluid);
        return recipes == null || recipes.isEmpty() ? null : recipes.get(0);
    }

    /** All recipes with the given input fluid type. */
    public List<ChemicalRecipe> get(String inputFluid) {
        List<ChemicalRecipe> recipes = byFluid.get(inputFluid);
        return recipes == null ? Collections.emptyList() : Collections.unmodifiableList(recipes);
    }

    public int size() {
        return byFluid.values().stream().mapToInt(List::size).sum();
    }

    public static List<ChemicalRecipe> readRecipes(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<ChemicalRecipe> out = new ArrayList<>();
        int index = 0;
        for (JsonElement e : root.getAsJsonArray("recipes")) {
            JsonObject o = e.getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : "chemical_" + index;
            try {
                JsonObject inputObj = o.getAsJsonObject("input");
                String inputFluid = inputObj.get("fluid").getAsString();
                int inputAmount = inputObj.has("amount") ? inputObj.get("amount").getAsInt() : 1;

                List<ChemicalOutput> outputs = new ArrayList<>();
                for (JsonElement output : o.getAsJsonArray("outputs")) {
                    JsonObject outputObj = output.getAsJsonObject();
                    String chemical = outputObj.get("chemical").getAsString();
                    int amount = outputObj.has("amount") ? outputObj.get("amount").getAsInt() : 1;
                    outputs.add(new ChemicalOutput(chemical, amount));
                }

                out.add(new ChemicalRecipe(id, inputFluid, inputAmount, outputs));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Bad chemical recipe '" + id + "': " + ex.getMessage(), ex);
            }
            index++;
        }
        return out;
    }
}
