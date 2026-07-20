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

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.fell.BlockTypes;
import org.xpfarm.timberblast.testsupport.FakeBlocks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fires real {@link BlockDamageEvent}s at the listener. The player and block are JDK proxies
 * that throw on anything unfaked, so this also pins <em>which</em> accessors the handler
 * reads -- the class of defect a mutation to {@code getBlock()} or {@code getPlayer()} would
 * otherwise slip past.
 */
class TimberBlastListenerTest {

    private static final BlockTypes ONLY_OAK_LOG = new BlockTypes() {

        @Override
        public boolean isLog(Material material) {
            return material == Material.OAK_LOG;
        }

        @Override
        public boolean isLeaf(Material material) {
            return material == Material.OAK_LEAVES;
        }
    };

    /** Records every fell request as {@code "player -> block"}. */
    private final List<Block> felled = new ArrayList<>();

    private TimberBlastListener listener(Predicate<ItemStack> isAxe) {
        return new TimberBlastListener(isAxe, ONLY_OAK_LOG, (wielder, struck) -> felled.add(struck));
    }

    private static Player player(boolean permitted) {
        return FakeBlocks.stub(Player.class, "the wielder", Map.of("hasPermission", permitted));
    }

    /** The held item is always null here; identity is decided by the injected predicate. */
    private static BlockDamageEvent swingAt(Player player, Block block) {
        return new BlockDamageEvent(player, block, BlockFace.UP, null, false);
    }

    @Test
    void aTimberBlastSwungAtALogFellsThatLog() {
        Block log = FakeBlocks.at("w", 4, 65, -7, Material.OAK_LOG);

        listener(stack -> true).onBlockDamage(swingAt(player(true), log));

        assertEquals(1, felled.size());
        assertSame(log, felled.get(0), "the felled block must be the one the event carries");
    }

    @Test
    void anOrdinaryAxeDoesNothing() {
        listener(stack -> false)
                .onBlockDamage(swingAt(player(true), FakeBlocks.at("w", 0, 0, 0, Material.OAK_LOG)));

        assertTrue(felled.isEmpty());
    }

    @Test
    void aPlayerWithoutTheUsePermissionDoesNothing() {
        listener(stack -> true)
                .onBlockDamage(swingAt(player(false), FakeBlocks.at("w", 0, 0, 0, Material.OAK_LOG)));

        assertTrue(felled.isEmpty());
    }

    @Test
    void leavesAreLeftToVanillaJustLikeAnyOtherBlock() {
        listener(stack -> true)
                .onBlockDamage(swingAt(player(true), FakeBlocks.at("w", 0, 0, 0, Material.OAK_LEAVES)));

        assertTrue(felled.isEmpty(), "there is no leaf behaviour; the axe acts like a normal axe");
    }

    @Test
    void stoneIsLeftToVanilla() {
        listener(stack -> true)
                .onBlockDamage(swingAt(player(true), FakeBlocks.at("w", 0, 0, 0, Material.STONE)));

        assertTrue(felled.isEmpty());
    }

    @Test
    void theIdentityCheckIsAskedAboutTheItemInHand() {
        List<Object> asked = new ArrayList<>();
        Block log = FakeBlocks.at("w", 0, 0, 0, Material.OAK_LOG);

        listener(stack -> {
            asked.add("checked");
            return true;
        }).onBlockDamage(swingAt(player(true), log));

        assertEquals(List.of("checked"), asked, "identity is checked exactly once, first");
    }

    @Test
    void theHandlerRunsAtNormalPriorityAndSkipsCancelledEvents() throws Exception {
        Method handler = TimberBlastListener.class.getMethod("onBlockDamage", BlockDamageEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.NORMAL, annotation.priority());
        assertTrue(annotation.ignoreCancelled(),
                "another plugin's cancellation of the swing must stop the fell");
    }
}
