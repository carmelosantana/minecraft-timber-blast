/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.fell;

import org.bukkit.Material;
import org.bukkit.World;
import org.xpfarm.timberblast.tree.BlockKind;
import org.xpfarm.timberblast.tree.BlockPos;
import org.xpfarm.timberblast.tree.BlockQuery;

import java.util.Objects;

/**
 * Adapts a Bukkit {@link World} into the scanner's {@link BlockQuery}.
 *
 * <p>This is the entire Bukkit boundary on the read side of a fell: {@code TreeScanner}
 * never sees a {@code World}, a {@code Block} or a {@code Material}, which is what keeps
 * it unit testable.
 */
public final class WorldBlockQuery implements BlockQuery {

    private final World world;
    private final BlockTypes types;

    /**
     * @param world the world to read blocks from
     * @param types log/leaf classification; production passes {@link BlockTypes#SERVER_TAGS}
     */
    public WorldBlockQuery(World world, BlockTypes types) {
        this.world = Objects.requireNonNull(world, "world");
        this.types = Objects.requireNonNull(types, "types");
    }

    @Override
    public BlockKind kindAt(BlockPos pos) {
        Material material = world.getBlockAt(pos.x(), pos.y(), pos.z()).getType();
        if (types.isLog(material)) {
            return BlockKind.LOG;
        }
        if (types.isLeaf(material)) {
            return BlockKind.LEAF;
        }
        return BlockKind.OTHER;
    }
}
