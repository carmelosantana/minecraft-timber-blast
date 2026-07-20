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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the edge cases of {@link ConfigValidator} that cannot be reached
 * through {@link TbConfig#load} with a well-behaved {@link ConfigSource} -- YAML's
 * {@code .NaN}, and a {@code null} material name. The ordinary range and material
 * cases are covered end-to-end in {@code TbConfigTest}.
 */
class ConfigValidatorTest {

    private final List<String> warnings = new ArrayList<>();

    private void warn(String message) {
        warnings.add(message);
    }

    @Test
    void outOfRangeValue_isReplacedByTheDefaultNotClampedToTheBound() {
        int result = ConfigValidator.requireInRange("fell.max-radius", 1000, 1, 64, 8, this::warn);

        assertEquals(8, result, "out-of-range values fall back to the default, they are not clamped to max");
        assertEquals(1, warnings.size());
    }

    @Test
    void notANumber_isRejectedAsOutOfRange() {
        double result = ConfigValidator.requireInRange("explosion.power", Double.NaN, 0.0, 10.0, 2.0, this::warn);

        assertEquals(2.0, result);
        assertEquals(1, warnings.size());
    }

    @Test
    void nullMaterial_fallsBackAndWarns() {
        String result = ConfigValidator.requireMaterial("fuel.material", null, name -> true, "GUNPOWDER", this::warn);

        assertEquals("GUNPOWDER", result);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("fuel.material"));
    }
}
