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

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.xpfarm.timberblast.config.TbConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The two compositions {@link TimberBlastPlugin} performs -- registering the listeners on
 * enable, and the reload sequence -- extracted behind suppliers so they can run without a
 * server.
 *
 * <h2>Why this class exists</h2>
 *
 * <p>{@link org.bukkit.plugin.java.JavaPlugin} cannot be instantiated offline, so anything
 * left inside {@code TimberBlastPlugin} is unreachable by the test suite. That is a fine
 * place for {@code getServer()} and {@code getConfig()}; it is a bad place for decisions.
 * Two defects proved the point: deleting the {@code applyConfig} call gave a reload that
 * reported success while changing nothing, and replacing the listener registrar with
 * {@code listener -> { }} gave a plugin that registered nothing and did nothing. Both built
 * green, because both lived in the one class no test could reach.
 *
 * <p>What is left in {@code TimberBlastPlugin} after this extraction is field assignment and
 * calls into the {@code JavaPlugin} API itself. The decisions live here.
 */
public final class TimberBlastLifecycle {

    private TimberBlastLifecycle() {
    }

    /** Re-reads the configuration file into memory. In production {@code reloadConfig()}. */
    @FunctionalInterface
    public interface ConfigLoader {

        /**
         * @param warn receives every validation warning the parse produced
         * @return the parsed configuration; never {@code null}
         */
        TbConfig load(Consumer<String> warn);
    }

    /** Re-registers the crafting recipe. In production {@code TimberBlastItem::registerRecipe}. */
    @FunctionalInterface
    public interface RecipeRegistrar {

        void register();
    }

    /**
     * Hands every listener {@code services} owns to the server's plugin manager.
     *
     * <p>Trivial-looking, and extracted anyway: this call is the difference between a
     * plugin that works and a plugin that enables cleanly, logs nothing alarming, and
     * responds to no event at all.
     *
     * @return how many listeners were registered -- the full count on the first call, and
     *         {@code 0} on any call after it, because {@link TimberBlastServices#registerOnce}
     *         is the listener-leak guard
     */
    public static int registerListeners(TimberBlastServices services, PluginManager manager,
                                        Plugin plugin) {
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(plugin, "plugin");
        return services.registerOnce(listener -> manager.registerEvents(listener, plugin));
    }

    /**
     * The whole of {@code /timberblast reload}: re-read the file, parse it, apply it live,
     * and refresh the recipe.
     *
     * <p>Constructs no service and registers no listener -- see {@link TimberBlastServices}
     * for why that is the entire point. The configuration is applied only once it has fully
     * parsed, so a parse that fails part-way leaves the previously working settings in place.
     *
     * @param reReadConfigFile re-reads {@code config.yml} from disk; in production
     *                         {@code JavaPlugin::reloadConfig}
     * @param loadConfig       parses the re-read file
     * @param services         the live services to apply the new configuration to;
     *                         {@code null} when the fell services failed to wire on enable
     * @param recipe           re-registers the crafting recipe; {@code null} when the item
     *                         layer failed to wire on enable
     * @param warn             the server log sink
     * @return the validation warnings this reload produced, in order; empty when the file was
     *         clean. Returning them rather than only logging them is what lets the command
     *         show an operator the result of their edit in chat.
     */
    public static List<String> reload(Runnable reReadConfigFile, ConfigLoader loadConfig,
                                      TimberBlastServices services, RecipeRegistrar recipe,
                                      Consumer<String> warn) {
        Objects.requireNonNull(reReadConfigFile, "reReadConfigFile");
        Objects.requireNonNull(loadConfig, "loadConfig");
        Objects.requireNonNull(warn, "warn");

        reReadConfigFile.run();

        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = message -> {
            warnings.add(message);
            warn.accept(message);
        };
        TbConfig next = loadConfig.load(sink);

        // The one line that makes a reload a reload. Without it the operator is told the
        // configuration was reloaded while every collaborator keeps reading the old values.
        if (services != null) {
            services.applyConfig(next);
        }
        if (recipe != null) {
            recipe.register();
        }
        return List.copyOf(warnings);
    }
}
