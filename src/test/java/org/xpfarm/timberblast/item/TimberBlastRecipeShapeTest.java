/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.item;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TimberBlastRecipeShape}.
 *
 * <p>This is the whole of the recipe that is testable without a server: building the
 * {@code ShapedRecipe} itself constructs an {@code ItemStack}, which resolves its item
 * type through {@code RegistryAccess} and throws with no server bootstrapped. The
 * registration call and the PDC round-trip are verified at runtime (gate 7a).
 */
class TimberBlastRecipeShapeTest {

    @Test
    void patternIsTheThreeRowsTheSpecStates() {
        assertEquals(List.of("GDG", "GSG", " S "), TimberBlastRecipeShape.PATTERN);
    }

    @Test
    void symbolsMapToGunpowderAxeAndStick() {
        assertEquals(Material.GUNPOWDER, TimberBlastRecipeShape.INGREDIENTS.get('G'));
        assertEquals(Material.DIAMOND_AXE, TimberBlastRecipeShape.INGREDIENTS.get('D'));
        assertEquals(Material.STICK, TimberBlastRecipeShape.INGREDIENTS.get('S'));
        assertEquals(3, TimberBlastRecipeShape.INGREDIENTS.size());
    }

    /**
     * Every symbol used in the grid must be mapped, and every mapping must be used.
     * Either half of that failing is what {@code ShapedRecipe} would reject at
     * registration -- on a live server, during enable, where it is far more expensive
     * to discover.
     */
    @Test
    void everyPatternSymbolIsMappedAndEveryMappingIsUsed() {
        StringBuilder used = new StringBuilder();
        for (String row : TimberBlastRecipeShape.PATTERN) {
            assertEquals(3, row.length(), "row must be exactly three columns: " + row);
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') {
                    continue;
                }
                assertNotNull(TimberBlastRecipeShape.INGREDIENTS.get(symbol),
                        "unmapped pattern symbol: " + symbol);
                used.append(symbol);
            }
        }
        for (char symbol : TimberBlastRecipeShape.INGREDIENTS.keySet()) {
            assertTrue(used.indexOf(String.valueOf(symbol)) >= 0,
                    "ingredient " + symbol + " is mapped but never appears in the pattern");
        }
    }
}
