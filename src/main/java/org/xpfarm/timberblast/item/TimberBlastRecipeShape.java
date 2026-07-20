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

import java.util.List;
import java.util.Map;

/**
 * The shape of the Timber Blast crafting recipe, separated from its registration.
 *
 * <pre>
 *   G D G     G = GUNPOWDER
 *   G S G     D = DIAMOND_AXE
 *   _ S _     S = STICK
 * </pre>
 *
 * <p>Constructing a {@code ShapedRecipe} needs a live server (its result is an
 * {@code ItemStack}, whose item type is resolved through {@code RegistryAccess}), so
 * the pattern and the ingredient mapping live here as plain data instead. That makes
 * the part of the recipe that can actually regress -- a mistyped row, a dropped
 * ingredient -- assertable in a unit test, leaving only the registration call itself
 * for runtime verification.
 */
public final class TimberBlastRecipeShape {

    /** The three rows of the crafting grid, top to bottom; {@code ' '} means empty. */
    public static final List<String> PATTERN = List.of("GDG", "GSG", " S ");

    /** Which material each non-space pattern symbol stands for. */
    public static final Map<Character, Material> INGREDIENTS = Map.of(
            'G', Material.GUNPOWDER,
            'D', Material.DIAMOND_AXE,
            'S', Material.STICK);

    private TimberBlastRecipeShape() {
    }
}
