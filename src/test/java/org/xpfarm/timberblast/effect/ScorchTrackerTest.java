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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ScorchTracker} against a hand-built world view: a mutable set of
 * positions that currently hold fire, standing in for the live world so the whole class
 * is exercised without a server.
 */
class ScorchTrackerTest {

    private static final String WORLD = "world";
    private static final String NETHER = "world_nether";

    /** Positions that currently hold fire; tests burn fires out by removing from this. */
    private final Set<FirePos> burning = new HashSet<>();

    private final ScorchTracker tracker = new ScorchTracker(burning::contains);

    private static FirePos at(int x, int y, int z) {
        return new FirePos(WORLD, x, y, z);
    }

    /** Lights a fire in the fake world and tracks it, as {@code scorch()} would. */
    private FirePos light(int x, int y, int z) {
        FirePos pos = at(x, y, z);
        burning.add(pos);
        tracker.track(pos);
        return pos;
    }

    @Test
    void trackedFire_isRecognisedAsOurs() {
        FirePos pos = light(0, 64, 0);

        assertTrue(tracker.isScorchFire(pos));
    }

    @Test
    void untrackedFire_isNotOurs() {
        light(0, 64, 0);
        burning.add(at(5, 64, 5)); // somebody else's fire, burning but never tracked

        assertFalse(tracker.isScorchFire(at(5, 64, 5)),
                "fire the plugin did not light must never be claimed -- containment would "
                        + "otherwise cancel legitimate vanilla fire spread");
    }

    @Test
    void samePositionInAnotherWorld_isNotOurs() {
        light(0, 64, 0);

        assertFalse(tracker.isScorchFire(new FirePos(NETHER, 0, 64, 0)),
                "world name is part of the fire's identity");
    }

    @Test
    void nullPosition_isNotOurs() {
        light(0, 64, 0);

        assertFalse(tracker.isScorchFire(null));
    }

    @Test
    void burntOutFire_isNoLongerOurs_andIsEvicted() {
        FirePos pos = light(0, 64, 0);
        burning.remove(pos); // the fire went out

        assertFalse(tracker.isScorchFire(pos),
                "once our fire is gone, anything at that position belongs to somebody else");
        assertEquals(0, tracker.size(), "the burnt-out entry must be evicted on query");
    }

    @Test
    void positionRelitByAnotherSource_isNotClaimed() {
        FirePos pos = light(0, 64, 0);
        burning.remove(pos);
        assertFalse(tracker.isScorchFire(pos));

        burning.add(pos); // unrelated fire later occupies the same block

        assertFalse(tracker.isScorchFire(pos),
                "a position we no longer own must not be reclaimed when fire reappears there");
    }

    @Test
    void trackingSweepsOutEveryBurntOutEntry() {
        FirePos first = light(0, 64, 0);
        FirePos second = light(1, 64, 0);
        FirePos third = light(2, 64, 0);
        assertEquals(3, tracker.size());

        burning.remove(first);
        burning.remove(third);

        light(3, 64, 0);

        assertEquals(2, tracker.size(),
                "track() must sweep stale entries so the set is bounded by fires currently "
                        + "burning, not by fires ever lit");
        assertTrue(tracker.isScorchFire(second));
        assertTrue(tracker.isScorchFire(at(3, 64, 0)));
        assertFalse(tracker.isScorchFire(first));
        assertFalse(tracker.isScorchFire(third));
    }

    @Test
    void repeatedScorchingOfBurntOutFires_doesNotGrowTheSet() {
        // Stands in for a long-running server: many swings over time, each fire burning
        // out before the next. Without eviction the set would reach 500.
        for (int i = 0; i < 500; i++) {
            FirePos pos = light(i, 64, 0);
            burning.remove(pos);
        }
        light(0, 100, 0);

        assertEquals(1, tracker.size(), "the set must not grow without bound");
    }

    @Test
    void stillBurningFires_areNotEvictedBySweep() {
        light(0, 64, 0);
        light(1, 64, 0);

        light(2, 64, 0);

        assertEquals(3, tracker.size(), "the sweep must only drop entries that stopped burning");
    }

    @Test
    void trackingTheSamePositionTwice_keepsOneEntry() {
        light(0, 64, 0);
        light(0, 64, 0);

        assertEquals(1, tracker.size());
    }

    @Test
    void clear_forgetsEveryTrackedFire() {
        light(0, 64, 0);
        FirePos second = light(1, 64, 0);
        assertEquals(2, tracker.size());

        tracker.clear();

        assertEquals(0, tracker.size());
        assertFalse(tracker.isScorchFire(second),
                "after disable, still-burning scorch fire reverts to vanilla behavior");
    }

    @Test
    void trackingNull_isRejected() {
        assertThrows(NullPointerException.class, () -> tracker.track(null));
    }

    @Test
    void nullPredicate_isRejected() {
        assertThrows(NullPointerException.class, () -> new ScorchTracker(null));
    }
}
