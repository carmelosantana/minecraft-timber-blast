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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The set of players whose own Timber Blast is detonating right now.
 *
 * <p>This is how the damage suppression stays narrow. {@link FellExecutor} marks the
 * wielder immediately before {@code World#createExplosion} and unmarks them immediately
 * after; because explosion damage is dealt synchronously inside that call, the window is
 * exactly the blast and nothing else. Every other explosion on the server -- creepers, TNT,
 * another player's axe -- sees an unmarked player and is left alone.
 */
public final class BlastGuard {

    private final Set<UUID> blasting = ConcurrentHashMap.newKeySet();

    /** Marks {@code player} as mid-blast. */
    public void begin(UUID player) {
        blasting.add(player);
    }

    /** Clears the mark. Safe to call for a player who was never marked. */
    public void end(UUID player) {
        blasting.remove(player);
    }

    /** Whether {@code player} is inside their own blast right now. */
    public boolean isBlasting(UUID player) {
        return blasting.contains(player);
    }
}
