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

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.config.CoalSettings;
import org.xpfarm.timberblast.config.ExplosionSettings;
import org.xpfarm.timberblast.config.FellSettings;
import org.xpfarm.timberblast.config.FuelSettings;
import org.xpfarm.timberblast.config.ScorchSettings;
import org.xpfarm.timberblast.config.TbConfig;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three event adapters, driven with real Bukkit event objects.
 *
 * <p>Bukkit's block events are plain data carriers -- their constructors store fields and
 * {@code setCancelled} flips a boolean -- so they can be built and handled with no server
 * running, given a source {@link Block}. {@link FakeBlocks} supplies that with a JDK
 * proxy, no mocking library involved.
 *
 * <p>What this pins that {@code ScorchServiceTest} cannot: that each handler reads the
 * <b>source</b> side of its event rather than the target, that a null source is tolerated
 * rather than thrown on, and that a positive decision is actually applied by cancelling.
 *
 * <p>What it still cannot pin: that the handlers are annotated {@code @EventHandler} at
 * {@code HIGHEST} and are reached by the server's event bus at all. That needs a live
 * server and is gate 7a residue.
 */
class ScorchListenerTest {

    private static final FirePos OURS = new FirePos("world", 0, 64, 0);
    private static final FirePos THEIRS = new FirePos("world", 40, 64, 40);

    private final Set<FirePos> burning = new HashSet<>();
    private ScorchSettings scorch = new ScorchSettings(true, false);

    private final ScorchService service =
            new ScorchService(this::config, burning::contains);

    private TbConfig config() {
        return new TbConfig(
                new FellSettings(64, 8, 16, true),
                new FuelSettings("GUNPOWDER", 1),
                new ExplosionSettings(2.0, false, 1.0),
                new CoalSettings(true, "COAL"),
                scorch);
    }

    /** Lights and registers a scorch fire at {@code pos}, as a leaf strike would. */
    private void lightScorchFire(FirePos pos) {
        burning.add(pos);
        service.scorchInto(pos, true, () -> {
        });
    }

    private BlockSpreadEvent spreadFrom(FirePos source, FirePos target) {
        return new BlockSpreadEvent(FakeBlocks.at(target), FakeBlocks.at(source), null);
    }

    // =================================================================================
    // BlockSpreadEvent
    // =================================================================================

    @Test
    void spreadFromOurFire_isCancelled() {
        lightScorchFire(OURS);

        BlockSpreadEvent event = spreadFrom(OURS, THEIRS);
        service.onBlockSpread(event);

        assertTrue(event.isCancelled(), "scorch fire must not spread -- this is the feature");
    }

    @Test
    void spreadFromVanillaFire_isLeftAlone() {
        lightScorchFire(OURS);

        BlockSpreadEvent event = spreadFrom(THEIRS, OURS);
        service.onBlockSpread(event);

        assertFalse(event.isCancelled(),
                "the handler must match on getSource(), not getBlock(): here the target is "
                        + "a position we once lit, and cancelling would silence vanilla fire");
    }

    @Test
    void spreadFromOurFire_isNotCancelledWhenSpreadIsConfiguredOn() {
        scorch = new ScorchSettings(true, true);
        lightScorchFire(OURS);

        BlockSpreadEvent event = spreadFrom(OURS, THEIRS);
        service.onBlockSpread(event);

        assertFalse(event.isCancelled());
    }

    // =================================================================================
    // BlockIgniteEvent
    // =================================================================================

    @Test
    void igniteCausedByOurFire_isCancelled() {
        lightScorchFire(OURS);

        BlockIgniteEvent event = new BlockIgniteEvent(FakeBlocks.at(THEIRS),
                BlockIgniteEvent.IgniteCause.SPREAD, FakeBlocks.at(OURS));
        service.onBlockIgnite(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void igniteWithNoIgnitingBlock_isLeftAlone() {
        lightScorchFire(OURS);

        // Flint and steel: the block being lit is a position we own, but no block caused
        // it. Reading getBlock() instead of getIgnitingBlock() would cancel a player's
        // deliberate action here.
        BlockIgniteEvent event = new BlockIgniteEvent(FakeBlocks.at(OURS),
                BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, (Block) null);
        service.onBlockIgnite(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void igniteCausedByVanillaFire_isLeftAlone() {
        lightScorchFire(OURS);

        BlockIgniteEvent event = new BlockIgniteEvent(FakeBlocks.at(OURS),
                BlockIgniteEvent.IgniteCause.SPREAD, FakeBlocks.at(THEIRS));
        service.onBlockIgnite(event);

        assertFalse(event.isCancelled());
    }

    // =================================================================================
    // BlockBurnEvent -- the route scorch fire actually takes first
    // =================================================================================

    @Test
    void burnCausedByOurFire_isCancelled() {
        lightScorchFire(OURS);

        // Scorch fire sits directly on top of a leaf block, so vanilla's checkBurnOut
        // consumes that leaf and puts untracked fire in its place. Without this handler
        // containment holds for exactly one block and then leaks.
        BlockBurnEvent event = new BlockBurnEvent(FakeBlocks.at(THEIRS), FakeBlocks.at(OURS));
        service.onBlockBurn(event);

        assertTrue(event.isCancelled(),
                "the burn route is how scorch fire escapes; cancelling it is why no "
                        + "untracked replacement fire is ever created");
    }

    @Test
    void burnCausedByVanillaFire_isLeftAlone() {
        lightScorchFire(OURS);

        BlockBurnEvent event = new BlockBurnEvent(FakeBlocks.at(OURS), FakeBlocks.at(THEIRS));
        service.onBlockBurn(event);

        assertFalse(event.isCancelled(),
                "a campfire burning a block must consume it exactly as vanilla intends");
    }

    @Test
    void burnWithNoIgnitingBlock_isLeftAlone() {
        lightScorchFire(OURS);

        BlockBurnEvent event = new BlockBurnEvent(FakeBlocks.at(OURS), (Block) null);
        service.onBlockBurn(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void burnCausedByOurFire_isNotCancelledWhenSpreadIsConfiguredOn() {
        scorch = new ScorchSettings(true, true);
        lightScorchFire(OURS);

        BlockBurnEvent event = new BlockBurnEvent(FakeBlocks.at(THEIRS), FakeBlocks.at(OURS));
        service.onBlockBurn(event);

        assertFalse(event.isCancelled());
    }
}
