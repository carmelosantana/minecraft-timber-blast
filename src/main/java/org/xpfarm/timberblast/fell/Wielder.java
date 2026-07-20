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
import org.bukkit.util.Vector;

/**
 * Everything a fell does to the player who swung the axe.
 *
 * <p>The counterpart to {@link FellWorld} on the player side, and a seam for the same
 * reason: fuel accounting and knockback are load-bearing rules that must be testable
 * without a server.
 */
public interface Wielder {

    /** Whether the player carries at least {@code amount} of {@code material}. */
    boolean hasFuel(Material material, int amount);

    /** Removes {@code amount} of {@code material} from the player's inventory. */
    void consumeFuel(Material material, int amount);

    /** The player's current position. */
    Vector position();

    /** Sets the player's velocity, replacing whatever the explosion gave them. */
    void setVelocity(Vector velocity);

    /** Applies {@code amount} points of durability damage to the axe in the main hand. */
    void damageTool(int amount);
}
