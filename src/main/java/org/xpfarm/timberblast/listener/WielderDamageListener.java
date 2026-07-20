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

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.xpfarm.timberblast.fell.BlastGuard;

import java.util.Objects;

/**
 * Spares the wielder their own blast without spoiling the launch.
 *
 * <p>The damage is zeroed, never cancelled -- Settled API Fact 1. Paper's
 * {@code ServerExplosion#hurtEntities} records the cancellation and {@code continue}s
 * <em>before</em> it computes the knockback vector, so cancelling would quietly take the
 * flight away along with the damage, which is the whole feel of the item.
 *
 * <p>{@link BlastGuard} keeps this narrow: only a player inside their own Timber Blast, and
 * only for the tick it takes to detonate. Creepers, TNT and everyone else's axe are
 * untouched.
 */
public final class WielderDamageListener implements Listener {

    private final BlastGuard guard;

    public WielderDamageListener(BlastGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    /**
     * Runs at {@link EventPriority#HIGHEST} so this plugin has the last word on the damage
     * value; a suppression that another plugin can raise again afterwards is no suppression.
     * Cancelled events are still visited deliberately -- there is nothing to do for them,
     * but nothing to gain from skipping them either.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isExplosion(event.getCause())) {
            return;
        }
        if (!guard.isBlasting(player.getUniqueId())) {
            return;
        }
        event.setDamage(0);
    }

    private static boolean isExplosion(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
    }
}
