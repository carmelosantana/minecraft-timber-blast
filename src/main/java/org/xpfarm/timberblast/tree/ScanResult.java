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

import java.util.List;

/**
 * The blocks a single {@link TreeScanner#scan} decided to fell.
 *
 * @param logs      every log to break, breadth-first from the struck block, which is
 *                  always element {@code 0}; empty if the origin was not a log
 * @param leaves    every leaf adjacent to a collected log, in the order encountered
 * @param truncated whether the scan hit its block cap and left part of the tree standing
 */
public record ScanResult(List<BlockPos> logs, List<BlockPos> leaves, boolean truncated) {

    /**
     * Defensively copies both lists, so a {@code ScanResult} is immutable no matter which
     * caller built it and neither list can be null.
     *
     * @throws NullPointerException if either list, or any element of either, is null
     */
    public ScanResult {
        logs = List.copyOf(logs);
        leaves = List.copyOf(leaves);
    }
}
