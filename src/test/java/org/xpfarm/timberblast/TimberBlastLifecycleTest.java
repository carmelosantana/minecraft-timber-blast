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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.config.ConfigSource;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.testsupport.FakeBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two compositions that used to live inside {@code TimberBlastPlugin}.
 *
 * <p>They were extracted precisely because they could not be covered where they were:
 * {@code JavaPlugin} cannot be instantiated offline, so every mutation applied inside it
 * built green. Three did, and all three are pinned here -- a reload that applies nothing,
 * a registrar that registers nothing, and a reload that swallows its warnings.
 */
class TimberBlastLifecycleTest {

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

    private static TimberBlastServices services(TbConfig initial) {
        return new TimberBlastServices(initial, stack -> false, NO_TYPES, List.of());
    }

    // ---------------------------------------------------------------- registerListeners

    /** What a {@code registerEvents(listener, plugin)} call recorded. */
    private record Registration(Listener listener, Plugin plugin) {
    }

    private static PluginManager recordingManager(List<Registration> into) {
        return FakeBlocks.stub(PluginManager.class, "the plugin manager", Map.of(
                "registerEvents", (FakeBlocks.Answer) args -> {
                    into.add(new Registration((Listener) args[0], (Plugin) args[1]));
                    return null;
                }));
    }

    @Test
    @DisplayName("every listener actually reaches the server's plugin manager")
    void registerListenersHandsEveryListenerToTheManager() {
        TimberBlastServices services = services(configWithMaxBlocks(100));
        Plugin plugin = FakeBlocks.stub(Plugin.class, "the plugin", Map.of());
        List<Registration> registered = new ArrayList<>();

        int count = TimberBlastLifecycle.registerListeners(
                services, recordingManager(registered), plugin);

        assertEquals(services.listeners().size(), count);
        assertEquals(services.listeners(),
                registered.stream().map(Registration::listener).toList(),
                "a registrar that registers nothing leaves the plugin enabled and inert");
        assertTrue(registered.stream().allMatch(entry -> entry.plugin() == plugin),
                "listeners registered against another plugin would unregister with it");
        assertTrue(services.isRegistered());
    }

    @Test
    @DisplayName("registerListeners inherits the listener-leak guard")
    void registerListenersIsIdempotent() {
        TimberBlastServices services = services(configWithMaxBlocks(100));
        Plugin plugin = FakeBlocks.stub(Plugin.class, "the plugin", Map.of());
        List<Registration> registered = new ArrayList<>();
        PluginManager manager = recordingManager(registered);

        int first = TimberBlastLifecycle.registerListeners(services, manager, plugin);
        int second = TimberBlastLifecycle.registerListeners(services, manager, plugin);

        assertEquals(2, first);
        assertEquals(0, second);
        assertEquals(2, registered.size(), "a second registration would double every fell");
    }

    @Test
    @DisplayName("registerListeners refuses nulls rather than half-registering")
    void registerListenersRefusesNulls() {
        TimberBlastServices services = services(configWithMaxBlocks(100));
        Plugin plugin = FakeBlocks.stub(Plugin.class, "the plugin", Map.of());
        PluginManager manager = recordingManager(new ArrayList<>());

        assertThrows(NullPointerException.class,
                () -> TimberBlastLifecycle.registerListeners(null, manager, plugin));
        assertThrows(NullPointerException.class,
                () -> TimberBlastLifecycle.registerListeners(services, null, plugin));
        assertThrows(NullPointerException.class,
                () -> TimberBlastLifecycle.registerListeners(services, manager, null));
    }

    // ------------------------------------------------------------------------- reload

    @Test
    @DisplayName("reload applies the newly parsed configuration to the live services")
    void reloadAppliesTheNewConfiguration() {
        TimberBlastServices services = services(configWithMaxBlocks(100));

        List<String> warnings = TimberBlastLifecycle.reload(
                () -> {
                },
                warn -> configWithMaxBlocks(37),
                services,
                () -> {
                },
                message -> {
                });

        assertEquals(37, services.config().fell().maxBlocks(),
                "a reload that parses the file and then applies nothing reports success "
                        + "while changing nothing -- the other half of the no-leak requirement");
        assertEquals(List.of(), warnings);
    }

    @Test
    @DisplayName("reload re-reads the file before parsing it")
    void reloadReReadsTheFileFirst() {
        List<String> order = new ArrayList<>();
        TimberBlastServices services = services(configWithMaxBlocks(100));

        TimberBlastLifecycle.reload(
                () -> order.add("re-read"),
                warn -> {
                    order.add("parse");
                    return configWithMaxBlocks(37);
                },
                services,
                () -> order.add("recipe"),
                message -> {
                });

        assertEquals(List.of("re-read", "parse", "recipe"), order,
                "parsing before re-reading would re-apply the configuration already in memory, "
                        + "so an operator's edit would never take effect");
    }

    @Test
    @DisplayName("reload re-registers the crafting recipe")
    void reloadRegistersTheRecipe() {
        AtomicInteger registrations = new AtomicInteger();
        TimberBlastServices services = services(configWithMaxBlocks(100));

        TimberBlastLifecycle.reload(() -> {
        }, warn -> configWithMaxBlocks(37), services, registrations::incrementAndGet,
                message -> {
                });

        assertEquals(1, registrations.get());
    }

    @Test
    @DisplayName("reload returns every validation warning the parse produced")
    void reloadReturnsItsWarnings() {
        TimberBlastServices services = services(configWithMaxBlocks(100));
        List<String> logged = new ArrayList<>();

        List<String> warnings = TimberBlastLifecycle.reload(
                () -> {
                },
                warn -> {
                    warn.accept("fell.max-blocks was 99999");
                    warn.accept("fuel.material was CHEESE");
                    return configWithMaxBlocks(37);
                },
                services,
                () -> {
                },
                logged::add);

        assertEquals(List.of("fell.max-blocks was 99999", "fuel.material was CHEESE"), warnings,
                "returning an empty list would report a clean reload while suppressing every "
                        + "warning the operator needs to see");
        assertEquals(warnings, logged, "the warnings must reach the server log as well as chat");
    }

    @Test
    @DisplayName("reload survives a plugin whose services or item failed to wire on enable")
    void reloadToleratesMissingCollaborators() {
        List<String> warnings = TimberBlastLifecycle.reload(
                () -> {
                },
                warn -> {
                    warn.accept("still parsed");
                    return configWithMaxBlocks(37);
                },
                null,
                null,
                message -> {
                });

        assertEquals(List.of("still parsed"), warnings,
                "a degraded plugin must still report what its config file says");
    }

    @Test
    @DisplayName("reload refuses nulls for the collaborators it cannot do without")
    void reloadRefusesNulls() {
        TimberBlastServices services = services(configWithMaxBlocks(100));

        assertThrows(NullPointerException.class, () -> TimberBlastLifecycle.reload(
                null, warn -> configWithMaxBlocks(1), services, () -> {
                }, message -> {
                }));
        assertThrows(NullPointerException.class, () -> TimberBlastLifecycle.reload(
                () -> {
                }, null, services, () -> {
                }, message -> {
                }));
        assertThrows(NullPointerException.class, () -> TimberBlastLifecycle.reload(
                () -> {
                }, warn -> configWithMaxBlocks(1), services, () -> {
                }, null));
    }
}
