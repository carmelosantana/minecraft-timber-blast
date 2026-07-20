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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The in-memory set of fires this plugin lit, and the bookkeeping that keeps it from
 * growing forever.
 *
 * <p>Contains no Bukkit types by design -- the world arrives through the
 * {@link Predicate} handed to the constructor, so add / contains / eviction / clear are
 * unit testable without a server, the same way {@code TreeScanner} is.
 *
 * <h2>Why this set exists</h2>
 *
 * <p>{@code ScorchService} cancels fire spread when the <em>source</em> block is a fire
 * in this set. Membership is therefore the difference between "singe a few leaves" and
 * "burn down the biome", and it must not accidentally cover fire the plugin did not
 * light -- a campfire, a lava flow, another player's flint and steel. That is why
 * entries are added only by {@code ScorchService#scorch} and are keyed by world as well
 * as coordinates.
 *
 * <h2>Bounding growth</h2>
 *
 * <p>Scorch fires burn out; nothing tells us when. Rather than schedule a task, entries
 * are evicted opportunistically whenever the set is touched:
 *
 * <ul>
 *   <li>{@link #track} sweeps every stale entry before adding, so the set's size is
 *       bounded by the number of scorch fires <em>currently burning</em> rather than by
 *       the number ever lit. The sweep is O(n) in that same bounded size, and runs only
 *       on an axe swing that hits leaves -- never on a hot path.</li>
 *   <li>{@link #isScorchFire} evicts the queried entry alone if its block is no longer
 *       fire. This one is on the fire-spread path, so it stays O(1): a full sweep here
 *       would run on every fire tick in the world.</li>
 * </ul>
 *
 * <p>A stale entry is never merely "wrong but harmless": the position could be
 * reoccupied by unrelated fire later, which this class would then wrongly claim as its
 * own. Eviction is a correctness rule, not just a memory one.
 */
public final class ScorchTracker {

    /**
     * Whether the block at a tracked position is still fire. Supplied by the caller so
     * this class stays Bukkit-free; {@code ScorchService} passes a lambda that reads the
     * live world, and tests pass a hand-built one.
     */
    private final Predicate<FirePos> stillBurning;

    private final Set<FirePos> tracked = new HashSet<>();

    /**
     * @param stillBurning tests whether the block at a position is still
     *                     {@code Material.FIRE}; must not be {@code null}
     */
    public ScorchTracker(Predicate<FirePos> stillBurning) {
        this.stillBurning = Objects.requireNonNull(stillBurning, "stillBurning");
    }

    /**
     * Records a fire this plugin just lit, first sweeping out every entry whose block is
     * no longer burning. See the class-level "Bounding growth" note for why the sweep
     * lives here and not on the query path.
     */
    public void track(FirePos pos) {
        Objects.requireNonNull(pos, "pos");
        evictBurntOut();
        tracked.add(pos);
    }

    /**
     * Whether {@code pos} is a fire this plugin lit and that is still burning.
     *
     * <p>A tracked position whose block is no longer fire is dropped here and reported
     * as not ours -- the fire we lit is gone, so anything at that position now belongs
     * to somebody else and must be left alone.
     */
    public boolean isScorchFire(FirePos pos) {
        if (pos == null || !tracked.contains(pos)) {
            return false;
        }
        if (!stillBurning.test(pos)) {
            tracked.remove(pos);
            return false;
        }
        return true;
    }

    /**
     * Forgets every tracked fire. Called on plugin disable: the set is in-memory only
     * and is never persisted, so a restart deliberately leaves any still-burning scorch
     * fire to behave like vanilla fire rather than staying contained by a plugin that is
     * no longer running.
     */
    public void clear() {
        tracked.clear();
    }

    /** How many fires are currently tracked. Exposed for tests and diagnostics. */
    public int size() {
        return tracked.size();
    }

    private void evictBurntOut() {
        Iterator<FirePos> it = tracked.iterator();
        while (it.hasNext()) {
            if (!stillBurning.test(it.next())) {
                it.remove();
            }
        }
    }
}
