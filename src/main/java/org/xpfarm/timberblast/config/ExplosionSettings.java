/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.config;

/**
 * Validated {@code explosion} settings section of {@code config.yml}.
 *
 * @param power                explosion power passed to {@code World#createExplosion} (valid range 0.0-10.0)
 * @param blockDamage          whether the explosion is allowed to break terrain
 * @param knockbackMultiplier  multiplier applied to the wielder's knockback (valid range 0.0-5.0)
 */
public record ExplosionSettings(double power, boolean blockDamage, double knockbackMultiplier) {
}
