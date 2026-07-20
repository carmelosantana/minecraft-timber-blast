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

import java.util.function.Consumer;

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
 *
 * <p><b>Uncoercible values.</b> {@code ConfigurationSection} returns the caller's
 * default for a value of the wrong type -- {@code getInt} only accepts a
 * {@link Number} and {@code getBoolean} only a {@link Boolean}, so a quoted YAML
 * scalar such as {@code max-blocks: "lots"} (or even {@code "4096"}) is silently
 * ignored. Silence is exactly the wrong answer for an operator typo, so this adapter
 * inspects the raw value first and reports the substitution through the warning sink
 * given to its constructor before returning the default.
 */
public final class BukkitConfigSource implements ConfigSource {

    private final ConfigurationSection section;
    private final Consumer<String> warn;

    /**
     * @param section the section to read, or {@code null} for an empty configuration
     * @param warn    sink for warnings about present-but-unreadable values
     */
    public BukkitConfigSource(ConfigurationSection section, Consumer<String> warn) {
        this.section = section;
        this.warn = warn;
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
        return section == null || rejectMismatch(path, Number.class, def) ? def : section.getInt(path, def);
    }

    @Override
    public double getDouble(String path, double def) {
        return section == null || rejectMismatch(path, Number.class, def) ? def : section.getDouble(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return section == null || rejectMismatch(path, Boolean.class, def) ? def : section.getBoolean(path, def);
    }

    @Override
    public String getString(String path, String def) {
        // Any present value renders as a string, so there is no mismatch case here.
        return section == null ? def : section.getString(path, def);
    }

    /**
     * Whether {@code path} holds a value that {@code ConfigurationSection} would refuse
     * to coerce to {@code expected}; warns as a side effect when it does. An absent key
     * is not a mismatch -- falling back to a default is the documented behaviour there,
     * and warning about it would spam every operator running a partial config.
     */
    private boolean rejectMismatch(String path, Class<?> expected, Object fallback) {
        if (!section.isSet(path)) {
            return false;
        }
        Object raw = section.get(path);
        if (raw == null || expected.isInstance(raw)) {
            return false;
        }
        ConfigValidator.reportUnreadable(path, raw, fallback, warn);
        return true;
    }
}
