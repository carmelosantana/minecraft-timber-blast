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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded breadth-first search for the tree hanging off a struck log.
 *
 * <p>Contains no Bukkit types by design -- the world arrives through
 * {@link BlockQuery}, so the whole fell decision is unit testable without a server.
 *
 * <p><b>Traversal rule:</b> the search expands through logs only. Leaves are collected
 * when adjacent to a collected log but are never expanded from; traversing leaves would
 * let a canopy that merely touches a neighbouring tree chain both trees into one fell.
 *
 * <p>Neighbours include all 26 surrounding positions rather than the 6 face-adjacent
 * ones: 2x2 jungle trunks and naturally generated diagonal branches connect only
 * diagonally, and a 6-neighbour fill leaves half of such a tree floating.
 */
public final class TreeScanner {

    /** The 26 offsets around a block, in a fixed order so results are reproducible. */
    private static final int[][] NEIGHBOUR_OFFSETS = neighbourOffsets();

    /**
     * Collects the tree rooted at {@code origin}.
     *
     * <p>Bounds are relative to the origin and exclude rather than clamp: a block whose
     * horizontal or vertical distance exceeds them is neither visited nor collected, so
     * a branch reaching past the limit simply stops there.
     *
     * @param origin    the struck block; always {@code logs.get(0)} when it is a log
     * @param query     the world view
     * @param maxBlocks cap on the number of <em>logs</em>; reaching it stops the scan and
     *                  sets {@link ScanResult#truncated()}, keeping the leaves found so far
     * @param maxRadius bound on {@code |x - origin.x|} and {@code |z - origin.z|}
     * @param maxHeight bound on {@code |y - origin.y|}
     * @return the blocks to fell; empty logs and leaves if {@code origin} is not a log
     */
    public ScanResult scan(BlockPos origin, BlockQuery query, int maxBlocks, int maxRadius, int maxHeight) {
        if (query.kindAt(origin) != BlockKind.LOG) {
            return new ScanResult(List.of(), List.of(), false);
        }

        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visitedLogs = new HashSet<>();
        Set<BlockPos> leaves = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        boolean truncated = false;

        logs.add(origin);
        visitedLogs.add(origin);
        frontier.add(origin);

        while (!frontier.isEmpty() && !truncated) {
            BlockPos current = frontier.remove();
            for (int[] offset : NEIGHBOUR_OFFSETS) {
                BlockPos neighbour = current.offset(offset[0], offset[1], offset[2]);
                if (!inBounds(neighbour, origin, maxRadius, maxHeight)
                        || visitedLogs.contains(neighbour)
                        || leaves.contains(neighbour)) {
                    continue;
                }
                switch (query.kindAt(neighbour)) {
                    case LOG -> {
                        // Finish this block's neighbours before stopping, so the leaves
                        // around the last log we did take are still felled with it.
                        if (logs.size() >= maxBlocks) {
                            truncated = true;
                        } else {
                            logs.add(neighbour);
                            visitedLogs.add(neighbour);
                            frontier.add(neighbour);
                        }
                    }
                    case LEAF -> leaves.add(neighbour);
                    case OTHER -> {
                    }
                }
            }
        }

        return new ScanResult(List.copyOf(logs), List.copyOf(leaves), truncated);
    }

    /** Whether {@code pos} is within the configured radius and height of {@code origin}. */
    private static boolean inBounds(BlockPos pos, BlockPos origin, int maxRadius, int maxHeight) {
        return Math.abs(pos.x() - origin.x()) <= maxRadius
                && Math.abs(pos.z() - origin.z()) <= maxRadius
                && Math.abs(pos.y() - origin.y()) <= maxHeight;
    }

    private static int[][] neighbourOffsets() {
        int[][] offsets = new int[26][];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        offsets[index++] = new int[] {dx, dy, dz};
                    }
                }
            }
        }
        return offsets;
    }
}
