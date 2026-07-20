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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link ConfigSource} for tests, holding raw values keyed by dotted path.
 *
 * <p>Coercion mirrors what {@code ConfigurationSection} does with a YAML value: a
 * numeric string is accepted as a number, and anything that cannot be coerced to the
 * requested type yields the caller's default. That keeps these tests honest about the
 * contract {@link BukkitConfigSource} actually delivers at runtime.
 */
final class MapConfigSource implements ConfigSource {

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** Empty source: every key falls back to its default. */
    static MapConfigSource empty() {
        return new MapConfigSource();
    }

    /** Source holding a single key, for one-value-at-a-time validation tests. */
    static MapConfigSource of(String path, Object value) {
        return new MapConfigSource().with(path, value);
    }

    MapConfigSource with(String path, Object value) {
        values.put(path, value);
        return this;
    }

    @Override
    public int getInt(String path, int def) {
        Object raw = values.get(path);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    @Override
    public double getDouble(String path, double def) {
        Object raw = values.get(path);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object raw = values.get(path);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof String str) {
            if (str.equalsIgnoreCase("true")) {
                return true;
            }
            if (str.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return def;
    }

    @Override
    public String getString(String path, String def) {
        Object raw = values.get(path);
        return raw == null ? def : String.valueOf(raw);
    }
}
