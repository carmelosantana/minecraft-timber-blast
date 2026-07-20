/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.tree;

/**
 * An immutable block coordinate in a single world.
 *
 * <p>Deliberately world-less and Bukkit-free: the scan never crosses worlds, so the
 * caller supplies the world when it turns these back into blocks.
 *
 * @param x block x
 * @param y block y
 * @param z block z
 */
public record BlockPos(int x, int y, int z) {

    /** This position offset by {@code (dx, dy, dz)}. */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
