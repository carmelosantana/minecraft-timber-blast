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
import org.bukkit.Tag;

/**
 * Decides whether a {@link Material} is a log or leaves.
 *
 * <p>This exists as an interface for one reason: {@link Tag#LOGS} is a static field
 * resolved from the running server's block-tag registry, so touching it without a server
 * throws {@code ExceptionInInitializerError}. Everything that classifies blocks therefore
 * goes through this seam, and unit tests supply their own classifier while production
 * uses {@link #SERVER_TAGS}.
 */
public interface BlockTypes {

    /** Classification backed by the server's vanilla {@code minecraft:logs}/{@code minecraft:leaves} tags. */
    BlockTypes SERVER_TAGS = new BlockTypes() {

        @Override
        public boolean isLog(Material material) {
            return material != null && Tag.LOGS.isTagged(material);
        }

        @Override
        public boolean isLeaf(Material material) {
            return material != null && Tag.LEAVES.isTagged(material);
        }
    };

    /** Whether {@code material} is a log or stem. */
    boolean isLog(Material material);

    /** Whether {@code material} is leaves. */
    boolean isLeaf(Material material);
}
