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

import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * A {@link Block} that knows its world name and coordinates and nothing else.
 *
 * <p>{@code Block} and {@code World} are interfaces with hundreds of members, and the
 * project takes no mocking dependency, so these are JDK {@link Proxy} instances -- no
 * library, no bytecode generation, about forty lines. Every method other than the handful
 * a {@link FirePos} is built from throws, which is the point: if an event adapter ever
 * starts reading something else off a block, these tests say so loudly instead of quietly
 * returning a default.
 *
 * <p>This exists only so {@code ScorchListenerTest} can hand real Bukkit event objects a
 * source block. Nothing here stands in for server behaviour.
 */
final class FakeBlocks {

    private FakeBlocks() {
    }

    /** A block at {@code pos} whose {@code getWorld().getName()} matches {@code pos}. */
    static Block at(FirePos pos) {
        World world = proxy(World.class, "the world", Map.of("getName", pos.world()));
        return proxy(Block.class, "the block at " + pos, Map.of(
                "getWorld", world,
                "getX", pos.x(),
                "getY", pos.y(),
                "getZ", pos.z()));
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
