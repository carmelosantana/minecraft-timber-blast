/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.fell;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.tree.BlockPos;
import org.xpfarm.timberblast.tree.BlockQuery;
import org.xpfarm.timberblast.tree.ScanResult;
import org.xpfarm.timberblast.tree.TreeScanner;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Runs a fell: check fuel, scan, break, consume, detonate, kick, wear the axe.
 *
 * <p>The class is split into a Bukkit-facing {@link #fell(Player, Block)}, which does
 * nothing but build the two adapters, and {@link #run}, which holds every decision and
 * takes only ports. That split is deliberate -- {@code run} is what the tests drive, so the
 * origin-vs-rest drop, the protection-plugin veto, and the fuel accounting are pinned
 * without a server.
 */
public final class FellExecutor implements FellAction {

    /** Durability cost of one fell, per the brief. */
    private static final int TOOL_DAMAGE = 1;

    private static final Logger LOG = Logger.getLogger(FellExecutor.class.getName());

    private final Supplier<TbConfig> config;
    private final TreeScanner scanner;
    private final BlockTypes types;
    private final BlastGuard guard;

    /**
     * @param config a supplier rather than a snapshot so {@code /timberblast reload} takes
     *               effect without re-registering listeners
     * @param types  log/leaf classification; production passes {@link BlockTypes#SERVER_TAGS}
     */
    public FellExecutor(Supplier<TbConfig> config, TreeScanner scanner, BlockTypes types) {
        this.config = Objects.requireNonNull(config, "config");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.types = Objects.requireNonNull(types, "types");
        this.guard = new BlastGuard();
    }

    /**
     * The guard this executor arms around its own explosions.
     *
     * <p>The executor owns it rather than receiving it, and {@code WielderDamageListener} is
     * built <em>from</em> the executor, so there is no way to wire the two halves to
     * different guards. That mistake would leave the wielder taking full damage from their
     * own axe, which is severe enough that it is worth making unrepresentable rather than
     * warning about in a comment.
     *
     * @return the one guard both halves of the damage suppression share
     */
    public BlastGuard guard() {
        return guard;
    }

    @Override
    public void fell(Player wielder, Block struck) {
        BlockPos origin = new BlockPos(struck.getX(), struck.getY(), struck.getZ());
        run(origin,
                new WorldBlockQuery(struck.getWorld(), types),
                new BukkitFellWorld(struck.getWorld(), wielder),
                new BukkitWielder(wielder),
                wielder.getUniqueId());
    }

    /**
     * The fell itself, in terms of ports only.
     *
     * <p>The return value is a test-facing signal, not a production one: {@link FellAction}
     * is {@code void} because a swing that bails out has nothing left to do -- the axe simply
     * falls through to vanilla behaviour. It is kept because "did this fell happen at all"
     * is the single most useful assertion in {@code FellExecutorTest}, and every bail-out
     * path is asserted through it.
     *
     * @return {@code true} when the fell went ahead, {@code false} when it bailed out and
     *         the swing must fall through to vanilla axe behaviour
     */
    boolean run(BlockPos origin, BlockQuery query, FellWorld world, Wielder wielder, UUID wielderId) {
        TbConfig cfg = config.get();

        // Defence in depth only: ConfigValidator substitutes a default for an unresolvable
        // fuel material, so on a validated config this is unreachable. It is kept because a
        // future caller could hand run() a hand-built TbConfig, and because silently
        // treating "no such material" as "the player has infinite fuel" would be worse.
        Material fuel = Material.matchMaterial(cfg.fuel().material());
        if (fuel == null) {
            LOG.warning("fuel.material '" + cfg.fuel().material()
                    + "' is not a known material; no fell will run until it is corrected");
            return false;
        }
        if (!wielder.hasFuel(fuel, cfg.fuel().amount())) {
            return false;
        }

        ScanResult scan = scanner.scan(origin, query, cfg.fell().maxBlocks(),
                cfg.fell().maxRadius(), cfg.fell().maxHeight());
        // Step 4a, and the only "this was not a fell" exit after the scan. A swing at a
        // non-log scans to an empty result and a fully-vetoed fell breaks nothing; both must
        // cost the player nothing -- no fuel, no blast, no shove, no durability. Breaking
        // zero blocks and still charging for it is the worst outcome of a fell inside
        // someone else's claim, so the two cases deliberately share one guard rather than
        // having a separate emptiness check that no test could tell apart from this one.
        if (breakLogs(scan.logs(), world, cfg) == 0) {
            return false;
        }

        if (cfg.fell().dropLeaves()) {
            for (BlockPos leaf : scan.leaves()) {
                // Step 5. Leaves go through the same cancellable event as the logs. Removing
                // them unprotected would let a fully-vetoed fell still strip the canopy.
                if (world.requestBreak(leaf)) {
                    world.breakNaturally(leaf);
                }
            }
        }

        wielder.consumeFuel(fuel, cfg.fuel().amount());

        guard.begin(wielderId);
        try {
            world.explode(origin, cfg.explosion().power(), cfg.explosion().blockDamage());
        } finally {
            guard.end(wielderId);
        }

        // This replaces whatever velocity the explosion just imparted, which is the point:
        // explosion.knockback-multiplier has to be a predictable dial, not a nudge on top of
        // a vanilla falloff curve. Settled API Fact 1 -- never cancelling the damage event --
        // is therefore retained as defensive ordering insurance rather than as the mechanism
        // actually delivering the launch: if this call is ever reordered or removed, a
        // cancelled damage event would silently ground the wielder.
        wielder.setVelocity(Knockback.away(centreOf(origin), wielder.position(),
                cfg.explosion().knockbackMultiplier()));
        wielder.damageTool(TOOL_DAMAGE);
        return true;
    }

    /**
     * Breaks every log a protection plugin allows. Index 0 is the block the player actually
     * struck -- {@code TreeScanner} guarantees the origin leads the list -- and it is the
     * one that chars into coal.
     *
     * @return how many logs actually came down; zero means every one was vetoed
     */
    private int breakLogs(List<BlockPos> logs, FellWorld world, TbConfig cfg) {
        Material coal = coalDrop(cfg);
        int broken = 0;
        for (int i = 0; i < logs.size(); i++) {
            BlockPos log = logs.get(i);
            if (!world.requestBreak(log)) {
                continue;
            }
            if (i == 0 && coal != null) {
                world.breakDropping(log, coal);
            } else {
                world.breakNaturally(log);
            }
            broken++;
        }
        return broken;
    }

    /**
     * The material the struck log chars into, or {@code null} for its vanilla drop.
     *
     * <p>Unlike the fuel material, an unresolvable coal material does not abort the fell --
     * the tree still comes down, the origin just drops its log. That asymmetry is deliberate
     * (a cosmetic reward is not worth denying the feature over) but it is not silent.
     */
    private static Material coalDrop(TbConfig cfg) {
        if (!cfg.coal().enabled()) {
            return null;
        }
        Material coal = Material.matchMaterial(cfg.coal().material());
        if (coal == null) {
            LOG.warning("coal.material '" + cfg.coal().material()
                    + "' is not a known material; the struck log will drop naturally instead");
        }
        return coal;
    }

    private static Vector centreOf(BlockPos pos) {
        return new Vector(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }
}
