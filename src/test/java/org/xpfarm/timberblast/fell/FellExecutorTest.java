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
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.config.CoalSettings;
import org.xpfarm.timberblast.config.ExplosionSettings;
import org.xpfarm.timberblast.config.FellSettings;
import org.xpfarm.timberblast.config.FuelSettings;
import org.xpfarm.timberblast.config.TbConfig;
import org.xpfarm.timberblast.tree.BlockKind;
import org.xpfarm.timberblast.tree.BlockPos;
import org.xpfarm.timberblast.tree.BlockQuery;
import org.xpfarm.timberblast.tree.TreeScanner;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link FellExecutor#run} against recording ports. Everything asserted here is a
 * rule the plugin would be wrong without: which block chars into coal, what a protection
 * plugin's veto does, that fuel gates the fell and is paid exactly once, and where the
 * wielder is thrown.
 */
class FellExecutorTest {

    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final BlockPos MIDDLE = new BlockPos(0, 65, 0);
    private static final BlockPos TOP = new BlockPos(0, 66, 0);
    private static final BlockPos LEAF = new BlockPos(1, 67, 0);
    private static final UUID WIELDER = UUID.fromString("00000000-0000-0000-0000-00000000beef");

    /** A three-log trunk with one leaf block hanging off the top. */
    private static BlockQuery tree() {
        Map<BlockPos, BlockKind> blocks = new HashMap<>();
        blocks.put(ORIGIN, BlockKind.LOG);
        blocks.put(MIDDLE, BlockKind.LOG);
        blocks.put(TOP, BlockKind.LOG);
        blocks.put(LEAF, BlockKind.LEAF);
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    private static TbConfig config() {
        return new TbConfig(
                new FellSettings(256, 8, 32, true),
                new FuelSettings("GUNPOWDER", 2),
                new ExplosionSettings(2.0, false, 1.5),
                new CoalSettings(true, "COAL"));
    }

    private static FellExecutor executor(TbConfig config, BlastGuard guard) {
        return new FellExecutor(() -> config, new TreeScanner(), BlockTypes.SERVER_TAGS, guard);
    }

    private static RecordingWielder wielder(int gunpowder) {
        return new RecordingWielder(Material.GUNPOWDER, gunpowder, new Vector(0.5, 64.5, 5.5));
    }

    // --- the origin becomes coal, the rest do not -------------------------------------

    @Test
    void theStruckLogDropsCoalAndEveryOtherLogDropsNaturally() {
        RecordingFellWorld world = new RecordingFellWorld();

        assertTrue(executor(config(), new BlastGuard())
                .run(ORIGIN, tree(), world, wielder(64), WIELDER));

        assertEquals(java.util.List.of(
                        "breakDropping 0,64,0 COAL",
                        "breakNaturally 0,65,0",
                        "breakNaturally 0,66,0",
                        "breakNaturally 1,67,0"),
                world.broken(),
                "only the struck log chars into coal; the rest roll their vanilla drops");
    }

    @Test
    void theStruckLogDropsNaturallyWhenCoalIsDisabled() {
        TbConfig config = new TbConfig(config().fell(), config().fuel(), config().explosion(),
                new CoalSettings(false, "COAL"));
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config, new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(world.broken().contains("breakNaturally 0,64,0"));
        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("breakDropping")));
    }

    @Test
    void theCoalMaterialIsResolvedByNameNotByEnumConstant() {
        TbConfig config = new TbConfig(config().fell(), config().fuel(), config().explosion(),
                new CoalSettings(true, "minecraft:charcoal"));
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config, new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(world.broken().contains("breakDropping 0,64,0 CHARCOAL"),
                "Material.valueOf would have thrown on a namespaced, lower-case name");
    }

    // --- protection plugins get their veto ---------------------------------------------

    @Test
    void aCancelledBreakLeavesThatLogStandingAndFellsTheRest() {
        RecordingFellWorld world = new RecordingFellWorld().veto(MIDDLE);

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.contains("breakNaturally 0,65,0"),
                "the vetoed log must be left standing");
        assertTrue(world.calls.contains("breakDropping 0,64,0 COAL"));
        assertTrue(world.calls.contains("breakNaturally 0,66,0"));
    }

    @Test
    void aCancelledOriginNeitherBreaksNorDropsCoal() {
        RecordingFellWorld world = new RecordingFellWorld().veto(ORIGIN);

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("breakDropping")));
        assertFalse(world.calls.contains("breakNaturally 0,64,0"));
        assertTrue(world.calls.contains("breakNaturally 0,65,0"));
    }

    @Test
    void everyLogIsOfferedForVetoBeforeItIsBroken() {
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertEquals("requestBreak 0,64,0 -> true", world.calls.get(0));
        assertEquals("breakDropping 0,64,0 COAL", world.calls.get(1));
        assertEquals("requestBreak 0,65,0 -> true", world.calls.get(2));
        assertEquals(3, world.calls.stream().filter(c -> c.startsWith("requestBreak")).count(),
                "one break event per log, and none for leaves");
    }

    // --- fuel ---------------------------------------------------------------------------

    @Test
    void tooLittleFuelMeansNoFellAtAll() {
        RecordingFellWorld world = new RecordingFellWorld();
        RecordingWielder wielder = wielder(1);

        assertFalse(executor(config(), new BlastGuard()).run(ORIGIN, tree(), world, wielder, WIELDER));

        assertTrue(world.calls.isEmpty(), "nothing may be broken and nothing may explode");
        assertTrue(wielder.consumed.isEmpty());
        assertNull(wielder.velocity);
        assertEquals(0, wielder.toolDamage);
    }

    @Test
    void exactlyEnoughFuelIsEnough() {
        assertTrue(executor(config(), new BlastGuard())
                .run(ORIGIN, tree(), new RecordingFellWorld(), wielder(2), WIELDER));
    }

    @Test
    void aDifferentMaterialIsNotFuel() {
        RecordingWielder carryingSand = new RecordingWielder(Material.SAND, 64, new Vector(0, 65, 0));

        assertFalse(executor(config(), new BlastGuard())
                .run(ORIGIN, tree(), new RecordingFellWorld(), carryingSand, WIELDER));
    }

    @Test
    void fuelIsConsumedExactlyOncePerFellAndInTheConfiguredAmount() {
        RecordingWielder wielder = wielder(64);

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        assertEquals(java.util.List.of("GUNPOWDER x2"), wielder.consumed);
    }

    @Test
    void theFuelMaterialIsResolvedByNameNotByEnumConstant() {
        TbConfig config = new TbConfig(config().fell(), new FuelSettings("gunpowder", 1),
                config().explosion(), config().coal());

        assertTrue(executor(config, new BlastGuard())
                .run(ORIGIN, tree(), new RecordingFellWorld(), wielder(1), WIELDER),
                "Material.valueOf would have thrown on a lower-case name");
    }

    // --- knockback ------------------------------------------------------------------------

    @Test
    void theWielderIsThrownAwayFromTheStruckBlockScaledByTheMultiplier() {
        RecordingWielder wielder = wielder(64);

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        // Standing five blocks north-of-nothing, straight along +z from the block centre.
        assertEquals(0.0, wielder.velocity.getX(), 1.0E-9);
        assertEquals(0.0, wielder.velocity.getY(), 1.0E-9);
        assertEquals(1.5, wielder.velocity.getZ(), 1.0E-9, "away from the blast, scaled by 1.5");
    }

    @Test
    void aWielderOnTheOtherSideIsThrownTheOtherWay() {
        RecordingWielder wielder = new RecordingWielder(Material.GUNPOWDER, 64, new Vector(0.5, 64.5, -5.5));

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        assertEquals(-1.5, wielder.velocity.getZ(), 1.0E-9);
    }

    // --- explosion and its damage window ----------------------------------------------------

    @Test
    void theExplosionUsesTheConfiguredPowerAndBlockDamage() {
        TbConfig config = new TbConfig(config().fell(), config().fuel(),
                new ExplosionSettings(4.5, true, 1.0), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config, new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(world.calls.contains("explode 0,64,0 power=4.5 blockDamage=true"));
    }

    @Test
    void theWielderIsGuardedDuringTheExplosionAndNotBeforeOrAfter() {
        BlastGuard guard = new BlastGuard();
        RecordingFellWorld world = new RecordingFellWorld();
        AtomicBoolean guardedMidBlast = new AtomicBoolean();
        world.duringExplode = () -> guardedMidBlast.set(guard.isBlasting(WIELDER));

        assertFalse(guard.isBlasting(WIELDER));
        executor(config(), guard).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(guardedMidBlast.get(), "damage suppression must be armed during the blast");
        assertFalse(guard.isBlasting(WIELDER), "and disarmed the moment it is over");
    }

    @Test
    void theGuardIsClearedEvenWhenTheExplosionThrows() {
        BlastGuard guard = new BlastGuard();
        RecordingFellWorld world = new RecordingFellWorld();
        world.duringExplode = () -> {
            throw new IllegalStateException("boom");
        };

        try {
            executor(config(), guard).run(ORIGIN, tree(), world, wielder(64), WIELDER);
        } catch (IllegalStateException expected) {
            // the point is what happens to the guard, not the exception
        }

        assertFalse(guard.isBlasting(WIELDER), "a stuck guard would make the player immortal");
    }

    // --- leaves, bounds, tool -----------------------------------------------------------------

    @Test
    void leavesAreLeftAloneWhenDropLeavesIsOff() {
        TbConfig config = new TbConfig(new FellSettings(256, 8, 32, false), config().fuel(),
                config().explosion(), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config, new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.contains("breakNaturally 1,67,0"));
        assertTrue(world.calls.contains("breakNaturally 0,66,0"), "logs still go");
    }

    @Test
    void theConfiguredBlockCapReachesTheScanner() {
        TbConfig config = new TbConfig(new FellSettings(1, 8, 32, false), config().fuel(),
                config().explosion(), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config, new BlastGuard()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertEquals(1, world.calls.stream().filter(c -> c.startsWith("requestBreak")).count());
    }

    @Test
    void aNonLogOriginIsNotAFell() {
        RecordingFellWorld world = new RecordingFellWorld();
        RecordingWielder wielder = wielder(64);
        BlockQuery nothing = pos -> BlockKind.OTHER;

        assertFalse(executor(config(), new BlastGuard()).run(ORIGIN, nothing, world, wielder, WIELDER));

        assertTrue(world.calls.isEmpty());
        assertTrue(wielder.consumed.isEmpty(), "no fell, no fuel");
    }

    @Test
    void theAxeLosesExactlyOnePointOfDurabilityPerFell() {
        RecordingWielder wielder = wielder(64);

        executor(config(), new BlastGuard()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        assertEquals(1, wielder.toolDamage);
    }
}
