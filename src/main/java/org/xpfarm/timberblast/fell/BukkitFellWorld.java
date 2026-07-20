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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.timberblast.tree.BlockPos;

import java.util.Objects;

/**
 * The live-server {@link FellWorld}: real blocks, real events, real explosions.
 *
 * <p>Nothing here is unit tested -- every method is a one-line delegation to a Bukkit call
 * that only exists on a running server, and the logic that decides <em>which</em> of these
 * to call lives in {@link FellExecutor}, which is. These calls are verified at gate 7a.
 */
public final class BukkitFellWorld implements FellWorld {

    private final World world;
    private final Player breaker;

    /**
     * @param world   the world being felled in
     * @param breaker the wielder, named as the breaker on every {@code BlockBreakEvent} so
     *                protection plugins can judge the right player
     */
    public BukkitFellWorld(World world, Player breaker) {
        this.world = Objects.requireNonNull(world, "world");
        this.breaker = Objects.requireNonNull(breaker, "breaker");
    }

    @Override
    public boolean requestBreak(BlockPos pos) {
        BlockBreakEvent event = new BlockBreakEvent(blockAt(pos), breaker);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override
    public void breakNaturally(BlockPos pos) {
        blockAt(pos).breakNaturally();
    }

    @Override
    public void breakDropping(BlockPos pos, Material drop) {
        blockAt(pos).setType(Material.AIR, false);
        world.dropItemNaturally(centreOf(pos), new ItemStack(drop, 1));
    }

    @Override
    public void explode(BlockPos origin, double power, boolean blockDamage) {
        // The four-argument overload takes no source entity. Passing the wielder here would
        // deny them both the damage and the knockback -- see Settled API Fact 2.
        world.createExplosion(centreOf(origin), (float) power, false, blockDamage);
    }

    private Block blockAt(BlockPos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    /**
     * The middle of {@code pos}, which is where drops and the blast are aimed.
     *
     * <p>Package-private rather than private so the offsets can be asserted directly: a
     * dropped or negated {@code +0.5} misplaces every drop and the explosion itself, and
     * {@code breakDropping} cannot be driven end to end offline because {@code new ItemStack}
     * reaches Paper's item registry.
     */
    Location centreOf(BlockPos pos) {
        return new Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }
}
