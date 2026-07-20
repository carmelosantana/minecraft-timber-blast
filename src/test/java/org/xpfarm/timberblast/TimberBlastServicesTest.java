/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast;

import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.config.ConfigSource;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.fell.FellExecutor;
import org.xpfarm.timberblast.listener.TimberBlastListener;
import org.xpfarm.timberblast.listener.WielderDamageListener;
import org.xpfarm.timberblast.testsupport.FakeBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the reload contract: a reload swaps configuration and registers nothing.
 *
 * <p>This is the defect this class exists to prevent. A reload that rebuilds services and
 * registers a fresh listener leaves the previous one registered, so the Nth reload makes a
 * single swing fell the tree N+1 times -- each fell consuming fuel and creating its own
 * explosion. Nothing about the code looks wrong when that happens, and no existing test
 * would have caught it, so it is tested here directly.
 */
class TimberBlastServicesTest {

    /** Classifies nothing; the scan is not what these tests are about. */
    private static final BlockTypes NO_TYPES = new BlockTypes() {

        @Override
        public boolean isLog(Material material) {
            return false;
        }

        @Override
        public boolean isLeaf(Material material) {
            return false;
        }
    };

    /** A config whose only interesting value is {@code fell.max-blocks}. */
    private static TbConfig configWithMaxBlocks(int maxBlocks) {
        ConfigSource source = new ConfigSource() {

            @Override
            public int getInt(String path, int def) {
                return "fell.max-blocks".equals(path) ? maxBlocks : def;
            }

            @Override
            public double getDouble(String path, double def) {
                return def;
            }

            @Override
            public boolean getBoolean(String path, boolean def) {
                return def;
            }

            @Override
            public String getString(String path, String def) {
                return def;
            }
        };
        return TbConfig.load(source, name -> true, warning -> {
        });
    }

    private static TimberBlastServices services(TbConfig initial, List<Listener> extra) {
        return new TimberBlastServices(initial, stack -> false, NO_TYPES, extra);
    }

    @Test
    @DisplayName("registerOnce hands over every listener exactly once")
    void registersEveryListenerOnce() {
        List<Listener> extra = List.of(new Listener() {
        });
        TimberBlastServices services = services(configWithMaxBlocks(100), extra);

        List<Listener> registered = new ArrayList<>();
        int count = services.registerOnce(registered::add);

        assertEquals(3, count, "trigger + damage + the one extra listener");
        assertEquals(3, registered.size());
        assertEquals(services.listeners(), registered);
    }

    @Test
    @DisplayName("a second registerOnce registers nothing -- the listener-leak guard")
    void secondRegistrationRegistersNothing() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());

        List<Listener> registered = new ArrayList<>();
        int first = services.registerOnce(registered::add);
        int second = services.registerOnce(registered::add);
        int third = services.registerOnce(registered::add);

        assertEquals(2, first);
        assertEquals(0, second, "re-registering would double every fell");
        assertEquals(0, third);
        assertEquals(2, registered.size(), "no listener may be handed over twice");
    }

    @Test
    @DisplayName("isRegistered reports the one-shot state")
    void isRegisteredReportsState() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        assertFalse(services.isRegistered());
        services.registerOnce(listener -> {
        });
        assertTrue(services.isRegistered());
    }

    @Test
    @DisplayName("applyConfig swaps the live configuration")
    void applyConfigSwapsConfiguration() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        assertEquals(100, services.config().fell().maxBlocks());

        services.applyConfig(configWithMaxBlocks(37));

        assertEquals(37, services.config().fell().maxBlocks(),
                "a reload that does not reach the live config does nothing at all");
    }

    @Test
    @DisplayName("applyConfig registers nothing and rebuilds nothing")
    void applyConfigRegistersNothing() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        List<Listener> registered = new ArrayList<>();
        services.registerOnce(registered::add);

        FellExecutor executorBefore = services.executor();
        List<Listener> listenersBefore = services.listeners();

        services.applyConfig(configWithMaxBlocks(37));

        assertEquals(2, registered.size(), "applyConfig must not register a listener");
        assertSame(executorBefore, services.executor(),
                "a rebuilt executor would orphan the registered listeners' guard");
        assertSame(listenersBefore, services.listeners(),
                "rebuilt listeners would have to be registered, and the old ones unregistered");
    }

    @Test
    @DisplayName("the trigger and damage listeners are built from the paired call site")
    void listenersSharePairing() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        List<Listener> listeners = services.listeners();

        assertTrue(listeners.get(0) instanceof TimberBlastListener,
                "the trigger listener must come first");
        assertTrue(listeners.get(1) instanceof WielderDamageListener,
                "its paired damage listener must come second");
        // damageListener() throws IllegalStateException when the trigger listener was built
        // around a bare FellAction, which has no BlastGuard to share -- the exact miswiring
        // that kills the wielder with their own axe. That it returns here proves the
        // executor-taking factory was used.
        //
        // This is a type and construction check only. It does NOT prove the pairing; see
        // theDamageListenerWatchesThisServicesOwnExecutor for that.
        assertNotNull(((TimberBlastListener) listeners.get(0)).damageListener());
    }

    /**
     * The wielder-dies-to-their-own-axe invariant, asserted behaviourally.
     *
     * <p>This is the test the previous round did not have, and its absence was not an
     * oversight in the code -- it was an oversight in the assertion. {@code listenersSharePairing}
     * above proves only that <em>a</em> {@link WielderDamageListener} sits at index 1. Swapping
     * the constructor's {@code all.add(trigger.damageListener())} for a listener derived from a
     * second, unrelated {@link FellExecutor} -- the exact miswiring both class notes warn about
     * -- left the whole suite green, because a foreign listener is still a
     * {@code WielderDamageListener} and the trigger listener still mints one on demand.
     *
     * <p>So this asserts the property itself: arm the guard of the executor this instance
     * actually fells with, fire a real explosion damage event at the listener this instance
     * actually registers, and require the damage to be suppressed. A listener watching any
     * other guard sees a guard nobody armed and lets the wielder take the full 12.0.
     */
    @Test
    @DisplayName("the registered damage listener watches the guard of the executor that fells")
    void theDamageListenerWatchesThisServicesOwnExecutor() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        UUID wielder = UUID.fromString("00000000-0000-0000-0000-00000000beef");
        Player victim = FakeBlocks.stub(Player.class, "the wielder",
                Map.of("getUniqueId", wielder));
        DamageSource source = FakeBlocks.stub(DamageSource.class, "the blast", Map.of());
        EntityDamageEvent event =
                new EntityDamageEvent(victim, DamageCause.BLOCK_EXPLOSION, source, 12.0);

        // Arm the guard of this services instance's executor -- nothing else.
        services.executor().guard().begin(wielder);
        ((WielderDamageListener) services.listeners().get(1)).onEntityDamage(event);

        assertEquals(0.0, event.getDamage(), 1.0E-9,
                "the registered damage listener must watch the executor this instance fells "
                        + "with; any other guard is never armed and the wielder dies to their "
                        + "own axe");
    }

    /**
     * The same property stated the other way: suppression is specific to the armed guard, not
     * something a {@code WielderDamageListener} does for any blast at all. Without this, a
     * listener that suppressed unconditionally would pass the test above.
     */
    @Test
    @DisplayName("an unrelated executor's blast window does not protect this wielder")
    void aForeignGuardDoesNotProtectTheWielder() {
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        TimberBlastServices unrelated = services(configWithMaxBlocks(100), List.of());
        UUID wielder = UUID.fromString("00000000-0000-0000-0000-00000000beef");
        Player victim = FakeBlocks.stub(Player.class, "the wielder",
                Map.of("getUniqueId", wielder));
        DamageSource source = FakeBlocks.stub(DamageSource.class, "the blast", Map.of());
        EntityDamageEvent event =
                new EntityDamageEvent(victim, DamageCause.BLOCK_EXPLOSION, source, 12.0);

        unrelated.executor().guard().begin(wielder);
        ((WielderDamageListener) services.listeners().get(1)).onEntityDamage(event);

        assertEquals(12.0, event.getDamage(), 1.0E-9,
                "suppression must be tied to the guard that was armed, not unconditional");
    }

    @Test
    @DisplayName("null arguments are refused at construction rather than at the first swing")
    void nullArgumentsRefused() {
        assertThrows(NullPointerException.class,
                () -> services(null, List.of()));
        TimberBlastServices services = services(configWithMaxBlocks(100), List.of());
        assertThrows(NullPointerException.class, () -> services.applyConfig(null));
        assertThrows(NullPointerException.class, () -> services.registerOnce(null));
    }

    @Test
    @DisplayName("a null extra-listener list is treated as empty")
    void nullExtrasTreatedAsEmpty() {
        TimberBlastServices services = services(configWithMaxBlocks(100), null);
        assertEquals(2, services.listeners().size());
    }
}
