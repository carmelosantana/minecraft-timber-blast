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
import java.util.function.Consumer;

/**
 * In-memory {@link ConfigSource} for tests, holding raw values keyed by dotted path.
 *
 * <p>Coercion mirrors {@code MemorySection} exactly: a numeric getter accepts only a
 * {@link Number} and the boolean getter only a {@link Boolean}. Strings are never
 * parsed -- not even {@code "4096"} -- because the real
 * {@code ConfigurationSection.getInt(String, int)} returns the caller's default unless
 * the stored value is already a {@code Number}. A present-but-uncoercible value warns
 * and falls back, matching {@link BukkitConfigSource}. Keeping this double honest to
 * the runtime is the whole point of it existing.
 */
final class MapConfigSource implements ConfigSource {

    private final Map<String, Object> values = new LinkedHashMap<>();

    private Consumer<String> warn = message -> {
    };

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

    /** Routes this source's own warnings about unreadable values to {@code sink}. */
    MapConfigSource warnTo(Consumer<String> sink) {
        this.warn = sink;
        return this;
    }

    @Override
    public int getInt(String path, int def) {
        Object raw = values.get(path);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        rejected(path, raw, def);
        return def;
    }

    @Override
    public double getDouble(String path, double def) {
        Object raw = values.get(path);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        rejected(path, raw, def);
        return def;
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        Object raw = values.get(path);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        rejected(path, raw, def);
        return def;
    }

    @Override
    public String getString(String path, String def) {
        Object raw = values.get(path);
        return raw == null ? def : String.valueOf(raw);
    }

    /** Warns when {@code raw} is present but of the wrong type; absent keys are silent. */
    private boolean rejected(String path, Object raw, Object fallback) {
        if (raw == null) {
            return false;
        }
        ConfigValidator.reportUnreadable(path, raw, fallback, warn);
        return true;
    }
}
