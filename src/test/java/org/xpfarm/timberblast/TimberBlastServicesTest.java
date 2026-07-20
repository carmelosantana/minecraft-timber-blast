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
import org.bukkit.event.Listener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.config.ConfigSource;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.fell.FellExecutor;
import org.xpfarm.timberblast.listener.TimberBlastListener;
import org.xpfarm.timberblast.listener.WielderDamageListener;

import java.util.ArrayList;
import java.util.List;

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
        assertNotNull(((TimberBlastListener) listeners.get(0)).damageListener());
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
