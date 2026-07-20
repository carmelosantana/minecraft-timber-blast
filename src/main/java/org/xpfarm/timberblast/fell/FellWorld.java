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

/**
 * Everything a fell does to the world, as four calls.
 *
 * <p>The production implementation ({@link BukkitFellWorld}) fires real events and mutates
 * real blocks; tests implement this and record the calls, which is what makes
 * {@link FellExecutor}'s ordering, veto handling and origin-vs-rest drop decision testable
 * without a server.
 */
public interface FellWorld {

    /**
     * Fires a cancellable {@code BlockBreakEvent} for {@code pos} and reports the verdict.
     *
     * @return {@code true} when the break may proceed, {@code false} when a plugin
     *         cancelled the event and the block must be left standing
     */
    boolean requestBreak(BlockPos pos);

    /** Breaks the block at {@code pos}, rolling its vanilla drop table. */
    void breakNaturally(BlockPos pos);

    /** Removes the block at {@code pos} and drops exactly one {@code drop} in its place. */
    void breakDropping(BlockPos pos, Material drop);

    /** Detonates at the centre of {@code origin} with no source entity. */
    void explode(BlockPos origin, double power, boolean blockDamage);
}
