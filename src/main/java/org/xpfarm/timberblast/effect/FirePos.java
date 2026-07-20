/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.effect;

/**
 * Identity of one tracked scorch fire: a world name plus absolute block coordinates.
 *
 * <p>Deliberately not a {@code org.bukkit.Location} or {@code Block} reference. Holding
 * either would drag Bukkit into {@link ScorchTracker} and could keep a world or chunk
 * alive longer than it should be. The world <em>name</em> is part of the identity on
 * purpose: two worlds routinely have a block at the same coordinates, and a
 * coordinates-only key would let a scorch fire in the nether silence legitimate fire
 * spread at the same x/y/z in the overworld.
 *
 * @param world the world's name
 * @param x     absolute block x
 * @param y     absolute block y
 * @param z     absolute block z
 */
public record FirePos(String world, int x, int y, int z) {
}
