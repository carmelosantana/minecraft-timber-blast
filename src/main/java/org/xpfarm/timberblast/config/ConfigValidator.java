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

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pure, Bukkit-free range validation for {@code config.yml} values.
 *
 * <p>Problems are reported through a caller-supplied {@link Consumer} rather than
 * logged directly, so {@code ConfigValidatorTest} can exercise the whole contract
 * with zero {@code org.bukkit} types and no running server.
 *
 * <p><b>Contract:</b> an out-of-range value is <em>not</em> clamped to the nearest
 * bound -- it is replaced entirely by the documented default, and exactly one warning
 * naming the key, the offending value, and the substituted default is emitted. This
 * class never throws.
 */
public final class ConfigValidator {

    private ConfigValidator() {
    }

    /** Rejects an {@code int} outside {@code [min, max]}, substituting {@code fallback}. */
    public static int requireInRange(String key, int value, int min, int max, int fallback, Consumer<String> warn) {
        if (value < min || value > max) {
            warn.accept(outOfRangeMessage(key, value, min, max, fallback));
            return fallback;
        }
        return value;
    }

    /** Rejects a {@code double} outside {@code [min, max]}, substituting {@code fallback}. */
    public static double requireInRange(String key, double value, double min, double max, double fallback,
                                        Consumer<String> warn) {
        if (value < min || value > max || Double.isNaN(value)) {
            warn.accept(outOfRangeMessage(key, value, min, max, fallback));
            return fallback;
        }
        return value;
    }

    /**
     * Rejects a material name that {@code validator} cannot resolve, substituting
     * {@code fallback}. The validator is injected rather than calling
     * {@code Material.matchMaterial} directly so this class stays Bukkit-free; see
     * {@link BukkitConfigSource#isValidMaterial}.
     */
    public static String requireMaterial(String key, String value, Function<String, Boolean> validator,
                                         String fallback, Consumer<String> warn) {
        String candidate = value == null ? null : value.trim();
        if (candidate == null || candidate.isEmpty() || !Boolean.TRUE.equals(validator.apply(candidate))) {
            warn.accept("TimberBlast config: key '" + key + "' has unknown material '" + value
                    + "'; using default '" + fallback + "' instead.");
            return fallback;
        }
        return candidate;
    }

    /**
     * Reports a key that is present in the configuration but whose stored value cannot be
     * read as the requested type -- an operator typo such as {@code max-blocks: "lots"}.
     * Detecting this is the {@link ConfigSource} implementation's job (only it can see the
     * raw value); phrasing the warning lives here so every rejection reads alike.
     *
     * @param key      the dotted configuration path
     * @param value    the raw, uncoercible value as stored
     * @param fallback the default being substituted
     * @param warn     the warning sink
     */
    public static void reportUnreadable(String key, Object value, Object fallback, Consumer<String> warn) {
        warn.accept("TimberBlast config: key '" + key + "' has unreadable value '" + value
                + "'; using default '" + fallback + "' instead.");
    }

    private static String outOfRangeMessage(String key, Object value, Object min, Object max, Object fallback) {
        return "TimberBlast config: key '" + key + "' has out-of-range value '" + value
                + "' (must be between " + min + " and " + max + "); using default '" + fallback + "' instead.";
    }
}
