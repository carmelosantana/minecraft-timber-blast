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
import org.xpfarm.timberblast.fell.FellExecutor;

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

    /** The executor {@link #fell} belongs to, or {@code null} for a bare {@link FellAction}. */
    private final FellExecutor executor;

    /**
     * Package-private: it accepts any {@link FellAction}, which is exactly the freedom that
     * lets the damage suppression be miswired. Tests use it to drive the handler against a
     * recording lambda; production goes through {@link #triggering}.
     *
     * @param isTimberBlast the identity check, in production {@code TimberBlastItem::isTimberBlast}
     * @param types         log classification; production passes {@link BlockTypes#SERVER_TAGS}
     * @param fell          what to do once a swing qualifies
     */
    TimberBlastListener(Predicate<ItemStack> isTimberBlast, BlockTypes types, FellAction fell) {
        this(isTimberBlast, types, Objects.requireNonNull(fell, "fell"), null);
    }

    private TimberBlastListener(Predicate<ItemStack> isTimberBlast, BlockTypes types,
                                FellAction fell, FellExecutor executor) {
        this.isTimberBlast = Objects.requireNonNull(isTimberBlast, "isTimberBlast");
        this.types = Objects.requireNonNull(types, "types");
        this.fell = fell;
        this.executor = executor;
    }

    /**
     * The trigger listener for {@code executor}, and the only public way to build one.
     *
     * <p>Together with {@link #damageListener()} this is what keeps the two halves of the
     * damage suppression on one guard. A public constructor taking a bare {@link FellAction}
     * plus a public {@code WielderDamageListener.protecting(FellExecutor)} let a caller pass
     * executor A to one and executor B to the other using nothing but public API; the listener
     * then watched a guard that was never armed and the wielder died to their own blast.
     * Deriving the damage listener from this object instead means the pair cannot come from
     * different executors by ordinary wiring.
     *
     * @param isTimberBlast the identity check, in production {@code TimberBlastItem::isTimberBlast}
     * @param types         log classification; production passes {@link BlockTypes#SERVER_TAGS}
     * @param executor      the executor this listener triggers, and whose guard its companion
     *                      damage listener watches
     */
    public static TimberBlastListener triggering(Predicate<ItemStack> isTimberBlast,
                                                 BlockTypes types, FellExecutor executor) {
        Objects.requireNonNull(executor, "executor");
        return new TimberBlastListener(isTimberBlast, types, executor, executor);
    }

    /**
     * The damage listener that spares the wielders this listener fells for.
     *
     * <p>Register both, from this one call site, and the suppression is wired correctly by
     * construction:
     * {@snippet :
     * TimberBlastListener trigger = TimberBlastListener.triggering(isTb, types, executor);
     * pm.registerEvents(trigger, plugin);
     * pm.registerEvents(trigger.damageListener(), plugin);
     * }
     *
     * @return a listener watching the guard of the executor this listener triggers
     * @throws IllegalStateException if this listener was built around a bare {@link FellAction}
     *                               rather than a {@link FellExecutor}, which has no guard to share
     */
    public WielderDamageListener damageListener() {
        if (executor == null) {
            throw new IllegalStateException(
                    "this listener was built around a bare FellAction, which has no BlastGuard; "
                            + "use TimberBlastListener.triggering(..., FellExecutor) in production");
        }
        return WielderDamageListener.protecting(executor);
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
