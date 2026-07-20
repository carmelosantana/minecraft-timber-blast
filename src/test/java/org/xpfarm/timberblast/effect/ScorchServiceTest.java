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
import org.xpfarm.timberblast.config.CoalSettings;
import org.xpfarm.timberblast.config.ExplosionSettings;
import org.xpfarm.timberblast.config.FellSettings;
import org.xpfarm.timberblast.config.FuelSettings;
import org.xpfarm.timberblast.config.ScorchSettings;
import org.xpfarm.timberblast.config.TbConfig;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ScorchService}'s decisions: the two static config gates, and the
 * two methods that combine them with tracker state -- {@code scorchInto} (does a strike
 * light and register a fire?) and {@code shouldCancel} (must this event be vetoed?).
 *
 * <p>Both take a {@link FirePos} rather than a {@code Block}, so the whole decision half
 * of the service runs here with no server. What is left uncovered is the event adapters'
 * choice of accessor; {@code ScorchListenerTest} covers what it can of that, and the
 * remainder is listed as gate 7a residue in the task report.
 */
class ScorchServiceTest {

    private static final FirePos POS = new FirePos("world", 0, 64, 0);

    /** Positions that currently hold fire, standing in for the live world. */
    private final Set<FirePos> burning = new HashSet<>();

    /** Live, mutable config: tests reassign it to simulate {@code /timberblast reload}. */
    private ScorchSettings scorch = new ScorchSettings(true, false);

    private final ScorchService service =
            new ScorchService(this::config, burning::contains);

    private TbConfig config() {
        return new TbConfig(
                new FellSettings(64, 8, 16, true),
                new FuelSettings("GUNPOWDER", 1),
                new ExplosionSettings(2.0, false, 1.0),
                new CoalSettings(true, "COAL"),
                scorch);
    }

    /** Runs the ignite callback for a strike into air, recording whether it fired. */
    private boolean strikeIntoAir() {
        return strike(true);
    }

    private boolean strike(boolean aboveIsAir) {
        boolean[] lit = {false};
        service.scorchInto(POS, aboveIsAir, () -> {
            lit[0] = true;
            burning.add(POS);
        });
        return lit[0];
    }

    // =================================================================================
    // The static config gates
    // =================================================================================

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

    // =================================================================================
    // scorchInto -- the gates are actually consulted, and acted on
    // =================================================================================

    @Test
    void strikeIntoAir_lightsTheFireAndRegistersIt() {
        assertTrue(strikeIntoAir(), "the ignite action must run");
        assertEquals(1, service.trackedFireCount(),
                "an unregistered fire is an uncontained fire");
    }

    @Test
    void strikeWithScorchDisabled_neverRunsTheIgniteAction() {
        scorch = new ScorchSettings(false, false);

        assertFalse(strikeIntoAir(), "scorch.enabled = false must be a total no-op");
        assertEquals(0, service.trackedFireCount());
    }

    @Test
    void strikeIntoANonAirBlock_neverRunsTheIgniteAction() {
        assertFalse(strike(false),
                "lighting here would replace a block the player never broke");
        assertEquals(0, service.trackedFireCount());
    }

    @Test
    void strikeWithSpreadEnabled_lightsTheFireButLeavesItUntracked() {
        scorch = new ScorchSettings(true, true);

        assertTrue(strikeIntoAir(), "spread = true still lights the fire");
        assertEquals(0, service.trackedFireCount(),
                "the operator asked for vanilla fire; tracking it would contain it anyway");
    }

    // =================================================================================
    // shouldCancel -- the containment veto
    // =================================================================================

    @Test
    void eventFromOurBurningFire_isCancelled() {
        strikeIntoAir();

        assertTrue(service.shouldCancel(POS),
                "this single answer is the whole feature: our fire may not spread");
    }

    @Test
    void eventFromSomebodyElsesFire_isNotCancelled() {
        strikeIntoAir();
        FirePos campfire = new FirePos("world", 40, 64, 40);
        burning.add(campfire);

        assertFalse(service.shouldCancel(campfire),
                "vanilla fire we did not light must spread exactly as vanilla intends");
    }

    @Test
    void eventWithNoSourceBlock_isNotCancelled() {
        strikeIntoAir();

        assertFalse(service.shouldCancel(null),
                "flint and steel and lightning name no source block and are never ours");
    }

    @Test
    void eventFromAPositionWhoseFireWentOut_isNotCancelled() {
        strikeIntoAir();
        burning.remove(POS);

        assertFalse(service.shouldCancel(POS),
                "our fire is gone; whatever burns there now belongs to somebody else");
    }

    @Test
    void reloadEnablingSpread_stopsCancellingAlreadyTrackedFires() {
        strikeIntoAir();
        assertTrue(service.shouldCancel(POS));

        scorch = new ScorchSettings(true, true); // /timberblast reload

        assertFalse(service.shouldCancel(POS),
                "the gate must be read live at handling time, not frozen at registration");
    }

    @Test
    void reloadDisablingScorch_stopsCancellingAlreadyTrackedFires() {
        strikeIntoAir();

        scorch = new ScorchSettings(false, false); // /timberblast reload

        assertFalse(service.shouldCancel(POS));
    }

    @Test
    void reloadDisablingSpread_startsContainingImmediately() {
        scorch = new ScorchSettings(true, true);
        strikeIntoAir();
        assertFalse(service.shouldCancel(POS), "untracked while spread was on");

        scorch = new ScorchSettings(true, false); // /timberblast reload
        strikeIntoAir();

        assertTrue(service.shouldCancel(POS),
                "a reload turning containment back on must take effect without a restart -- "
                        + "the listeners are always registered, so there is nothing to miss");
    }

    @Test
    void clear_forgetsEveryTrackedFire() {
        strikeIntoAir();

        service.clear();

        assertEquals(0, service.trackedFireCount());
        assertFalse(service.shouldCancel(POS),
                "on disable, still-burning scorch fire reverts to vanilla behaviour");
    }
}
