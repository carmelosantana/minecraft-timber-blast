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

import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.xpfarm.timberblast.command.TimberBlastCommand;
import org.xpfarm.timberblast.config.BukkitConfigSource;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.item.TimberBlastItem;
import org.xpfarm.timberblast.listener.CraftPermissionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Plugin entry point: wires the config layer, the item layer, the fell services, the
 * listeners, the crafting recipe, and the command together.
 *
 * <h2>Startup never throws</h2>
 *
 * <p>A binding contract, and the reason every wiring step runs inside {@link #step}: an
 * operator with a typo in {@code config.yml} -- or a Paper build where one API call has
 * moved -- gets a warning and a degraded feature, never a dead plugin and never a dead
 * server. {@link TbConfig#load} already guarantees this for values; {@link #step} extends
 * the same guarantee to the wiring itself.
 *
 * <h2>Reload rebuilds nothing</h2>
 *
 * <p>{@link #reload()} re-reads {@code config.yml} and calls
 * {@link TimberBlastServices#applyConfig}. That is the whole of it: it constructs no
 * executor, no listener, and registers nothing. Registering a fresh listener per reload
 * while the previous one is still registered is the classic form of this bug -- three
 * reloads later a single swing fells the tree four times, each fell burning fuel and
 * spawning its own explosion, and nothing says so until a player notices. See
 * {@link TimberBlastServices} for how the design removes the possibility rather than
 * relying on remembering to unregister.
 *
 * <h2>No onDisable</h2>
 *
 * <p>Deliberately absent. There is nothing to persist -- the axe's identity lives in its
 * own item PDC, not in plugin state -- and the only in-memory state, the executor's
 * {@code BlastGuard}, dies with the plugin instance. Recipes are left registered on
 * purpose: removing them on disable would strip the recipe from every player's book
 * during a {@code /reload}, and re-registering already replaces the old entry by key.
 */
public final class TimberBlastPlugin extends JavaPlugin {

    private TimberBlastItem item;
    private TimberBlastServices services;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Loaded outside step(): every later step depends on it, so a failure here must
        // still yield a usable value rather than a null. A null section makes
        // BukkitConfigSource answer every key with its default, which is the fallback.
        TbConfig initial;
        try {
            initial = loadConfig(this::warn);
        } catch (Throwable t) {
            warn("TimberBlast: could not read config.yml (" + t.getClass().getName() + ": "
                    + t.getMessage() + "). Running on the built-in defaults.");
            initial = TbConfig.load(new BukkitConfigSource(null, this::warn),
                    BukkitConfigSource::isValidMaterial, this::warn);
        }
        TbConfig startingConfig = initial;

        step("axe item", () -> item = new TimberBlastItem(this));

        step("fell services", () -> {
            List<Listener> extra = new ArrayList<>();
            if (item != null) {
                extra.add(new CraftPermissionListener(item.recipeKey()));
            }
            // The identity check goes through a lambda rather than a method reference so
            // that a failed item step degrades to "no item is a Timber Blast axe" instead
            // of a NullPointerException on every block-damage event.
            services = new TimberBlastServices(startingConfig,
                    stack -> item != null && item.isTimberBlast(stack),
                    BlockTypes.SERVER_TAGS, extra);
            int count = services.registerOnce(
                    listener -> getServer().getPluginManager().registerEvents(listener, this));
            getLogger().info("TimberBlast: registered " + count + " listeners.");
        });

        // resendRecipes=false: no player is connected at enable time on a normal start, so
        // there is no client recipe book to refresh. The reload path passes true; see
        // reload().
        step("crafting recipe", () -> {
            if (item != null) {
                item.registerRecipe(false);
            }
        });

        step("command", () -> {
            PluginCommand command = getCommand("timberblast");
            if (command == null) {
                warn("TimberBlast: command 'timberblast' is missing from plugin.yml; "
                        + "the command will not be available.");
                return;
            }
            TimberBlastCommand executor = new TimberBlastCommand(
                    () -> item == null ? null : item.create(), this::reload);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        });

        getLogger().info("TimberBlast enabled.");
    }

    /**
     * Re-reads {@code config.yml} and applies it live.
     *
     * <p>Invoked by {@code /timberblast reload}. Constructs no services and registers no
     * listeners -- see the class note. The configuration is swapped only once it has fully
     * parsed, so a failure part-way leaves the previously working settings in place.
     *
     * @return the validation warnings this reload produced, in order; empty when the file
     *         was clean. The command reports these to the sender, so an operator fixing a
     *         typo sees the result in chat instead of having to open the server log.
     */
    public List<String> reload() {
        reloadConfig();
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = message -> {
            warnings.add(message);
            warn(message);
        };
        TbConfig next = loadConfig(sink);
        if (services != null) {
            services.applyConfig(next);
        }
        // resendRecipes=true, unlike the enable path. Players are connected during a
        // reload, and the single-argument add/remove forms leave every already-connected
        // client holding the stale recipe in its book until it relogs. A reload that
        // changed nothing about the recipe pays one cheap resend; a reload that fixed a
        // broken recipe is visible immediately, which is the point of running it.
        if (item != null) {
            item.registerRecipe(true);
        }
        return List.copyOf(warnings);
    }

    private TbConfig loadConfig(Consumer<String> warn) {
        return TbConfig.load(new BukkitConfigSource(getConfig(), warn),
                BukkitConfigSource::isValidMaterial, warn);
    }

    private void warn(String message) {
        getLogger().warning(message);
    }

    /**
     * Runs one wiring step, converting any failure into a warning.
     *
     * <p>Catches {@link Throwable} on purpose. A {@code NoSuchMethodError} or
     * {@code NoClassDefFoundError} from a Paper API change is an {@link Error}, not an
     * {@link Exception}, and is exactly the kind of failure most likely to hit this path
     * on a server upgrade. Losing one subsystem with a clear log line beats failing to
     * enable at all.
     */
    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            warn("TimberBlast: failed to initialise " + name + " (" + t.getClass().getName()
                    + ": " + t.getMessage() + "). That feature is unavailable; the plugin is "
                    + "still enabled.");
        }
    }
}
