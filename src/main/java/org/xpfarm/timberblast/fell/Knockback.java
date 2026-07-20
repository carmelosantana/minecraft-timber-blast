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

import org.bukkit.util.Vector;

/** The knockback vector a fell hands the wielder. Pure arithmetic, no server needed. */
public final class Knockback {

    /**
     * Below this squared distance the direction is meaningless -- a player standing exactly
     * inside the struck block -- and normalising would divide by zero.
     */
    private static final double DEGENERATE = 1.0E-6;

    private Knockback() {
    }

    /**
     * A unit vector pointing from {@code origin} to {@code target}, scaled by
     * {@code multiplier}.
     *
     * <p>Direction is <em>away from</em> the blast: {@code target - origin}, so a player
     * north of the struck log is thrown further north. The result is always exactly
     * {@code multiplier} long, which is what makes {@code explosion.knockback-multiplier}
     * a predictable dial rather than something that also varies with how far away the
     * player happened to be standing.
     *
     * @param origin     centre of the struck block
     * @param target     the wielder's position
     * @param multiplier configured {@code explosion.knockback-multiplier}
     * @return a fresh vector; neither argument is mutated
     */
    public static Vector away(Vector origin, Vector target, double multiplier) {
        Vector delta = target.clone().subtract(origin);
        if (delta.lengthSquared() < DEGENERATE) {
            // Standing in the block itself: throw them straight up rather than nowhere.
            return new Vector(0, multiplier, 0);
        }
        return delta.normalize().multiply(multiplier);
    }
}
