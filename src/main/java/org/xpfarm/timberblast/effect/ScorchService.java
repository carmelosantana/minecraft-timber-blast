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
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.xpfarm.timberblast.config.TbConfig;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The leaf-hit path: swinging a Timber Blast axe at leaves sets them alight instead of
 * felling anything.
 *
 * <h2>The fire must not spread -- that is the feature</h2>
 *
 * <p>A player who mis-aims near spawn should singe a few leaves, not burn down a biome.
 * Containment works by tracking every fire this plugin lights and cancelling
 * {@link BlockSpreadEvent} and {@link BlockIgniteEvent} whose <b>source</b> block is one
 * of ours. Checking the source rather than the target is load bearing in both
 * directions: checking the target would leave scorch fire free to spread outward while
 * blocking unrelated legitimate fire from ever reaching a position we once lit.
 *
 * <p>Nothing here blocks fire the plugin did not light. A campfire, a lava flow, or
 * another player's flint and steel spreads exactly as vanilla intends, because its
 * source block is not in {@link ScorchTracker}.
 *
 * <h2>Config gates</h2>
 *
 * <p>{@code scorch.enabled = false} makes {@link #scorch} a no-op -- no fire, no sound,
 * no tracking. {@code scorch.spread = true} means the operator asked for vanilla fire:
 * the fire is still lit, but it is never registered, so nothing cancels its spread.
 * Both gates are read live through the config supplier so {@code /timberblast reload}
 * takes effect without rebuilding this object.
 *
 * <h2>Testability</h2>
 *
 * <p>The set bookkeeping lives in the Bukkit-free {@link ScorchTracker}, and the two
 * decisions -- {@link #shouldScorch} and {@link #shouldContainFire} -- are static
 * functions over primitives, so {@code ScorchServiceTest} pins them with no running
 * server. What is left in this class is Bukkit glue thin enough to read.
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
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.tracker = new ScorchTracker(ScorchService::isFireAt);
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
        TbConfig config = configSupplier.get();
        boolean enabled = config.scorch().enabled();

        Block above = leafBlock.getRelative(BlockFace.UP);
        if (!shouldScorch(enabled, above.getType().isAir())) {
            return;
        }

        above.setType(Material.FIRE);
        above.getWorld().playSound(above.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE,
                SoundCategory.BLOCKS, SOUND_VOLUME, SOUND_PITCH);

        if (shouldContainFire(enabled, config.scorch().spread())) {
            tracker.track(posOf(above));
        }
    }

    /**
     * Whether the containment listeners are worth registering under the current config.
     * Consulted by the plugin's enable path; with {@code spread = true} nothing is ever
     * tracked, so registering the listeners would only add per-fire-tick work that can
     * never match.
     */
    public boolean shouldRegisterContainment() {
        TbConfig config = configSupplier.get();
        return shouldContainFire(config.scorch().enabled(), config.scorch().spread());
    }

    /** Forgets every tracked fire. Called on plugin disable. */
    public void clear() {
        tracker.clear();
    }

    /** How many scorch fires are currently tracked. Exposed for diagnostics. */
    public int trackedFireCount() {
        return tracker.size();
    }

    /**
     * Fire spreading to a neighbouring block. {@code getSource()} is the burning block
     * doing the spreading; {@code getBlock()} is where the new fire would appear. We
     * match on the source -- see the class-level note on why that direction matters.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (tracker.isScorchFire(posOf(event.getSource()))) {
            event.setCancelled(true);
        }
    }

    /**
     * A block being set alight. The igniting block is null for causes with no block
     * behind them (a player's flint and steel, a lightning strike); those are never ours
     * and are left alone.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block source = event.getIgnitingBlock();
        if (source != null && tracker.isScorchFire(posOf(source))) {
            event.setCancelled(true);
        }
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
