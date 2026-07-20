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

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-built {@link BlockQuery} over a map of positions, standing in for a live world.
 * Anything not put in the map is {@link BlockKind#OTHER}, matching the interface's
 * "unknown means nothing tree-like" contract.
 */
final class MapBlockQuery implements BlockQuery {

    private final Map<BlockPos, BlockKind> blocks = new HashMap<>();

    /** Places a block of {@code kind} at {@code (x, y, z)}. */
    MapBlockQuery put(int x, int y, int z, BlockKind kind) {
        blocks.put(new BlockPos(x, y, z), kind);
        return this;
    }

    /** Places a vertical run of {@code height} logs starting at {@code (x, y, z)}. */
    MapBlockQuery trunk(int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            put(x, y + i, z, BlockKind.LOG);
        }
        return this;
    }

    @Override
    public BlockKind kindAt(BlockPos pos) {
        return blocks.getOrDefault(pos, BlockKind.OTHER);
    }
}
