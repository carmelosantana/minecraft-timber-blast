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
 * Read-only, Bukkit-free view of a configuration tree addressed by dotted paths.
 *
 * <p>This is the seam that keeps {@link TbConfig} testable without a running server:
 * production code hands it a {@link BukkitConfigSource} wrapping the plugin's
 * {@code config.yml}, while tests hand it a plain map.
 *
 * <p><b>Contract:</b> every getter returns {@code def} when the path is absent, and
 * also when the stored value cannot be coerced to the requested type. Coercion is the
 * implementation's business -- range validation is not, and lives in {@link TbConfig}.
 * No getter ever throws.
 */
public interface ConfigSource {

    /** The {@code int} at {@code path}, or {@code def} if absent or not a number. */
    int getInt(String path, int def);

    /** The {@code double} at {@code path}, or {@code def} if absent or not a number. */
    double getDouble(String path, double def);

    /** The {@code boolean} at {@code path}, or {@code def} if absent or not a boolean. */
    boolean getBoolean(String path, boolean def);

    /** The {@code String} at {@code path}, or {@code def} if absent. */
    String getString(String path, String def);
}
