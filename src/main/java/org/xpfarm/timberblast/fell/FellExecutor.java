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

    private final Supplier<TbConfig> config;
    private final TreeScanner scanner;
    private final BlockTypes types;
    private final BlastGuard guard;

    /**
     * @param config a supplier rather than a snapshot so {@code /timberblast reload} takes
     *               effect without re-registering listeners
     * @param types  log/leaf classification; production passes {@link BlockTypes#SERVER_TAGS}
     * @param guard  shared with {@code WielderDamageListener}; the same instance must reach both
     */
    public FellExecutor(Supplier<TbConfig> config, TreeScanner scanner, BlockTypes types, BlastGuard guard) {
        this.config = Objects.requireNonNull(config, "config");
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.types = Objects.requireNonNull(types, "types");
        this.guard = Objects.requireNonNull(guard, "guard");
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
     * @return {@code true} when the fell went ahead, {@code false} when it bailed out and
     *         the swing must fall through to vanilla axe behaviour
     */
    boolean run(BlockPos origin, BlockQuery query, FellWorld world, Wielder wielder, UUID wielderId) {
        TbConfig cfg = config.get();

        Material fuel = Material.matchMaterial(cfg.fuel().material());
        if (fuel == null) {
            return false;
        }
        if (!wielder.hasFuel(fuel, cfg.fuel().amount())) {
            return false;
        }

        ScanResult scan = scanner.scan(origin, query, cfg.fell().maxBlocks(),
                cfg.fell().maxRadius(), cfg.fell().maxHeight());
        List<BlockPos> logs = scan.logs();
        if (logs.isEmpty()) {
            return false;
        }

        breakLogs(logs, world, cfg);
        if (cfg.fell().dropLeaves()) {
            for (BlockPos leaf : scan.leaves()) {
                world.breakNaturally(leaf);
            }
        }

        wielder.consumeFuel(fuel, cfg.fuel().amount());

        guard.begin(wielderId);
        try {
            world.explode(origin, cfg.explosion().power(), cfg.explosion().blockDamage());
        } finally {
            guard.end(wielderId);
        }

        wielder.setVelocity(Knockback.away(centreOf(origin), wielder.position(),
                cfg.explosion().knockbackMultiplier()));
        wielder.damageTool(TOOL_DAMAGE);
        return true;
    }

    /**
     * Breaks every log a protection plugin allows. Index 0 is the block the player actually
     * struck -- {@code TreeScanner} guarantees the origin leads the list -- and it is the
     * one that chars into coal.
     */
    private void breakLogs(List<BlockPos> logs, FellWorld world, TbConfig cfg) {
        Material coal = cfg.coal().enabled() ? Material.matchMaterial(cfg.coal().material()) : null;
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
        }
    }

    private static Vector centreOf(BlockPos pos) {
        return new Vector(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }
}
