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

import org.bukkit.inventory.ItemStack;

/**
 * An {@link ItemStack} with an identity and nothing else.
 *
 * <p>{@code ItemStack} is a class, not an interface, so {@link FakeBlocks} cannot proxy it,
 * and {@code new ItemStack(Material.DIAMOND_AXE)} throws {@link ExceptionInInitializerError}
 * offline -- the material path reaches the server's item registry. The protected no-argument
 * constructor does not, which is enough for the tests that need one: they only ever check
 * <em>which</em> stack came out the other end, never what it is made of.
 *
 * <p>Every inherited method that would touch the registry is left inherited on purpose. A
 * test that starts asking this for a {@code Material} gets the same loud failure the rest of
 * the offline suite gives, rather than a plausible-looking wrong answer.
 */
public final class FakeItemStack extends ItemStack {

    private final String label;

    /**
     * @param label what this stands for, used in failure messages
     */
    public FakeItemStack(String label) {
        super();
        this.label = label;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return label;
    }
}
