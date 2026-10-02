package com.doolecg.techtale.core.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipeLoaderTest {
    private static RecipeRegistry registry;

    @BeforeAll
    static void load() throws IOException {
        registry = RecipeLoader.loadBuiltIn(RecipeLoaderTest.class.getClassLoader());
    }

    @Test
    void loadsEveryShippedFileWithoutError() {
        assertDoesNotThrow(() -> RecipeLoader.loadBuiltIn(getClass().getClassLoader()));
        for (RecipeType type : RecipeType.values()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(RecipeLoader.ROOT + type.id + ".json")) {
                if (in != null) {
                    assertFalse(registry.get(type).isEmpty(), type.id + ".json loaded no recipes");
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }
        assertTrue(registry.size() > 0);
    }

    @Test
    void recipeIdsAreUniquePerType() {
        for (RecipeType type : RecipeType.values()) {
            Set<String> ids = new HashSet<>();
            for (MachineRecipe r : registry.get(type)) {
                assertTrue(ids.add(r.id()), "duplicate " + type.id + " recipe id " + r.id());
            }
        }
    }

    @Test
    void rawOsmiumEnrichesIntoTwoOsmiumDust() {
        MachineRecipe r = registry.find(RecipeType.ENRICHING, "Techtale_Raw_Osmium", null, null);
        assertNotNull(r);
        assertEquals("Techtale_Dust_Osmium", r.output().itemId());
        assertEquals(2, r.output().count());
        assertEquals(1, r.input().count());
        assertFalse(r.usesSecondary(), "enriching uses no secondary buffer");
    }

    @Test
    void ironPlusCarbonInfusesToEnrichedIron() {
        MachineRecipe r = registry.find(RecipeType.INFUSING, "Ingredient_Bar_Iron", null, "carbon");
        assertNotNull(r);
        assertEquals("Techtale_Enriched_Iron", r.output().itemId());
        assertEquals("carbon", r.secondaryType());
        assertEquals(10, r.secondaryAmount());
    }

    @Test
    void ironPlusCopperInfusesToInfusedAlloy() {
        MachineRecipe r = registry.find(RecipeType.INFUSING, "Ingredient_Bar_Iron", null, "copper");
        assertNotNull(r);
        assertEquals("Techtale_Alloy_Infused", r.output().itemId());
        assertEquals("copper", r.secondaryType());
    }

    @Test
    void ironWithAnUnrelatedInfuseTypeHasNoRecipe() {
        assertNull(registry.find(RecipeType.INFUSING, "Ingredient_Bar_Iron", null, "tin"));
    }

    @Test
    void charcoalFillsTheCarbonBuffer() {
        SecondaryConversion c = registry.conversionFor("Ingredient_Charcoal");
        assertNotNull(c);
        assertEquals("carbon", c.type());
        assertEquals(10, c.amount());
        assertNull(registry.conversionFor("Ore_Iron"));
        assertNull(registry.conversionFor(null));
    }

    @Test
    void everyInfuseTypeUsedByARecipeHasAConversion() {
        Set<String> convertible = new HashSet<>();
        for (String item : List.of("Ingredient_Charcoal", "Techtale_Dust_Copper", "Techtale_Dust_Diamond",
            "Techtale_Dust_Refined_Obsidian", "Techtale_Dust_Tin", "Techtale_Ingot_Osmium")) {
            SecondaryConversion c = registry.conversionFor(item);
            if (c != null) {
                convertible.add(c.type());
            }
        }
        for (RecipeType type : List.of(RecipeType.INFUSING, RecipeType.COMPRESSING)) {
            for (MachineRecipe r : registry.get(type)) {
                if (r.usesSecondary()) {
                    assertTrue(convertible.contains(r.secondaryType()), r.id() + " needs " + r.secondaryType());
                }
            }
        }
    }

    @Test
    void readRecipesParsesIngredientListsExtrasAndDefaultCounts() {
        String json = """
            {"recipes": [
              {"input": {"item": ["A", "B"]}, "extra": {"item": "C", "count": 2}, "output": {"item": "D"}}
            ]}
            """;
        MachineRecipe r = RecipeLoader.readRecipes(RecipeType.COMBINING, new StringReader(json)).get(0);
        assertEquals("combining_0", r.id());
        assertTrue(r.input().matches("B"));
        assertEquals(1, r.input().count());
        assertEquals(2, r.extra().count());
        assertEquals(1, r.output().count());
    }

    @Test
    void readRecipesNamesTheBrokenRecipe() {
        String json = "{\"recipes\": [{\"id\": \"bad\", \"input\": {\"item\": \"A\", \"count\": 0}, \"output\": {\"item\": \"B\"}}]}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> RecipeLoader.readRecipes(RecipeType.ENRICHING, new StringReader(json)));
        assertTrue(e.getMessage().contains("bad"));
    }
}
