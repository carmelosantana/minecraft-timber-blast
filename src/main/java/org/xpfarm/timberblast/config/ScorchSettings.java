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
 * Validated {@code scorch} settings section of {@code config.yml}.
 *
 * @param enabled whether the ground around the felled tree is visually scorched
 * @param spread  whether the scorch may set actual fire that spreads
 */
public record ScorchSettings(boolean enabled, boolean spread) {
}
