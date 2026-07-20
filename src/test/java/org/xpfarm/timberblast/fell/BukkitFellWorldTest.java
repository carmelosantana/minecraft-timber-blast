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

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.testsupport.FakeBlocks;
import org.xpfarm.timberblast.tree.BlockPos;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins where {@link BukkitFellWorld} aims, which is separable from whether the Bukkit calls
 * it makes do anything.
 *
 * <p>Argument order and the block-centre offsets are pure arithmetic on the plugin's side of
 * the boundary, and getting either wrong misplaces every drop and the explosion itself --
 * a defect a live-server smoke test is unlikely to catch, because a tree still falls, just
 * the wrong one. What genuinely needs a server, and is deferred to gate 7a, is
 * {@code setType}, {@code dropItemNaturally}, {@code createExplosion} and
 * {@code BlockBreakEvent} dispatch actually <em>doing</em> something.
 */
class BukkitFellWorldTest {

    /** Every call the adapter made on the world, as {@code "verb args"}. */
    private final List<String> calls = new ArrayList<>();

    private World world() {
        return (World) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{World.class}, (self, method, args) -> switch (method.getName()) {
                    case "getBlockAt" -> {
                        calls.add("getBlockAt " + args[0] + "," + args[1] + "," + args[2]);
                        yield block();
                    }
                    case "dropItemNaturally" -> {
                        calls.add("dropItemNaturally " + at((Location) args[0]));
                        yield null;
                    }
                    case "createExplosion" -> {
                        calls.add("createExplosion " + at((Location) args[0])
                                + " power=" + args[1] + " fire=" + args[2] + " blocks=" + args[3]);
                        yield Boolean.TRUE;
                    }
                    case "toString" -> "the world";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A block that accepts only the two mutations the adapter performs. */
    private Block block() {
        return (Block) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Block.class}, (self, method, args) -> switch (method.getName()) {
                    case "breakNaturally" -> {
                        calls.add("breakNaturally");
                        yield Boolean.TRUE;
                    }
                    case "setType" -> {
                        calls.add("setType " + args[0]);
                        yield null;
                    }
                    case "toString" -> "a block";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static String at(Location location) {
        return location.getX() + "," + location.getY() + "," + location.getZ();
    }

    private BukkitFellWorld fellWorld(World world) {
        Player breaker = FakeBlocks.stub(Player.class, "the wielder", Map.of());
        return new BukkitFellWorld(world, breaker);
    }

    @Test
    void breakNaturallyTargetsTheBlockAtExactlyThoseCoordinates() {
        fellWorld(world()).breakNaturally(new BlockPos(4, 65, -7));

        assertEquals(List.of("getBlockAt 4,65,-7", "breakNaturally"), calls,
                "x, y, z in that order -- a transposition fells a block somewhere else");
    }

    @Test
    void breakDroppingClearsExactlyTheBlockItWasAskedAbout() {
        try {
            fellWorld(world()).breakDropping(new BlockPos(4, 65, -7), Material.COAL);
        } catch (Throwable itemStackNeedsAServer) {
            // new ItemStack(Material, int) resolves through Paper's item registry, which only
            // exists on a running server. That the drop actually spawns therefore defers to
            // gate 7a; that the right block is cleared first does not, and is asserted below.
        }

        assertEquals(List.of("getBlockAt 4,65,-7", "setType AIR"), calls,
                "the struck block is cleared at its own coordinates before anything drops");
    }

    @Test
    void aBlockCentreIsHalfABlockPositiveOnEveryAxis() {
        Location centre = fellWorld(world()).centreOf(new BlockPos(4, 65, -7));

        assertEquals(4.5, centre.getX(), 1.0E-9);
        assertEquals(65.5, centre.getY(), 1.0E-9);
        assertEquals(-6.5, centre.getZ(), 1.0E-9, "-7 + 0.5 is -6.5, not -7.5");
    }

    @Test
    void theExplosionIsCentredOnTheStruckBlockAndNeverStartsFires() {
        fellWorld(world()).explode(new BlockPos(4, 65, -7), 2.5, true);

        assertEquals(List.of("createExplosion 4.5,65.5,-6.5 power=2.5 fire=false blocks=true"),
                calls,
                "centre offsets of +0.5 on every axis, and the fire flag is hard-coded false");
    }

    @Test
    void aNegativeCoordinateStillGetsAPositiveHalfBlockOffset() {
        // -7 + 0.5 is -6.5, not -7.5. Getting the sign of the offset wrong on the negative
        // side of the origin is the classic version of this bug.
        fellWorld(world()).explode(new BlockPos(-1, -2, -3), 1.0, false);

        assertEquals(List.of("createExplosion -0.5,-1.5,-2.5 power=1.0 fire=false blocks=false"),
                calls);
    }
}
