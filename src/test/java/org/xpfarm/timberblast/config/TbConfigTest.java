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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TbConfig#load}. Deliberately imports nothing from
 * {@code org.bukkit}: reading goes through {@link MapConfigSource} and material
 * resolution through a stub validator, so the entire validation contract is
 * exercised with no running server.
 */
class TbConfigTest {

    /** Materials the stub validator recognises; everything else is "unknown to the server". */
    private static final Set<String> KNOWN_MATERIALS = Set.of("GUNPOWDER", "COAL", "BLAZE_POWDER", "CHARCOAL");

    private static final Function<String, Boolean> VALIDATOR = KNOWN_MATERIALS::contains;

    private final List<String> warnings = new ArrayList<>();

    private void warn(String message) {
        warnings.add(message);
    }

    private TbConfig load(ConfigSource source) {
        return TbConfig.load(source, VALIDATOR, this::warn);
    }

    // ---- defaults ------------------------------------------------------------------

    @Test
    void emptySource_yieldsDocumentedDefaultsWithoutWarnings() {
        TbConfig config = load(MapConfigSource.empty());

        assertEquals(256, config.fell().maxBlocks());
        assertEquals(8, config.fell().maxRadius());
        assertEquals(32, config.fell().maxHeight());
        assertTrue(config.fell().dropLeaves());

        assertEquals("GUNPOWDER", config.fuel().material());
        assertEquals(1, config.fuel().amount());

        assertEquals(2.0, config.explosion().power());
        assertFalse(config.explosion().blockDamage());
        assertEquals(1.0, config.explosion().knockbackMultiplier());

        assertTrue(config.coal().enabled());
        assertEquals("COAL", config.coal().material());

        assertTrue(config.scorch().enabled());
        assertFalse(config.scorch().spread());

        assertTrue(warnings.isEmpty(), () -> "defaults must not warn, got " + warnings);
    }

    @Test
    void configuredValues_areReadThroughUnchanged() {
        TbConfig config = load(MapConfigSource.empty()
                .with("fell.max-blocks", 512)
                .with("fell.max-radius", 12)
                .with("fell.max-height", 48)
                .with("fell.drop-leaves", false)
                .with("fuel.material", "BLAZE_POWDER")
                .with("fuel.amount", 4)
                .with("explosion.power", 3.5)
                .with("explosion.block-damage", true)
                .with("explosion.knockback-multiplier", 0.25)
                .with("coal.enabled", false)
                .with("coal.material", "CHARCOAL")
                .with("scorch.enabled", false)
                .with("scorch.spread", true));

        assertEquals(512, config.fell().maxBlocks());
        assertEquals(12, config.fell().maxRadius());
        assertEquals(48, config.fell().maxHeight());
        assertFalse(config.fell().dropLeaves());
        assertEquals("BLAZE_POWDER", config.fuel().material());
        assertEquals(4, config.fuel().amount());
        assertEquals(3.5, config.explosion().power());
        assertTrue(config.explosion().blockDamage());
        assertEquals(0.25, config.explosion().knockbackMultiplier());
        assertFalse(config.coal().enabled());
        assertEquals("CHARCOAL", config.coal().material());
        assertFalse(config.scorch().enabled());
        assertTrue(config.scorch().spread());

        assertTrue(warnings.isEmpty(), () -> "valid values must not warn, got " + warnings);
    }

    // ---- numeric bounds ------------------------------------------------------------

    /**
     * One bounded numeric key: its path, its inclusive range, its default, and how to
     * pull the loaded value back out of a {@link TbConfig}.
     */
    private record Bound(String key, boolean integral, double min, double max, double def,
                         Function<TbConfig, Double> reader) {

        /** The value as this key's YAML would carry it: a whole number for int keys. */
        Object value(double raw) {
            return integral ? (Object) (int) raw : (Object) raw;
        }

        /** How that value is rendered inside a warning message. */
        String text(double raw) {
            return String.valueOf(value(raw));
        }

        @Override
        public String toString() {
            return key;
        }
    }

    private static Stream<Bound> bounds() {
        return Stream.of(
                new Bound("fell.max-blocks", true, 1, 4096, 256, c -> (double) c.fell().maxBlocks()),
                new Bound("fell.max-radius", true, 1, 64, 8, c -> (double) c.fell().maxRadius()),
                new Bound("fell.max-height", true, 1, 256, 32, c -> (double) c.fell().maxHeight()),
                new Bound("fuel.amount", true, 1, 64, 1, c -> (double) c.fuel().amount()),
                new Bound("explosion.power", false, 0.0, 10.0, 2.0, c -> c.explosion().power()),
                new Bound("explosion.knockback-multiplier", false, 0.0, 5.0, 1.0,
                        c -> c.explosion().knockbackMultiplier())
        );
    }

    @ParameterizedTest(name = "{0} accepts exactly min")
    @MethodSource("bounds")
    void atMinimum_isAccepted(Bound bound) {
        TbConfig config = load(MapConfigSource.of(bound.key(), bound.value(bound.min())));

        assertEquals(bound.min(), bound.reader().apply(config));
        assertTrue(warnings.isEmpty(), () -> "the minimum is valid and must not warn, got " + warnings);
    }

    @ParameterizedTest(name = "{0} accepts exactly max")
    @MethodSource("bounds")
    void atMaximum_isAccepted(Bound bound) {
        TbConfig config = load(MapConfigSource.of(bound.key(), bound.value(bound.max())));

        assertEquals(bound.max(), bound.reader().apply(config));
        assertTrue(warnings.isEmpty(), () -> "the maximum is valid and must not warn, got " + warnings);
    }

    @ParameterizedTest(name = "{0} rejects min - 1")
    @MethodSource("bounds")
    void belowMinimum_fallsBackAndWarns(Bound bound) {
        TbConfig config = load(MapConfigSource.of(bound.key(), bound.value(bound.min() - 1)));

        assertEquals(bound.def(), bound.reader().apply(config));
        assertSingleWarningNaming(bound.key(), bound.text(bound.min() - 1), bound.text(bound.def()));
    }

    @ParameterizedTest(name = "{0} rejects max + 1")
    @MethodSource("bounds")
    void aboveMaximum_fallsBackAndWarns(Bound bound) {
        TbConfig config = load(MapConfigSource.of(bound.key(), bound.value(bound.max() + 1)));

        assertEquals(bound.def(), bound.reader().apply(config));
        assertSingleWarningNaming(bound.key(), bound.text(bound.max() + 1), bound.text(bound.def()));
    }

    @ParameterizedTest(name = "{0} falls back on an unparseable string")
    @MethodSource("bounds")
    void unparseableString_fallsBackToDefault(Bound bound) {
        TbConfig config = load(MapConfigSource.of(bound.key(), "not-a-number"));

        assertEquals(bound.def(), bound.reader().apply(config));
    }

    @ParameterizedTest(name = "{0} accepts a numeric string")
    @MethodSource("bounds")
    void numericString_isCoercedAndAccepted(Bound bound) {
        TbConfig config = load(MapConfigSource.of(bound.key(), bound.text(bound.max())));

        assertEquals(bound.max(), bound.reader().apply(config));
        assertTrue(warnings.isEmpty(), () -> "an in-range numeric string must not warn, got " + warnings);
    }

    // ---- booleans ------------------------------------------------------------------

    @Test
    void unparseableBoolean_fallsBackToDefault() {
        TbConfig config = load(MapConfigSource.empty()
                .with("fell.drop-leaves", "yes-please")
                .with("scorch.spread", "nope"));

        assertTrue(config.fell().dropLeaves());
        assertFalse(config.scorch().spread());
    }

    // ---- materials -----------------------------------------------------------------

    @Test
    void unknownFuelMaterial_fallsBackAndWarns() {
        TbConfig config = load(MapConfigSource.of("fuel.material", "GUNPOWDR"));

        assertEquals("GUNPOWDER", config.fuel().material());
        assertSingleWarningNaming("fuel.material", "GUNPOWDR", "GUNPOWDER");
    }

    @Test
    void unknownCoalMaterial_fallsBackAndWarns() {
        TbConfig config = load(MapConfigSource.of("coal.material", "COALL"));

        assertEquals("COAL", config.coal().material());
        assertSingleWarningNaming("coal.material", "COALL", "COAL");
    }

    @Test
    void blankMaterial_fallsBackAndWarns() {
        TbConfig config = load(MapConfigSource.of("fuel.material", "   "));

        assertEquals("GUNPOWDER", config.fuel().material());
        assertEquals(1, warnings.size(), () -> "expected exactly one warning, got " + warnings);
    }

    @Test
    void surroundingWhitespaceOnAMaterial_isTrimmedNotRejected() {
        TbConfig config = load(MapConfigSource.of("fuel.material", "  BLAZE_POWDER  "));

        assertEquals("BLAZE_POWDER", config.fuel().material());
        assertTrue(warnings.isEmpty(), () -> "a resolvable material must not warn, got " + warnings);
    }

    // ---- warning accounting --------------------------------------------------------

    @Test
    void everyRejectedValue_producesItsOwnWarning() {
        load(MapConfigSource.empty()
                .with("fell.max-blocks", 0)
                .with("fell.max-radius", 65)
                .with("fell.max-height", 0)
                .with("fuel.material", "NOPE")
                .with("fuel.amount", 65)
                .with("explosion.power", 10.5)
                .with("explosion.knockback-multiplier", -0.5)
                .with("coal.material", "ALSO_NOPE"));

        assertEquals(8, warnings.size(), () -> "one warning per rejected key, got " + warnings);
    }

    private void assertSingleWarningNaming(String key, String value, Object fallback) {
        assertEquals(1, warnings.size(), () -> "expected exactly one warning, got " + warnings);
        String message = warnings.get(0);
        assertTrue(message.contains(key), () -> "warning must name the key: " + message);
        assertTrue(message.contains(value), () -> "warning must quote the offending value: " + message);
        assertTrue(message.contains(String.valueOf(fallback)),
                () -> "warning must name the substituted default: " + message);
    }
}
