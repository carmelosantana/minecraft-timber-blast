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
 * The only three things {@link TreeScanner} needs to know about a block: whether it is
 * part of a trunk, part of a canopy, or neither. Mapping Minecraft materials onto these
 * is the caller's job, which is what keeps this package Bukkit-free.
 */
public enum BlockKind {

    /** A log or stem -- the scan traverses through these. */
    LOG,

    /** Leaves -- collected when adjacent to a log, but never traversed through. */
    LEAF,

    /** Anything else; the scan stops here. */
    OTHER
}
