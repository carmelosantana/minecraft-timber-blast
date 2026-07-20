/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.testsupport;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * A {@link Block} that knows its world name and coordinates and nothing else.
 *
 * <p>{@code Block} and {@code World} are interfaces with hundreds of members, and the
 * project takes no mocking dependency, so these are JDK {@link Proxy} instances -- no
 * library, no bytecode generation, about forty lines. Every method other than the handful
 * a block position is built from throws, which is the point: if an event adapter ever
 * starts reading something else off a block, the tests say so loudly instead of quietly
 * returning a default.
 *
 * <p>This exists only so tests can hand real Bukkit event objects a source block. Nothing
 * here stands in for server behaviour.
 */
public final class FakeBlocks {

    private FakeBlocks() {
    }

    /**
     * A block at {@code (x, y, z)} whose {@code getWorld().getName()} is {@code world}.
     *
     * @param world name reported by the block's world
     * @param x     block x coordinate
     * @param y     block y coordinate
     * @param z     block z coordinate
     * @return a proxy {@link Block} answering only world and coordinate queries
     */
    public static Block at(String world, int x, int y, int z) {
        World w = proxy(World.class, "the world", Map.of("getName", world));
        return proxy(Block.class, "the block at " + world + " " + x + "," + y + "," + z, Map.of(
                "getWorld", w,
                "getX", x,
                "getY", y,
                "getZ", z));
    }

    private static <T> T proxy(Class<T> type, String label, Map<String, Object> answers) {
        InvocationHandler handler = (self, method, args) -> {
            Object answer = answers.get(method.getName());
            if (answer != null) {
                return answer;
            }
            switch (method.getName()) {
                case "equals":
                    return self == args[0];
                case "hashCode":
                    return System.identityHashCode(self);
                case "toString":
                    return label;
                default:
                    throw new UnsupportedOperationException(
                            label + " was asked for " + method.getName()
                                    + "(), which these tests deliberately do not fake");
            }
        };
        return type.cast(Proxy.newProxyInstance(
                FakeBlocks.class.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
