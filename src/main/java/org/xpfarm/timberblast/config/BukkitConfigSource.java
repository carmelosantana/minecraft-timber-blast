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

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * {@link ConfigSource} adapter over a Bukkit {@link ConfigurationSection}.
 *
 * <p>This is the only class in the package that touches {@code org.bukkit}. It also
 * hosts {@link #isValidMaterial}, the production material validator handed to
 * {@link TbConfig#load} -- keeping {@code Material} resolution here is what lets
 * {@code TbConfig} and {@code ConfigValidator} be unit tested without a server.
 *
 * <p>A {@code null} section is treated as an entirely empty configuration, so every
 * key falls back to its default.
 */
public final class BukkitConfigSource implements ConfigSource {

    private final ConfigurationSection section;

    public BukkitConfigSource(ConfigurationSection section) {
        this.section = section;
    }

    /**
     * Whether {@code name} resolves to a known {@link Material} via
     * {@link Material#matchMaterial(String)}. Method-reference shaped for use as the
     * validator argument of {@link TbConfig#load}.
     */
    public static boolean isValidMaterial(String name) {
        return name != null && Material.matchMaterial(name) != null;
    }

    @Override
    public int getInt(String path, int def) {
        return section == null ? def : section.getInt(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return section == null ? def : section.getDouble(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return section == null ? def : section.getBoolean(path, def);
    }

    @Override
    public String getString(String path, String def) {
        return section == null ? def : section.getString(path, def);
    }
}
