/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.fell.FellAction;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * The trigger. Turns a swing of the axe against a log into a fell.
 *
 * <p>{@link BlockDamageEvent} rather than {@code PlayerInteractEvent} -- Settled API Fact 3:
 * it fires for Bedrock players through Geyser including the instant-break path, and it
 * carries the held item directly.
 *
 * <p>Anything that is not a log, leaves very much included, falls straight through, so the
 * axe behaves exactly like a normal diamond axe everywhere else.
 */
public final class TimberBlastListener implements Listener {

    /** Permission required to trigger a fell. */
    public static final String USE_PERMISSION = "timberblast.use";

    private final Predicate<ItemStack> isTimberBlast;
    private final BlockTypes types;
    private final FellAction fell;

    /**
     * @param isTimberBlast the identity check, in production {@code TimberBlastItem::isTimberBlast}
     * @param types         log classification; production passes {@link BlockTypes#SERVER_TAGS}
     * @param fell          what to do once a swing qualifies
     */
    public TimberBlastListener(Predicate<ItemStack> isTimberBlast, BlockTypes types, FellAction fell) {
        this.isTimberBlast = Objects.requireNonNull(isTimberBlast, "isTimberBlast");
        this.types = Objects.requireNonNull(types, "types");
        this.fell = Objects.requireNonNull(fell, "fell");
    }

    /**
     * Cheapest check first: the identity test rejects an ordinary tool without touching the
     * permission map or the block, and this runs on every block a player starts breaking.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!isTimberBlast.test(event.getItemInHand())) {
            return;
        }
        Player wielder = event.getPlayer();
        if (!wielder.hasPermission(USE_PERMISSION)) {
            return;
        }
        Block struck = event.getBlock();
        if (!types.isLog(struck.getType())) {
            return;
        }
        fell.fell(wielder, struck);
    }
}
