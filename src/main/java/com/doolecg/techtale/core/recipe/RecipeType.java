package com.doolecg.techtale.core.recipe;

/** The machine recipe families. The JSON file for a type is {@code techtale/recipes/<id>.json}. */
public enum RecipeType {
    ENRICHING("enriching"),
    CRUSHING("crushing"),
    SMELTING("smelting"),
    COMPRESSING("compressing"),
    COMBINING("combining"),
    INFUSING("infusing");

    public final String id;

    RecipeType(String id) {
        this.id = id;
    }

    public static RecipeType byId(String id) {
        for (RecipeType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown recipe type " + id);
    }
}
