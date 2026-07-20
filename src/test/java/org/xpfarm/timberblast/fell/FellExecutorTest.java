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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                new FellSettings(256, 256, 8, 32, true),
                new FuelSettings("GUNPOWDER", 2),
                new ExplosionSettings(2.0, false, 1.5),
                new CoalSettings(true, "COAL"));
    }

    private static FellExecutor executor(TbConfig config) {
        return new FellExecutor(() -> config, new TreeScanner(), BlockTypes.SERVER_TAGS);
    }

    private static RecordingWielder wielder(int gunpowder) {
        return new RecordingWielder(Material.GUNPOWDER, gunpowder, new Vector(0.5, 64.5, 5.5));
    }

    // --- the origin becomes coal, the rest do not -------------------------------------

    @Test
    void theStruckLogDropsCoalAndEveryOtherLogDropsNaturally() {
        RecordingFellWorld world = new RecordingFellWorld();

        assertTrue(executor(config())
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

        executor(config).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(world.broken().contains("breakNaturally 0,64,0"));
        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("breakDropping")));
    }

    @Test
    void theCoalMaterialIsResolvedByNameNotByEnumConstant() {
        TbConfig config = new TbConfig(config().fell(), config().fuel(), config().explosion(),
                new CoalSettings(true, "minecraft:charcoal"));
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(world.broken().contains("breakDropping 0,64,0 CHARCOAL"),
                "Material.valueOf would have thrown on a namespaced, lower-case name");
    }

    // --- protection plugins get their veto ---------------------------------------------

    @Test
    void aCancelledBreakLeavesThatLogStandingAndFellsTheRest() {
        RecordingFellWorld world = new RecordingFellWorld().veto(MIDDLE);

        executor(config()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.contains("breakNaturally 0,65,0"),
                "the vetoed log must be left standing");
        assertTrue(world.calls.contains("breakDropping 0,64,0 COAL"));
        assertTrue(world.calls.contains("breakNaturally 0,66,0"));
    }

    @Test
    void aCancelledOriginNeitherBreaksNorDropsCoal() {
        RecordingFellWorld world = new RecordingFellWorld().veto(ORIGIN);

        executor(config()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("breakDropping")));
        assertFalse(world.calls.contains("breakNaturally 0,64,0"));
        assertTrue(world.calls.contains("breakNaturally 0,65,0"));
    }

    @Test
    void everyLogIsOfferedForVetoBeforeItIsBroken() {
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertEquals("requestBreak 0,64,0 -> true", world.calls.get(0));
        assertEquals("breakDropping 0,64,0 COAL", world.calls.get(1));
        assertEquals("requestBreak 0,65,0 -> true", world.calls.get(2));
        assertEquals(4, world.calls.stream().filter(c -> c.startsWith("requestBreak")).count(),
                "one break event per log and one per leaf -- nothing is removed unprotected");
    }

    // --- a fully vetoed fell costs nothing and touches nothing (step 4a) -------------------

    @Test
    void aFellWhereEveryLogIsVetoedIsAbandonedEntirely() {
        RecordingFellWorld world = new RecordingFellWorld().veto(ORIGIN).veto(MIDDLE).veto(TOP);
        RecordingWielder wielder = wielder(64);

        assertFalse(executor(config()).run(ORIGIN, tree(), world, wielder, WIELDER),
                "a fell that felled nothing did not happen");

        assertTrue(world.broken().isEmpty(), "nothing came down");
        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("explode")),
                "a protection plugin's veto must not be answered with an explosion anyway");
        assertTrue(wielder.consumed.isEmpty(), "gunpowder must not be spent on a refused fell");
        assertNull(wielder.velocity, "and the player must not be shoved for it");
        assertEquals(0, wielder.toolDamage, "nor the axe worn");
    }

    @Test
    void aFullyVetoedFellLeavesTheCanopyStandingToo() {
        // The interaction that matters: leaves are not collateral. A claim that refuses the
        // trunk must not lose its canopy as a side effect.
        RecordingFellWorld world = new RecordingFellWorld().veto(ORIGIN).veto(MIDDLE).veto(TOP);

        executor(config()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.contains("breakNaturally 1,67,0"));
        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("requestBreak 1,67,0")),
                "the leaf pass is not even reached once the fell is abandoned");
    }

    @Test
    void oneSurvivingLogIsEnoughForTheFellToGoAhead() {
        // The boundary on the other side of step 4a: partial protection is still a fell.
        RecordingFellWorld world = new RecordingFellWorld().veto(ORIGIN).veto(MIDDLE);
        RecordingWielder wielder = wielder(64);

        assertTrue(executor(config()).run(ORIGIN, tree(), world, wielder, WIELDER));

        assertEquals(java.util.List.of("GUNPOWDER x2"), wielder.consumed);
        assertEquals(1, wielder.toolDamage);
    }

    // --- leaves go through the veto as well (step 5) ---------------------------------------

    @Test
    void everyLeafIsOfferedForVetoBeforeItIsBroken() {
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        int leafRequest = world.calls.indexOf("requestBreak 1,67,0 -> true");
        int leafBreak = world.calls.indexOf("breakNaturally 1,67,0");
        assertTrue(leafRequest >= 0, "the leaf must be offered to protection plugins");
        assertTrue(leafRequest < leafBreak, "and offered before it is removed");
    }

    @Test
    void aVetoedLeafIsLeftHangingWhileTheLogsStillComeDown() {
        RecordingFellWorld world = new RecordingFellWorld().veto(LEAF);

        executor(config()).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.contains("breakNaturally 1,67,0"),
                "the protected leaf must be left standing");
        assertTrue(world.calls.contains("breakNaturally 0,66,0"), "the logs are unaffected");
    }

    // --- fell.max-leaves bounds the event volume ------------------------------------------

    /**
     * The same three-log trunk under a full 25-block canopy: every non-log position in the
     * 3x3x3 shell around the top two logs. {@code fell.max-blocks} does not bound any of it --
     * that key caps logs only -- so without {@code fell.max-leaves} this fell dispatches 28
     * synchronous {@code BlockBreakEvent}s, and a real jungle tree dispatches several hundred.
     */
    private static BlockQuery leafyTree() {
        Map<BlockPos, BlockKind> blocks = new HashMap<>();
        blocks.put(ORIGIN, BlockKind.LOG);
        blocks.put(MIDDLE, BlockKind.LOG);
        blocks.put(TOP, BlockKind.LOG);
        for (int x = -1; x <= 1; x++) {
            for (int y = 65; y <= 67; y++) {
                for (int z = -1; z <= 1; z++) {
                    blocks.putIfAbsent(new BlockPos(x, y, z), BlockKind.LEAF);
                }
            }
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    /** How many break events the fell offered for blocks that are not the trunk. */
    private static long leafRequests(RecordingFellWorld world) {
        return world.calls.stream()
                .filter(c -> c.startsWith("requestBreak"))
                .filter(c -> !c.contains("0,64,0") && !c.contains("0,65,0") && !c.contains("0,66,0"))
                .count();
    }

    private static TbConfig withLeafCap(int maxLeaves) {
        FellSettings fell = config().fell();
        return new TbConfig(
                new FellSettings(fell.maxBlocks(), maxLeaves, fell.maxRadius(), fell.maxHeight(), true),
                config().fuel(), config().explosion(), config().coal());
    }

    @Test
    void theCanopyIsBigEnoughToNeedCapping() {
        // Guards the three tests below: if the fixture ever stopped producing more leaves than
        // the caps they set, they would pass vacuously.
        RecordingFellWorld world = new RecordingFellWorld();

        executor(withLeafCap(4096)).run(ORIGIN, leafyTree(), world, wielder(64), WIELDER);

        assertEquals(25, leafRequests(world),
                "the fixture must offer far more leaves than the caps under test");
    }

    @Test
    void theLeafCapBoundsHowManyBreakEventsOneFellDispatches() {
        RecordingFellWorld world = new RecordingFellWorld();

        assertTrue(executor(withLeafCap(4)).run(ORIGIN, leafyTree(), world, wielder(64), WIELDER));

        assertEquals(4, leafRequests(world),
                "fell.max-leaves must bound the leaf BlockBreakEvents, not merely the drops");
        assertEquals(4, world.broken().stream().filter(b -> !b.contains(",64,0")
                        && !b.contains("0,65,0") && !b.contains("0,66,0")).count());
    }

    @Test
    void aLeafCapOfZeroBreaksNoLeavesAtAllButStillFellsTheTree() {
        RecordingFellWorld world = new RecordingFellWorld();

        assertTrue(executor(withLeafCap(0)).run(ORIGIN, leafyTree(), world, wielder(64), WIELDER));

        assertEquals(0, leafRequests(world), "a cap of 0 must break no leaves at all");
        assertTrue(world.broken().contains("breakDropping 0,64,0 COAL"),
                "the leaf cap must not touch the logs");
        assertEquals(3, world.broken().size(), "exactly the three trunk logs come down");
    }

    /**
     * The cap counts offers, not successful breaks. If it counted breaks, a claim that vetoes
     * every leaf would drive one event per leaf in the whole canopy while breaking nothing --
     * unbounded dispatch in exactly the situation the cap exists for.
     */
    @Test
    void aVetoingClaimCannotDriveMoreLeafEventsThanTheCapAllows() {
        RecordingFellWorld world = new RecordingFellWorld();
        for (int x = -1; x <= 1; x++) {
            for (int y = 65; y <= 67; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    // The cube overlaps the trunk; veto only the canopy, so the assertion
                    // below distinguishes "leaves refused" from "whole fell refused".
                    if (!pos.equals(MIDDLE) && !pos.equals(TOP)) {
                        world.veto(pos);
                    }
                }
            }
        }

        executor(withLeafCap(4)).run(ORIGIN, leafyTree(), world, wielder(64), WIELDER);

        assertEquals(4, leafRequests(world),
                "vetoed leaves must still consume the budget, or the cap bounds nothing");
        assertEquals(3, world.broken().size(), "no leaf came down, but the trunk still did");
    }

    // --- fuel ---------------------------------------------------------------------------

    @Test
    void tooLittleFuelMeansNoFellAtAll() {
        RecordingFellWorld world = new RecordingFellWorld();
        RecordingWielder wielder = wielder(1);

        assertFalse(executor(config()).run(ORIGIN, tree(), world, wielder, WIELDER));

        assertTrue(world.calls.isEmpty(), "nothing may be broken and nothing may explode");
        assertTrue(wielder.consumed.isEmpty());
        assertNull(wielder.velocity);
        assertEquals(0, wielder.toolDamage);
    }

    @Test
    void exactlyEnoughFuelIsEnough() {
        assertTrue(executor(config())
                .run(ORIGIN, tree(), new RecordingFellWorld(), wielder(2), WIELDER));
    }

    @Test
    void aDifferentMaterialIsNotFuel() {
        RecordingWielder carryingSand = new RecordingWielder(Material.SAND, 64, new Vector(0, 65, 0));

        assertFalse(executor(config())
                .run(ORIGIN, tree(), new RecordingFellWorld(), carryingSand, WIELDER));
    }

    @Test
    void fuelIsConsumedExactlyOncePerFellAndInTheConfiguredAmount() {
        RecordingWielder wielder = wielder(64);

        executor(config()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        assertEquals(java.util.List.of("GUNPOWDER x2"), wielder.consumed);
    }

    @Test
    void theFuelMaterialIsResolvedByNameNotByEnumConstant() {
        TbConfig config = new TbConfig(config().fell(), new FuelSettings("gunpowder", 1),
                config().explosion(), config().coal());

        assertTrue(executor(config)
                .run(ORIGIN, tree(), new RecordingFellWorld(), wielder(1), WIELDER),
                "Material.valueOf would have thrown on a lower-case name");
    }

    // --- knockback ------------------------------------------------------------------------

    @Test
    void theWielderIsThrownAwayFromTheStruckBlockScaledByTheMultiplier() {
        RecordingWielder wielder = wielder(64);

        executor(config()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        // Standing five blocks north-of-nothing, straight along +z from the block centre.
        assertEquals(0.0, wielder.velocity.getX(), 1.0E-9);
        assertEquals(0.0, wielder.velocity.getY(), 1.0E-9);
        assertEquals(1.5, wielder.velocity.getZ(), 1.0E-9, "away from the blast, scaled by 1.5");
    }

    @Test
    void aWielderOnTheOtherSideIsThrownTheOtherWay() {
        RecordingWielder wielder = new RecordingWielder(Material.GUNPOWDER, 64, new Vector(0.5, 64.5, -5.5));

        executor(config()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        assertEquals(-1.5, wielder.velocity.getZ(), 1.0E-9);
    }

    // --- explosion and its damage window ----------------------------------------------------

    @Test
    void theExplosionUsesTheConfiguredPowerAndBlockDamage() {
        TbConfig config = new TbConfig(config().fell(), config().fuel(),
                new ExplosionSettings(4.5, true, 1.0), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(world.calls.contains("explode 0,64,0 power=4.5 blockDamage=true"));
    }

    @Test
    void theWielderIsGuardedDuringTheExplosionAndNotBeforeOrAfter() {
        FellExecutor executor = executor(config());
        BlastGuard guard = executor.guard();
        RecordingFellWorld world = new RecordingFellWorld();
        AtomicBoolean guardedMidBlast = new AtomicBoolean();
        world.duringExplode = () -> guardedMidBlast.set(guard.isBlasting(WIELDER));

        assertFalse(guard.isBlasting(WIELDER));
        executor.run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertTrue(guardedMidBlast.get(), "damage suppression must be armed during the blast");
        assertFalse(guard.isBlasting(WIELDER), "and disarmed the moment it is over");
    }

    @Test
    void theGuardIsClearedEvenWhenTheExplosionThrows() {
        FellExecutor executor = executor(config());
        BlastGuard guard = executor.guard();
        RecordingFellWorld world = new RecordingFellWorld();
        world.duringExplode = () -> {
            throw new IllegalStateException("boom");
        };

        try {
            executor.run(ORIGIN, tree(), world, wielder(64), WIELDER);
        } catch (IllegalStateException expected) {
            // the point is what happens to the guard, not the exception
        }

        assertFalse(guard.isBlasting(WIELDER), "a stuck guard would make the player immortal");
    }

    // --- leaves, bounds, tool -----------------------------------------------------------------

    @Test
    void leavesAreLeftAloneWhenDropLeavesIsOff() {
        TbConfig config = new TbConfig(new FellSettings(256, 256, 8, 32, false), config().fuel(),
                config().explosion(), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertFalse(world.calls.contains("breakNaturally 1,67,0"));
        assertTrue(world.calls.contains("breakNaturally 0,66,0"), "logs still go");
    }

    @Test
    void theConfiguredBlockCapReachesTheScanner() {
        TbConfig config = new TbConfig(new FellSettings(1, 256, 8, 32, false), config().fuel(),
                config().explosion(), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config).run(ORIGIN, tree(), world, wielder(64), WIELDER);

        assertEquals(1, world.calls.stream().filter(c -> c.startsWith("requestBreak")).count());
    }

    @Test
    void aNonLogOriginIsNotAFell() {
        RecordingFellWorld world = new RecordingFellWorld();
        RecordingWielder wielder = wielder(64);
        BlockQuery nothing = pos -> BlockKind.OTHER;

        assertFalse(executor(config()).run(ORIGIN, nothing, world, wielder, WIELDER));

        assertTrue(world.calls.isEmpty());
        assertTrue(wielder.consumed.isEmpty(), "no fell, no fuel");
    }

    @Test
    void theAxeLosesExactlyOnePointOfDurabilityPerFell() {
        RecordingWielder wielder = wielder(64);

        executor(config()).run(ORIGIN, tree(), new RecordingFellWorld(), wielder, WIELDER);

        assertEquals(1, wielder.toolDamage);
    }

    // --- radius and height are two different dials ------------------------------------------

    /**
     * A six-log trunk straight up from the origin and a five-log branch straight out along
     * +x. Deliberately asymmetric: a tree that fits inside both bounds cannot tell them
     * apart, which is exactly how a transposed {@code maxRadius}/{@code maxHeight} pair
     * survives.
     */
    private static BlockQuery trunkAndBranch() {
        Map<BlockPos, BlockKind> blocks = new HashMap<>();
        for (int dy = 0; dy <= 5; dy++) {
            blocks.put(new BlockPos(0, 64 + dy, 0), BlockKind.LOG);
        }
        for (int dx = 1; dx <= 5; dx++) {
            blocks.put(new BlockPos(dx, 64, 0), BlockKind.LOG);
        }
        return pos -> blocks.getOrDefault(pos, BlockKind.OTHER);
    }

    @Test
    void maxHeightBoundsTheClimbAndMaxRadiusBoundsTheReach() {
        // radius 2, height 5: the whole trunk comes down, the branch stops two out.
        TbConfig config = new TbConfig(new FellSettings(256, 256, 2, 5, false), config().fuel(),
                config().explosion(), new CoalSettings(false, "COAL"));
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config).run(ORIGIN, trunkAndBranch(), world, wielder(64), WIELDER);

        assertTrue(world.calls.contains("breakNaturally 0,69,0"),
                "maxHeight 5 must allow five blocks of climb");
        assertTrue(world.calls.contains("breakNaturally 2,64,0"));
        assertFalse(world.calls.contains("breakNaturally 3,64,0"),
                "maxRadius 2 must stop the branch two blocks out");
    }

    @Test
    void swappingRadiusAndHeightFellsAVisiblyDifferentTree() {
        // The same tree under the transposed bounds: a squat, wide fell instead of a tall,
        // narrow one. Same log count, different logs -- so this is asserted by position.
        TbConfig config = new TbConfig(new FellSettings(256, 256, 5, 2, false), config().fuel(),
                config().explosion(), new CoalSettings(false, "COAL"));
        RecordingFellWorld world = new RecordingFellWorld();

        executor(config).run(ORIGIN, trunkAndBranch(), world, wielder(64), WIELDER);

        assertTrue(world.calls.contains("breakNaturally 5,64,0"),
                "maxRadius 5 must allow five blocks of reach");
        assertFalse(world.calls.contains("breakNaturally 3,66,0"));
        assertFalse(world.calls.contains("breakNaturally 0,69,0"),
                "maxHeight 2 must stop the trunk two blocks up");
    }

    // --- defence in depth on an unusable fuel material -----------------------------------------

    @Test
    void anUnresolvableFuelMaterialAbortsTheFellRatherThanFellingForFree() {
        // ConfigValidator substitutes a default, so this is unreachable through config.yml.
        // It is asserted anyway because the failure mode if the guard were dropped is that
        // matchMaterial returns null, hasFuel is never consulted, and every swing fells free.
        TbConfig config = new TbConfig(config().fell(), new FuelSettings("NOT_A_MATERIAL", 2),
                config().explosion(), config().coal());
        RecordingFellWorld world = new RecordingFellWorld();
        RecordingWielder wielder = wielder(64);

        assertFalse(executor(config).run(ORIGIN, tree(), world, wielder, WIELDER));

        assertTrue(world.calls.isEmpty());
        assertTrue(wielder.consumed.isEmpty());
        assertTrue(wielder.asked.isEmpty(),
                "the inventory must never be asked whether it holds 'null' of anything");
    }

    @Test
    void anUnresolvableCoalMaterialStillFellsTheTreeAndDropsTheLogNaturally() {
        // The asymmetry with fuel is deliberate: a cosmetic reward is not worth refusing the
        // feature over. Asserted so the difference is a decision rather than an oversight.
        TbConfig config = new TbConfig(config().fell(), config().fuel(), config().explosion(),
                new CoalSettings(true, "NOT_A_MATERIAL"));
        RecordingFellWorld world = new RecordingFellWorld();

        assertTrue(executor(config).run(ORIGIN, tree(), world, wielder(64), WIELDER));

        assertTrue(world.calls.contains("breakNaturally 0,64,0"));
        assertFalse(world.calls.stream().anyMatch(c -> c.startsWith("breakDropping")));
    }

    // --- the guard is the executor's own, and nobody else's ------------------------------------

    @Test
    void theExecutorOwnsTheGuardTheDamageListenerMustShare() {
        FellExecutor executor = executor(config());

        assertNotNull(executor.guard());
        assertSame(executor.guard(), executor.guard(), "one guard per executor, not one per call");
    }

    // --- the Bukkit-facing entry point builds the origin from the struck block ------------------

    @Test
    void theStruckBlocksCoordinatesBecomeTheScanOriginInThatOrder() {
        // fell(Player, Block) is the sole place the origin BlockPos is built, and it feeds
        // the scan, the coal drop and the explosion. A transposition here would fell a tree
        // somewhere else entirely. The world is empty, so the scan finds no log and the fell
        // bails immediately -- but not before asking about the origin, which is the assertion.
        List<String> asked = new ArrayList<>();
        World world = (World) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{World.class}, (self, method, args) -> {
                    if (method.getName().equals("getBlockAt") && args.length == 3) {
                        asked.add(args[0] + "," + args[1] + "," + args[2]);
                        return org.xpfarm.timberblast.testsupport.FakeBlocks
                                .at("w", (int) args[0], (int) args[1], (int) args[2], Material.STONE);
                    }
                    if (method.getName().equals("toString")) {
                        return "the world";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        Block struck = org.xpfarm.timberblast.testsupport.FakeBlocks.stub(Block.class,
                "the struck log", Map.of("getX", 4, "getY", 65, "getZ", -7, "getWorld", world));
        Player player = org.xpfarm.timberblast.testsupport.FakeBlocks.stub(Player.class,
                "the wielder", Map.of("getUniqueId", WIELDER, "getInventory", fullOfGunpowder()));

        new FellExecutor(FellExecutorTest::config, new TreeScanner(), NOTHING_IS_A_LOG)
                .fell(player, struck);

        assertEquals(List.of("4,65,-7"), asked,
                "the origin is (getX, getY, getZ) of the struck block, in that order");
    }

    /** An inventory that always has the fuel, so {@code fell} gets as far as the scan. */
    private static PlayerInventory fullOfGunpowder() {
        return org.xpfarm.timberblast.testsupport.FakeBlocks.stub(PlayerInventory.class,
                "a bag of gunpowder", Map.of("contains", true));
    }

    private static final BlockTypes NOTHING_IS_A_LOG = new BlockTypes() {

        @Override
        public boolean isLog(Material material) {
            return false;
        }

        @Override
        public boolean isLeaf(Material material) {
            return false;
        }
    };
}
