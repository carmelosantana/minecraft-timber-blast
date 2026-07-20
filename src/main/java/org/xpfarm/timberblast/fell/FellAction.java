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

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * The one thing {@code TimberBlastListener} asks for once it has decided a swing qualifies.
 * {@link FellExecutor} is the production implementation; tests record the call instead.
 */
@FunctionalInterface
public interface FellAction {

    /**
     * Fells the tree {@code struck} belongs to, on behalf of {@code wielder}.
     *
     * @param wielder the player who swung the axe
     * @param struck  the log they hit; becomes the origin of the scan and the explosion
     */
    void fell(Player wielder, Block struck);
}
