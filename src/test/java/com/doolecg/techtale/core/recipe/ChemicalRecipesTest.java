package com.doolecg.techtale.core.recipe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChemicalRecipesTest {
    private static ChemicalRecipes recipes;

    @BeforeAll
    static void load() throws IOException {
        recipes = ChemicalRecipes.loadBuiltIn(ChemicalRecipesTest.class.getClassLoader());
    }

    @Test
    void loadsBuiltInWithoutError() {
        assertDoesNotThrow(() -> ChemicalRecipes.loadBuiltIn(getClass().getClassLoader()));
    }

    @Test
    void electrolysisRecipeLoads() {
        assertTrue(recipes.size() > 0, "built-in recipes should load");
    }

    @Test
    void waterElectrolysisProducesHydrogenAndOxygen() {
        ChemicalRecipe r = recipes.find("water");
        assertNotNull(r, "water recipe should exist");
        assertEquals("electrolysis_water", r.id());
        assertEquals("water", r.inputFluid());
        assertEquals(2, r.inputAmount());
        assertEquals(2, r.outputs().size());

        ChemicalOutput hydrogen = r.outputs().get(0);
        assertEquals("hydrogen", hydrogen.chemical());
        assertEquals(2, hydrogen.amount());

        ChemicalOutput oxygen = r.outputs().get(1);
        assertEquals("oxygen", oxygen.chemical());
        assertEquals(1, oxygen.amount());
    }

    @Test
    void findReturnsNullForUnknownFluid() {
        assertNull(recipes.find("unknown_fluid"));
    }

    @Test
    void findReturnsNullForNullFluid() {
        assertNull(recipes.find(null));
    }

    @Test
    void readRecipesParsesBasicRecipe() {
        String json = """
            {
              "recipes": [
                {
                  "id": "test_recipe",
                  "input": {"fluid": "test_fluid", "amount": 3},
                  "outputs": [{"chemical": "chem_a", "amount": 2}]
                }
              ]
            }
            """;
        List<ChemicalRecipe> parsed = ChemicalRecipes.readRecipes(new StringReader(json));
        assertEquals(1, parsed.size());
        ChemicalRecipe r = parsed.get(0);
        assertEquals("test_recipe", r.id());
        assertEquals("test_fluid", r.inputFluid());
        assertEquals(3, r.inputAmount());
        assertEquals(1, r.outputs().size());
        assertEquals("chem_a", r.outputs().get(0).chemical());
        assertEquals(2, r.outputs().get(0).amount());
    }

    @Test
    void readRecipesUsesDefaultAmounts() {
        String json = """
            {
              "recipes": [
                {
                  "input": {"fluid": "water"},
                  "outputs": [{"chemical": "hydrogen"}]
                }
              ]
            }
            """;
        List<ChemicalRecipe> parsed = ChemicalRecipes.readRecipes(new StringReader(json));
        ChemicalRecipe r = parsed.get(0);
        assertEquals("chemical_0", r.id());
        assertEquals(1, r.inputAmount());
        assertEquals(1, r.outputs().get(0).amount());
    }

    @Test
    void readRecipesThrowsOnInvalidRecipe() {
        String json = """
            {
              "recipes": [
                {
                  "id": "bad_recipe",
                  "input": {"fluid": "", "amount": 1},
                  "outputs": [{"chemical": "x", "amount": 1}]
                }
              ]
            }
            """;
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> ChemicalRecipes.readRecipes(new StringReader(json)));
        assertTrue(e.getMessage().contains("bad_recipe"));
    }

    @Test
    void multipleRecipesWithSameFluidAreAllReturned() {
        String json = """
            {
              "recipes": [
                {"id": "r1", "input": {"fluid": "water", "amount": 1}, "outputs": [{"chemical": "a", "amount": 1}]},
                {"id": "r2", "input": {"fluid": "water", "amount": 2}, "outputs": [{"chemical": "b", "amount": 1}]}
              ]
            }
            """;
        ChemicalRecipes r = new ChemicalRecipes();
        ChemicalRecipes.readRecipes(new StringReader(json)).forEach(r::add);
        List<ChemicalRecipe> all = r.get("water");
        assertEquals(2, all.size());
        assertEquals("r1", all.get(0).id());
        assertEquals("r2", all.get(1).id());
    }
}
