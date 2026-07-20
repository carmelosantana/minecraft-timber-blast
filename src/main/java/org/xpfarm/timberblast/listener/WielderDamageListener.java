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
import org.xpfarm.timberblast.fell.FellExecutor;

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

    /**
     * Package-private on purpose. The suppression only works when this listener and the
     * executor share one {@link BlastGuard}; handing them separate instances would leave the
     * wielder taking the full force of their own axe, and it would look correct at every call
     * site.
     */
    WielderDamageListener(BlastGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    /**
     * The listener that spares {@code executor}'s wielders.
     *
     * <p>Package-private, one level deeper than it first appears it needs to be. Making this
     * public closed the "two guards" miswiring but only moved the failure: with two executors
     * in scope, wiring executor A into {@link TimberBlastListener} as the {@code FellAction}
     * and calling {@code protecting(executorB)} rebuilds the original bug -- the listener
     * watches a guard nobody ever arms, and the wielder takes the full blast. The only public
     * route is now {@link TimberBlastListener#damageListener()}, so the damage listener is
     * always derived from the very executor the trigger listener calls.
     *
     * @param executor the executor whose blasts this listener suppresses damage for
     * @return a listener sharing that executor's guard, by construction
     */
    static WielderDamageListener protecting(FellExecutor executor) {
        return new WielderDamageListener(
                Objects.requireNonNull(executor, "executor").guard());
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
