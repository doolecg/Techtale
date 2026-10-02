package com.doolecg.techtale.core.recipe;

/** An item that fills a machine's secondary buffer, e.g. charcoal = 10 carbon. */
public record SecondaryConversion(String itemId, String type, int amount) {
}
