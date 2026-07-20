/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.config;

/**
 * Validated {@code fuel} settings section of {@code config.yml}.
 *
 * @param material name of the item consumed to power a fell; always a name that
 *                 resolved via the configured material validator at load time
 * @param amount   how many of that item one fell consumes (valid range 1-64)
 */
public record FuelSettings(String material, int amount) {
}
