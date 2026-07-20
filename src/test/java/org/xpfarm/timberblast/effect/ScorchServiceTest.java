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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ScorchService}'s config gates. These are static functions over
 * primitives, so they run with no server; everything in {@code ScorchService} that
 * touches a {@code Block}, a {@code World}, or an event is deferred to runtime
 * verification (gate 7a) and is listed in the task report.
 */
class ScorchServiceTest {

    @Test
    void scorchDisabled_doesNotLightFire_evenIntoAir() {
        assertFalse(ScorchService.shouldScorch(false, true));
    }

    @Test
    void scorchEnabled_lightsFireWhenTheBlockAboveIsAir() {
        assertTrue(ScorchService.shouldScorch(true, true));
    }

    @Test
    void scorchEnabled_doesNotReplaceANonAirBlockAbove() {
        assertFalse(ScorchService.shouldScorch(true, false),
                "a cosmetic effect must never destroy a block the player did not break");
    }

    @Test
    void scorchDisabledAndBlockOccupied_doesNotLightFire() {
        assertFalse(ScorchService.shouldScorch(false, false));
    }

    @Test
    void containmentApplies_whenScorchIsOnAndSpreadIsOff() {
        assertTrue(ScorchService.shouldContainFire(true, false),
                "the shipped defaults must produce contained fire -- this is the feature");
    }

    @Test
    void spreadEnabled_skipsRegistration_soVanillaFireBehaviorApplies() {
        assertFalse(ScorchService.shouldContainFire(true, true));
    }

    @Test
    void scorchDisabled_hasNothingToContain() {
        assertFalse(ScorchService.shouldContainFire(false, false));
        assertFalse(ScorchService.shouldContainFire(false, true));
    }
}
