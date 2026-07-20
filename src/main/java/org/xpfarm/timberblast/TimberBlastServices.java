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

import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.fell.FellExecutor;
import org.xpfarm.timberblast.listener.TimberBlastListener;
import org.xpfarm.timberblast.tree.TreeScanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The plugin's long-lived collaborators, built once and never rebuilt.
 *
 * <h2>Why this is not just fields on the plugin class</h2>
 *
 * <p>{@code /timberblast reload} is the reason. The obvious implementation of a reload --
 * re-read the config, rebuild the executor, build a fresh listener, register it -- leaks a
 * listener on every invocation, because the previously registered instances are still
 * registered. Three reloads later a single swing fells the tree four times, each fell
 * consuming fuel and creating its own explosion. The defect is silent until someone reloads
 * on a live server.
 *
 * <p>This class removes the possibility rather than relying on remembering to unregister.
 * The configuration lives in an {@link AtomicReference} handed to {@link FellExecutor} as a
 * {@code Supplier}, so {@link #applyConfig} is the entire reload path: the executor and both
 * listeners read the new values on their next event with no reconstruction and therefore no
 * re-registration. {@link #registerOnce} is idempotent as a second, independent guard -- if
 * a future edit does call it from a reload path, it registers nothing and says so.
 *
 * <h2>The listener pairing</h2>
 *
 * <p>The trigger listener and the wielder-damage listener must share one
 * {@link org.xpfarm.timberblast.fell.BlastGuard}; if they do not, the damage listener watches
 * a guard nobody arms and the wielder is killed by their own axe. They are therefore built
 * here from the single {@link TimberBlastListener#triggering} call site that guarantees the
 * pairing, and exposed only as one already-paired {@link #listeners()} list.
 */
public final class TimberBlastServices {

    private final AtomicReference<TbConfig> config;
    private final FellExecutor executor;
    private final List<Listener> listeners;

    private boolean registered;

    /**
     * @param initial         the configuration to start on; never {@code null}
     * @param isTimberBlast   the axe identity check, in production {@code TimberBlastItem::isTimberBlast}
     * @param types           log/leaf classification; production passes {@link BlockTypes#SERVER_TAGS}
     * @param extraListeners  listeners owned elsewhere that must share this class's
     *                        register-exactly-once discipline, in production the
     *                        craft-permission listener; {@code null} is treated as empty
     */
    public TimberBlastServices(TbConfig initial, Predicate<ItemStack> isTimberBlast,
                               BlockTypes types, List<Listener> extraListeners) {
        this.config = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        this.executor = new FellExecutor(this.config::get, new TreeScanner(), types);

        TimberBlastListener trigger = TimberBlastListener.triggering(isTimberBlast, types, executor);
        List<Listener> all = new ArrayList<>();
        all.add(trigger);
        // Must come from this trigger instance, not from a separate construction: that is
        // what binds the two to one BlastGuard. See the class note.
        all.add(trigger.damageListener());
        if (extraListeners != null) {
            all.addAll(extraListeners);
        }
        this.listeners = List.copyOf(all);
    }

    /** The configuration every collaborator is currently reading. */
    public TbConfig config() {
        return config.get();
    }

    /**
     * The whole of the reload path: swaps the configuration every collaborator reads.
     *
     * <p>Constructs nothing and registers nothing, deliberately. See the class note.
     */
    public void applyConfig(TbConfig next) {
        config.set(Objects.requireNonNull(next, "next"));
    }

    /** The single executor both listeners drive. */
    public FellExecutor executor() {
        return executor;
    }

    /** Every listener this plugin registers, already correctly paired. */
    public List<Listener> listeners() {
        return listeners;
    }

    /**
     * Hands each listener to {@code registrar}, at most once for the lifetime of this
     * instance.
     *
     * <p>The flag is set before the first hand-off rather than after: a registrar that
     * throws part-way through has already registered some listeners, and re-running the
     * whole list on a later call would double-register those. A partially wired plugin
     * that logs the failure beats a plugin that fells twice per swing.
     *
     * @param registrar receives each listener; in production
     *                  {@code l -> pluginManager.registerEvents(l, plugin)}
     * @return how many listeners were registered: the full count on the first call,
     *         {@code 0} on every call after it
     */
    public int registerOnce(Consumer<Listener> registrar) {
        Objects.requireNonNull(registrar, "registrar");
        if (registered) {
            return 0;
        }
        registered = true;
        listeners.forEach(registrar);
        return listeners.size();
    }

    /** Whether {@link #registerOnce} has already run. */
    public boolean isRegistered() {
        return registered;
    }
}
