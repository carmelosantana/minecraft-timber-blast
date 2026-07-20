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
 * Validated {@code coal} settings section of {@code config.yml}.
 *
 * @param enabled  whether the struck log is charred into a fuel item instead of dropping as a log
 * @param material name of the item the struck log chars into; always a name that
 *                 resolved via the configured material validator at load time
 */
public record CoalSettings(boolean enabled, String material) {
}
