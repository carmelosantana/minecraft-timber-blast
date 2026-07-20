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

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.config.CoalSettings;
import org.xpfarm.timberblast.config.ExplosionSettings;
import org.xpfarm.timberblast.config.FellSettings;
import org.xpfarm.timberblast.config.FuelSettings;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.fell.BlastGuard;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.fell.FellExecutor;
import org.xpfarm.timberblast.testsupport.FakeBlocks;
import org.xpfarm.timberblast.tree.TreeScanner;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fires real {@link EntityDamageEvent}s at the listener.
 *
 * <p>The two rules that matter are both asserted on every suppression: the damage is zeroed,
 * and the event is <em>not</em> cancelled. Settled API Fact 1 -- Paper skips the knockback
 * vector for entities whose damage event was cancelled, so cancelling here would silently
 * ground the wielder.
 */
class WielderDamageListenerTest {

    private static final UUID WIELDER = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final UUID BYSTANDER = UUID.fromString("00000000-0000-0000-0000-00000000cafe");

    private static Player player(UUID id) {
        return FakeBlocks.stub(Player.class, "player " + id, Map.of("getUniqueId", id));
    }

    private static EntityDamageEvent damage(org.bukkit.entity.Entity victim, DamageCause cause) {
        DamageSource source = FakeBlocks.stub(DamageSource.class, "an explosion", Map.of());
        return new EntityDamageEvent(victim, cause, source, 12.0);
    }

    private static WielderDamageListener listenerGuarding(UUID... blasting) {
        BlastGuard guard = new BlastGuard();
        for (UUID id : blasting) {
            guard.begin(id);
        }
        return new WielderDamageListener(guard);
    }

    @Test
    void theWieldersOwnBlastDoesNoDamageButIsNeverCancelled() {
        EntityDamageEvent event = damage(player(WIELDER), DamageCause.BLOCK_EXPLOSION);

        listenerGuarding(WIELDER).onEntityDamage(event);

        assertEquals(0.0, event.getDamage(), 1.0E-9);
        assertFalse(event.isCancelled(),
                "cancelling would take the knockback away with the damage");
    }

    @Test
    void entityExplosionsAreSuppressedToo() {
        EntityDamageEvent event = damage(player(WIELDER), DamageCause.ENTITY_EXPLOSION);

        listenerGuarding(WIELDER).onEntityDamage(event);

        assertEquals(0.0, event.getDamage(), 1.0E-9);
        assertFalse(event.isCancelled());
    }

    @Test
    void anotherPlayersExplosionStillHurts() {
        EntityDamageEvent event = damage(player(BYSTANDER), DamageCause.BLOCK_EXPLOSION);

        listenerGuarding(WIELDER).onEntityDamage(event);

        assertEquals(12.0, event.getDamage(), 1.0E-9, "the suppression must be per-player");
    }

    @Test
    void aPlayerWhoIsNotBlastingStillTakesCreeperDamage() {
        EntityDamageEvent event = damage(player(WIELDER), DamageCause.BLOCK_EXPLOSION);

        listenerGuarding().onEntityDamage(event);

        assertEquals(12.0, event.getDamage(), 1.0E-9,
                "outside the blast window the plugin must be invisible");
    }

    @Test
    void theWielderStillTakesNonExplosionDamageMidBlast() {
        EntityDamageEvent event = damage(player(WIELDER), DamageCause.FALL);

        listenerGuarding(WIELDER).onEntityDamage(event);

        assertEquals(12.0, event.getDamage(), 1.0E-9);
    }

    @Test
    void nonPlayersAreIgnored() {
        Creeper creeper = FakeBlocks.stub(Creeper.class, "a creeper", Map.of());
        EntityDamageEvent event = damage(creeper, DamageCause.BLOCK_EXPLOSION);

        listenerGuarding(WIELDER).onEntityDamage(event);

        assertEquals(12.0, event.getDamage(), 1.0E-9);
    }

    @Test
    void theHandlerRunsLastSoNothingCanReinstateTheDamage() throws Exception {
        Method handler = WielderDamageListener.class.getMethod("onEntityDamage", EntityDamageEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority(),
                "a suppression another plugin can raise again afterwards is no suppression");
        assertFalse(annotation.ignoreCancelled(),
                "cancelled events are visited deliberately -- see Settled API Fact 1");
    }

    @Test
    void theListenerBuiltFromAnExecutorSharesThatExecutorsGuard() {
        // The whole point of the factory: there is no way to hand the two halves of the
        // suppression different guards, so the wielder cannot be killed by their own axe
        // through a wiring mistake in Task 6.
        FellExecutor executor = new FellExecutor(
                () -> new TbConfig(new FellSettings(256, 8, 32, true), new FuelSettings("GUNPOWDER", 2),
                        new ExplosionSettings(2.0, false, 1.5), new CoalSettings(true, "COAL")),
                new TreeScanner(), BlockTypes.SERVER_TAGS);
        WielderDamageListener listener = WielderDamageListener.protecting(executor);
        EntityDamageEvent event = damage(player(WIELDER), DamageCause.BLOCK_EXPLOSION);

        executor.guard().begin(WIELDER);
        listener.onEntityDamage(event);

        assertEquals(0.0, event.getDamage(), 1.0E-9,
                "marking the executor's guard must be what this listener sees");
    }

    @Test
    void theGuardWindowClosesWhenTheBlastEnds() {
        BlastGuard guard = new BlastGuard();
        guard.begin(WIELDER);
        guard.end(WIELDER);
        EntityDamageEvent event = damage(player(WIELDER), DamageCause.BLOCK_EXPLOSION);

        new WielderDamageListener(guard).onEntityDamage(event);

        assertEquals(12.0, event.getDamage(), 1.0E-9);
    }
}
