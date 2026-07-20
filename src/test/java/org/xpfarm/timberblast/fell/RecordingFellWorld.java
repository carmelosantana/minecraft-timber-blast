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
import org.xpfarm.timberblast.tree.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A {@link FellWorld} that breaks nothing and remembers everything it was asked to do. */
final class RecordingFellWorld implements FellWorld {

    /** Positions whose break event comes back cancelled, standing in for a claim plugin. */
    private final Set<BlockPos> vetoed = new HashSet<>();

    /** Every call in order, as {@code "verb x,y,z[ detail]"}. */
    final List<String> calls = new ArrayList<>();

    /** Run inside {@link #explode}, so a test can observe state mid-detonation. */
    Runnable duringExplode = () -> {
    };

    RecordingFellWorld veto(BlockPos pos) {
        vetoed.add(pos);
        return this;
    }

    @Override
    public boolean requestBreak(BlockPos pos) {
        boolean allowed = !vetoed.contains(pos);
        calls.add("requestBreak " + at(pos) + " -> " + allowed);
        return allowed;
    }

    @Override
    public void breakNaturally(BlockPos pos) {
        calls.add("breakNaturally " + at(pos));
    }

    @Override
    public void breakDropping(BlockPos pos, Material drop) {
        calls.add("breakDropping " + at(pos) + " " + drop);
    }

    @Override
    public void explode(BlockPos origin, double power, boolean blockDamage) {
        calls.add("explode " + at(origin) + " power=" + power + " blockDamage=" + blockDamage);
        duringExplode.run();
    }

    /** The positions actually broken, in order, however they were broken. */
    List<String> broken() {
        return calls.stream().filter(c -> c.startsWith("break")).toList();
    }

    private static String at(BlockPos pos) {
        return pos.x() + "," + pos.y() + "," + pos.z();
    }
}
