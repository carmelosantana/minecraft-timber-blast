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
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.testsupport.FakeBlocks;
import org.xpfarm.timberblast.tree.BlockKind;
import org.xpfarm.timberblast.tree.BlockPos;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the read side of the Bukkit boundary, including the argument order of
 * {@code World#getBlockAt(x, y, z)} -- swapping two of those coordinates would fell a tree
 * somewhere else entirely and no other test would notice.
 */
class WorldBlockQueryTest {

    private static final BlockTypes VANILLA_ISH = new BlockTypes() {

        @Override
        public boolean isLog(Material material) {
            return material == Material.OAK_LOG;
        }

        @Override
        public boolean isLeaf(Material material) {
            return material == Material.OAK_LEAVES;
        }
    };

    /** A world where only the listed coordinates hold anything. */
    private static World worldOf(Map<String, Material> blocks) {
        return (World) Proxy.newProxyInstance(WorldBlockQueryTest.class.getClassLoader(),
                new Class<?>[]{World.class}, (self, method, args) -> {
                    if (method.getName().equals("getBlockAt") && args.length == 3) {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        Material material = blocks.getOrDefault(x + "," + y + "," + z, Material.STONE);
                        return FakeBlocks.at("w", x, y, z, material);
                    }
                    if (method.getName().equals("toString")) {
                        return "the world";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void classifiesLogsLeavesAndEverythingElse() {
        Map<String, Material> blocks = new HashMap<>();
        blocks.put("1,2,3", Material.OAK_LOG);
        blocks.put("4,5,6", Material.OAK_LEAVES);
        WorldBlockQuery query = new WorldBlockQuery(worldOf(blocks), VANILLA_ISH);

        assertEquals(BlockKind.LOG, query.kindAt(new BlockPos(1, 2, 3)));
        assertEquals(BlockKind.LEAF, query.kindAt(new BlockPos(4, 5, 6)));
        assertEquals(BlockKind.OTHER, query.kindAt(new BlockPos(7, 8, 9)));
    }

    @Test
    void readsTheBlockAtExactlyTheRequestedCoordinates() {
        Map<String, Material> blocks = new HashMap<>();
        blocks.put("1,2,3", Material.OAK_LOG);
        WorldBlockQuery query = new WorldBlockQuery(worldOf(blocks), VANILLA_ISH);

        assertEquals(BlockKind.LOG, query.kindAt(new BlockPos(1, 2, 3)));
        assertEquals(BlockKind.OTHER, query.kindAt(new BlockPos(3, 2, 1)),
                "x and z transposed must not resolve to the same block");
        assertEquals(BlockKind.OTHER, query.kindAt(new BlockPos(2, 1, 3)),
                "x and y transposed must not resolve to the same block");
    }

    @Test
    void readsTheBlocksTypeAndNothingElseAboutIt() {
        // FakeBlocks throws on any accessor it was not asked to fake, so this passing at
        // all is the assertion: the adapter reads getType() and no other block state.
        Block block = FakeBlocks.at("w", 0, 0, 0, Material.OAK_LOG);

        assertEquals(Material.OAK_LOG, block.getType());
    }
}
