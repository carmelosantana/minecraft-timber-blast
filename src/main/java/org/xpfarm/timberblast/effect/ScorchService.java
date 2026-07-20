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

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.xpfarm.timberblast.config.TbConfig;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The leaf-hit path: swinging a Timber Blast axe at leaves sets them alight instead of
 * felling anything.
 *
 * <h2>The fire must not spread -- that is the feature</h2>
 *
 * <p>A player who mis-aims near spawn should singe a few leaves, not burn down a biome.
 * Containment works by tracking every fire this plugin lights and cancelling every event
 * whose <b>source</b> block is one of ours. Checking the source rather than the target is
 * load bearing in both directions: checking the target would leave scorch fire free to
 * spread outward while blocking unrelated legitimate fire from ever reaching a position
 * we once lit.
 *
 * <h2>All three escape routes, not one</h2>
 *
 * <p>Vanilla fire reaches a new block by three different events, and cancelling only some
 * of them contains nothing:
 *
 * <ul>
 *   <li>{@link BlockSpreadEvent} -- fire propagating into air.</li>
 *   <li>{@link BlockIgniteEvent} -- a block being set alight, with the igniting block
 *       named as the cause.</li>
 *   <li>{@link BlockBurnEvent} -- vanilla {@code FireBlock} consuming an <em>adjacent
 *       flammable</em> block and putting fire where it stood. This one is not optional:
 *       scorch fire is placed directly above a leaf block, so it is <em>always</em>
 *       adjacent to something flammable, and this is the path it will take first. A fire
 *       created this way is not one we lit, so it is not tracked, so the other two
 *       handlers would never match it -- containment would hold for exactly one block and
 *       then leak with full vanilla behavior.</li>
 * </ul>
 *
 * <p>Because a cancelled burn means the flammable block is never consumed and the
 * replacement fire is never created, containment does not need to propagate outward:
 * there is no untracked child fire to track. Every route from one of our fires to a new
 * fire is refused at the source, so the depth is one by construction rather than by
 * accident. The cost is that scorch fire does not actually consume the leaves it sits on
 * -- it burns out and leaves them standing. For a cosmetic effect whose entire purpose is
 * "do not destroy the terrain", that is the intended trade.
 *
 * <p>Nothing here blocks fire the plugin did not light. A campfire, a lava flow, or
 * another player's flint and steel spreads exactly as vanilla intends, because its
 * source block is not in {@link ScorchTracker}.
 *
 * <h2>Config gates</h2>
 *
 * <p>{@code scorch.enabled = false} makes {@link #scorch} a no-op -- no fire, no sound,
 * no tracking. {@code scorch.spread = true} means the operator asked for vanilla fire:
 * the fire is still lit, but it is never registered, and the handlers additionally decline
 * to cancel anything.
 *
 * <p>Both gates are read live, on every call, through the config supplier. That is a
 * deliberate rejection of the cheaper alternative: deciding <em>once at enable</em>
 * whether to register the listeners at all. A one-time registration decision fed by a
 * reloadable value is a trap -- a {@code /timberblast reload} flipping {@code spread}
 * from true to false would leave the listeners permanently unregistered, {@code scorch()}
 * would go on tracking fires, and containment would be silently off with nothing to
 * observe. The listeners are therefore always registered and always gate on the current
 * value at handling time.
 *
 * <h2>Testability</h2>
 *
 * <p>The set bookkeeping lives in the Bukkit-free {@link ScorchTracker}; the config gates
 * {@link #shouldScorch} and {@link #shouldContainFire} are static functions over
 * primitives; and the two decisions that combine them with tracker state --
 * {@link #scorchInto} and {@link #shouldCancel} -- take {@link FirePos} and a callback
 * rather than a {@code Block}. {@code ScorchServiceTest} therefore pins what the service
 * decides <em>and</em> what it does about it, with no running server.
 *
 * <p>What is left is three one-line event adapters, each of which pulls the source block
 * off its event and applies the answer. {@code ScorchListenerTest} drives those adapters
 * with real event objects; see the task report for the residue that only gate 7a can
 * cover.
 */
public final class ScorchService implements Listener {

    private static final float SOUND_VOLUME = 1.0F;
    private static final float SOUND_PITCH = 1.0F;

    private final Supplier<TbConfig> configSupplier;
    private final ScorchTracker tracker;

    /**
     * @param configSupplier live config accessor, so a reload is picked up without
     *                       rebuilding this object
     */
    public ScorchService(Supplier<TbConfig> configSupplier) {
        this(configSupplier, ScorchService::isFireAt);
    }

    /**
     * Test seam: the same service with a hand-built view of which positions still hold
     * fire, so the decision methods can be exercised without a server.
     *
     * @param stillBurning see {@link ScorchTracker#ScorchTracker(Predicate)}
     */
    ScorchService(Supplier<TbConfig> configSupplier, Predicate<FirePos> stillBurning) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.tracker = new ScorchTracker(stillBurning);
    }

    // =================================================================================
    // Pure decision logic -- see ScorchServiceTest
    // =================================================================================

    /**
     * Whether a leaf strike should light a fire. The block above the struck leaf must be
     * air: replacing anything else would destroy a block the player never asked to
     * break, which a purely cosmetic effect has no business doing.
     *
     * @param enabled    {@code scorch.enabled}
     * @param aboveIsAir whether the block above the struck leaf is air
     */
    public static boolean shouldScorch(boolean enabled, boolean aboveIsAir) {
        return enabled && aboveIsAir;
    }

    /**
     * Whether a newly lit scorch fire should be registered for containment.
     *
     * <p>{@code spread = true} is an explicit request for vanilla fire behavior, so the
     * fire is left untracked and the containment listeners never match it. With scorch
     * disabled there is no fire to contain in the first place.
     *
     * @param enabled {@code scorch.enabled}
     * @param spread  {@code scorch.spread}
     */
    public static boolean shouldContainFire(boolean enabled, boolean spread) {
        return enabled && !spread;
    }

    // =================================================================================
    // Bukkit glue
    // =================================================================================

    /**
     * Sets the block above {@code leafBlock} alight, if the current config allows it and
     * that block is air, and registers the fire for containment.
     *
     * @param leafBlock the leaf block the player struck
     */
    public void scorch(Block leafBlock) {
        Objects.requireNonNull(leafBlock, "leafBlock");
        Block above = leafBlock.getRelative(BlockFace.UP);
        scorchInto(posOf(above), above.getType().isAir(), () -> {
            above.setType(Material.FIRE);
            above.getWorld().playSound(above.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE,
                    SoundCategory.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
        });
    }

    /**
     * The decision half of {@link #scorch}, over a position rather than a {@code Block}
     * so it is testable: consult the live config, and if the strike should light a fire,
     * run {@code ignite} and register the result for containment.
     *
     * @param pos        where the fire would go
     * @param aboveIsAir whether that position is currently air
     * @param ignite     lights the fire and plays the sound; run only if the gates pass
     */
    void scorchInto(FirePos pos, boolean aboveIsAir, Runnable ignite) {
        TbConfig config = configSupplier.get();
        boolean enabled = config.scorch().enabled();
        if (!shouldScorch(enabled, aboveIsAir)) {
            return;
        }

        ignite.run();

        if (shouldContainFire(enabled, config.scorch().spread())) {
            tracker.track(pos);
        }
    }

    /**
     * Whether an event whose source block sits at {@code sourcePos} must be cancelled:
     * the live config still asks for containment, and that block is a scorch fire of ours
     * that is still burning.
     *
     * <p>{@code null} means the event named no source block at all -- a player's flint
     * and steel, a lightning strike, an unknown burn cause. Those are never ours.
     *
     * @param sourcePos the source block's position, or {@code null} if the event has none
     */
    boolean shouldCancel(FirePos sourcePos) {
        if (sourcePos == null) {
            return false;
        }
        TbConfig config = configSupplier.get();
        if (!shouldContainFire(config.scorch().enabled(), config.scorch().spread())) {
            return false;
        }
        return tracker.isScorchFire(sourcePos);
    }

    /** Forgets every tracked fire. Called on plugin disable. */
    public void clear() {
        tracker.clear();
    }

    /**
     * How many scorch fires are currently tracked. No production caller: this exists so
     * tests can assert that a scorch was or was not registered, and so an operator
     * debugging a containment report has something to read.
     */
    public int trackedFireCount() {
        return tracker.size();
    }

    /**
     * Fire spreading to a neighbouring block. {@code getSource()} is the burning block
     * doing the spreading; {@code getBlock()} is where the new fire would appear. We
     * match on the source -- see the class-level note on why that direction matters.
     *
     * <p>{@link EventPriority#HIGHEST} on all three handlers: this is a veto, not a
     * preference. At {@code NORMAL} any plugin listening later could un-cancel it and the
     * containment guarantee would not be a guarantee.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (shouldCancel(posOfNullable(event.getSource()))) {
            event.setCancelled(true);
        }
    }

    /**
     * A block being set alight. The igniting block is null for causes with no block
     * behind them (a player's flint and steel, a lightning strike); those are never ours
     * and are left alone.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (shouldCancel(posOfNullable(event.getIgnitingBlock()))) {
            event.setCancelled(true);
        }
    }

    /**
     * Vanilla fire consuming an adjacent flammable block and replacing it with fire. This
     * is the route scorch fire takes first, because it is lit directly above a leaf; see
     * the class-level note. The igniting block is null when the server does not attribute
     * the burn to a specific fire, which means it is not attributable to us either.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (shouldCancel(posOfNullable(event.getIgnitingBlock()))) {
            event.setCancelled(true);
        }
    }

    private static FirePos posOfNullable(Block block) {
        return block == null ? null : posOf(block);
    }

    private static FirePos posOf(Block block) {
        return new FirePos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private static boolean isFireAt(FirePos pos) {
        World world = Bukkit.getWorld(pos.world());
        if (world == null) {
            return false;
        }
        // Reading through the world rather than a held Block reference: a held Block
        // would pin its chunk in memory, and world.getBlockAt would load the chunk on
        // demand. An unloaded chunk therefore counts as "not fire" and the entry is
        // evicted -- a deliberate trade. Fire in an unloaded chunk is not ticking and
        // cannot spread; if that chunk reloads with the fire still burning it reverts to
        // vanilla behavior, which is the same outcome as a server restart.
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return false;
        }
        return world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.FIRE;
    }
}
