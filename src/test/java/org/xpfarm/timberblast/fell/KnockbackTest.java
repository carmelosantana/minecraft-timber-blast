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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockbackTest {

    private static final double EPS = 1.0E-9;

    @Test
    void pointsFromTheOriginTowardsTheTarget() {
        Vector v = Knockback.away(new Vector(0, 64, 0), new Vector(3, 64, 0), 1.0);

        assertEquals(1.0, v.getX(), EPS, "positive x: the player is east, so they go east");
        assertEquals(0.0, v.getY(), EPS);
        assertEquals(0.0, v.getZ(), EPS);
    }

    @Test
    void reversingOriginAndTargetReversesTheThrow() {
        Vector v = Knockback.away(new Vector(3, 64, 0), new Vector(0, 64, 0), 1.0);

        assertEquals(-1.0, v.getX(), EPS);
    }

    @Test
    void theLengthIsTheMultiplierRegardlessOfDistance() {
        Vector near = Knockback.away(new Vector(0, 0, 0), new Vector(0, 0, 1), 2.5);
        Vector far = Knockback.away(new Vector(0, 0, 0), new Vector(0, 0, 40), 2.5);

        assertEquals(2.5, near.length(), EPS);
        assertEquals(2.5, far.length(), EPS);
    }

    @Test
    void aZeroMultiplierMeansNoThrow() {
        assertEquals(0.0, Knockback.away(new Vector(0, 0, 0), new Vector(0, 0, 5), 0.0).length(), EPS);
    }

    @Test
    void theThrowKeepsItsDiagonalDirection() {
        Vector v = Knockback.away(new Vector(0, 0, 0), new Vector(3, 0, 4), 1.0);

        assertEquals(0.6, v.getX(), EPS);
        assertEquals(0.8, v.getZ(), EPS);
    }

    @Test
    void aTargetInsideTheOriginGoesStraightUpRatherThanNowhere() {
        Vector v = Knockback.away(new Vector(1.5, 64.5, 1.5), new Vector(1.5, 64.5, 1.5), 1.5);

        assertEquals(0.0, v.getX(), EPS);
        assertEquals(1.5, v.getY(), EPS);
        assertEquals(0.0, v.getZ(), EPS);
        assertTrue(Double.isFinite(v.getY()), "normalising a zero vector would give NaN");
    }

    @Test
    void neitherArgumentIsMutated() {
        Vector origin = new Vector(0, 0, 0);
        Vector target = new Vector(0, 0, 9);

        Knockback.away(origin, target, 3.0);

        assertEquals(0.0, origin.length(), EPS);
        assertEquals(9.0, target.length(), EPS);
    }
}
