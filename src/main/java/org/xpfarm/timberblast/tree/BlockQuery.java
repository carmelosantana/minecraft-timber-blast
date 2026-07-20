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
 * Read-only, Bukkit-free view of the blocks around the struck log.
 *
 * <p>This is the seam that keeps {@link TreeScanner} testable without a running server:
 * production code hands it an adapter over the live world, while tests hand it a map.
 *
 * <p><b>Contract:</b> {@link #kindAt} is total -- unloaded, unknown, or out-of-world
 * positions answer {@link BlockKind#OTHER} rather than throwing or returning null.
 */
@FunctionalInterface
public interface BlockQuery {

    /** The kind of block at {@code pos}; {@link BlockKind#OTHER} if there is nothing tree-like there. */
    BlockKind kindAt(BlockPos pos);
}
