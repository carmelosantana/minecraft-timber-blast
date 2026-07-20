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
 * Validated {@code fell} settings section of {@code config.yml}.
 *
 * @param maxBlocks  hard ceiling on logs felled in one swing (valid range 1-4096)
 * @param maxRadius  horizontal search radius, in blocks, from the struck log (valid range 1-64)
 * @param maxHeight  vertical search height, in blocks, above the struck log (valid range 1-256)
 * @param dropLeaves whether leaves broken along with the tree drop their items
 */
public record FellSettings(int maxBlocks, int maxRadius, int maxHeight, boolean dropLeaves) {
}
