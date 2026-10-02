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
import java.util.List;

/**
 * Reads recipe JSON shipped in the jar under {@code techtale/recipes/}:
 * <pre>{"recipes": [{"id": "...", "input": {"item": "A", "count": 1}, "extra": {...}, "secondary": {"type": "carbon", "amount": 10},
 *   "output": {"item": "B", "count": 2}}]}</pre>
 * {@code item} may be an array of interchangeable ids. {@code secondary.json} holds {"conversions": [{"item", "type", "amount"}]}.
 */
public final class RecipeLoader {
    public static final String ROOT = "techtale/recipes/";

    private RecipeLoader() {
    }

    /** Loads every recipe type and the conversions from the class path. */
    public static RecipeRegistry loadBuiltIn(ClassLoader loader) throws IOException {
        RecipeRegistry registry = new RecipeRegistry();
        for (RecipeType type : RecipeType.values()) {
            try (InputStream in = loader.getResourceAsStream(ROOT + type.id + ".json")) {
                if (in != null) {
                    readRecipes(type, new InputStreamReader(in, StandardCharsets.UTF_8)).forEach(registry::add);
                }
            }
        }
        try (InputStream in = loader.getResourceAsStream(ROOT + "secondary.json")) {
            if (in != null) {
                readConversions(new InputStreamReader(in, StandardCharsets.UTF_8)).forEach(registry::addConversion);
            }
        }
        return registry;
    }

    public static List<MachineRecipe> readRecipes(RecipeType type, Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<MachineRecipe> out = new ArrayList<>();
        int index = 0;
        for (JsonElement e : root.getAsJsonArray("recipes")) {
            JsonObject o = e.getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : type.id + "_" + index;
            try {
                String secondaryType = null;
                int secondaryAmount = 0;
                if (o.has("secondary")) {
                    JsonObject s = o.getAsJsonObject("secondary");
                    secondaryType = s.get("type").getAsString();
                    secondaryAmount = s.get("amount").getAsInt();
                }
                JsonObject outObj = o.getAsJsonObject("output");
                out.add(new MachineRecipe(
                    id,
                    type,
                    ingredient(o.getAsJsonObject("input")),
                    o.has("extra") ? ingredient(o.getAsJsonObject("extra")) : null,
                    secondaryType,
                    secondaryAmount,
                    new ItemOutput(outObj.get("item").getAsString(), count(outObj))
                ));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("Bad " + type.id + " recipe '" + id + "': " + ex.getMessage(), ex);
            }
            index++;
        }
        return out;
    }

    public static List<SecondaryConversion> readConversions(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<SecondaryConversion> out = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("conversions")) {
            JsonObject o = e.getAsJsonObject();
            out.add(new SecondaryConversion(o.get("item").getAsString(), o.get("type").getAsString(), o.get("amount").getAsInt()));
        }
        return out;
    }

    private static ItemIngredient ingredient(JsonObject o) {
        JsonElement item = o.get("item");
        List<String> ids = new ArrayList<>();
        if (item.isJsonArray()) {
            JsonArray arr = item.getAsJsonArray();
            arr.forEach(x -> ids.add(x.getAsString()));
        } else {
            ids.add(item.getAsString());
        }
        return new ItemIngredient(ids, count(o));
    }

    private static int count(JsonObject o) {
        return o.has("count") ? o.get("count").getAsInt() : 1;
    }
}
