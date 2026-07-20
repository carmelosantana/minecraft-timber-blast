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

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BukkitConfigSource} against a real
 * {@link org.bukkit.configuration.ConfigurationSection}. {@code MemoryConfiguration} is
 * plain data with no server behind it, so the adapter's null guard and its
 * present-but-uncoercible detection are both exercisable here -- and these tests double
 * as the executable proof that {@code MemorySection} really does refuse to parse strings,
 * which is the behaviour {@code MapConfigSource} is written to mirror.
 *
 * <p>{@link BukkitConfigSource#isValidMaterial} is not covered: {@code Material.matchMaterial}
 * needs a running server (the registry is populated at enable time), so it is verified in
 * runtime smoke testing rather than here.
 */
class BukkitConfigSourceTest {

    private final List<String> warnings = new ArrayList<>();

    private BukkitConfigSource sourceOver(MemoryConfiguration section) {
        return new BukkitConfigSource(section, warnings::add);
    }

    // ---- null section --------------------------------------------------------------

    @Test
    void nullSection_behavesAsAnEmptyConfiguration() {
        BukkitConfigSource source = new BukkitConfigSource(null, warnings::add);

        assertEquals(256, source.getInt("fell.max-blocks", 256));
        assertEquals(2.0, source.getDouble("explosion.power", 2.0));
        assertTrue(source.getBoolean("fell.drop-leaves", true));
        assertEquals("GUNPOWDER", source.getString("fuel.material", "GUNPOWDER"));

        assertTrue(warnings.isEmpty(), () -> "an absent configuration is not an error, got " + warnings);
    }

    @Test
    void nullSection_loadsTheDocumentedDefaults() {
        TbConfig config = TbConfig.load(new BukkitConfigSource(null, warnings::add), name -> true, warnings::add);

        assertEquals(256, config.fell().maxBlocks());
        assertEquals("GUNPOWDER", config.fuel().material());
        assertEquals(2.0, config.explosion().power());
        assertTrue(warnings.isEmpty(), () -> "defaults must not warn, got " + warnings);
    }

    // ---- absent keys ---------------------------------------------------------------

    @Test
    void absentKeys_fallBackSilently() {
        BukkitConfigSource source = sourceOver(new MemoryConfiguration());

        assertEquals(8, source.getInt("fell.max-radius", 8));
        assertEquals(1.0, source.getDouble("explosion.knockback-multiplier", 1.0));
        assertTrue(source.getBoolean("coal.enabled", true));

        assertTrue(warnings.isEmpty(), () -> "a missing key is not a typo, got " + warnings);
    }

    // ---- well-typed values ---------------------------------------------------------

    @Test
    void wellTypedValues_areReadThroughWithoutWarning() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("fell.max-blocks", 512);
        section.set("explosion.power", 3.5);
        section.set("fell.drop-leaves", false);
        section.set("fuel.material", "BLAZE_POWDER");

        BukkitConfigSource source = sourceOver(section);

        assertEquals(512, source.getInt("fell.max-blocks", 256));
        assertEquals(3.5, source.getDouble("explosion.power", 2.0));
        assertFalse(source.getBoolean("fell.drop-leaves", true));
        assertEquals("BLAZE_POWDER", source.getString("fuel.material", "GUNPOWDER"));

        assertTrue(warnings.isEmpty(), () -> "valid values must not warn, got " + warnings);
    }

    // ---- present but uncoercible ---------------------------------------------------

    @Test
    void unreadableInt_warnsAndFallsBack() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("fell.max-blocks", "lots");

        assertEquals(256, sourceOver(section).getInt("fell.max-blocks", 256));
        assertSingleWarningNaming("fell.max-blocks", "lots", "256");
    }

    @Test
    void quotedNumericInt_warnsAndFallsBack() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("fell.max-blocks", "4096");

        // Proof of the contract MapConfigSource mirrors: a String is never parsed.
        assertEquals(256, section.getInt("fell.max-blocks", 256));
        assertEquals(256, sourceOver(section).getInt("fell.max-blocks", 256));
        assertSingleWarningNaming("fell.max-blocks", "4096", "256");
    }

    @Test
    void unreadableDouble_warnsAndFallsBack() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("explosion.power", "very");

        assertEquals(2.0, sourceOver(section).getDouble("explosion.power", 2.0));
        assertSingleWarningNaming("explosion.power", "very", "2.0");
    }

    @Test
    void unreadableBoolean_warnsAndFallsBack() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("fell.drop-leaves", "yes-please");

        // Proof of the contract: getBoolean accepts only a real Boolean.
        assertTrue(section.getBoolean("fell.drop-leaves", true));
        assertTrue(sourceOver(section).getBoolean("fell.drop-leaves", true));
        assertSingleWarningNaming("fell.drop-leaves", "yes-please", "true");
    }

    @Test
    void unreadableValue_isReportedThroughTbConfigLoad() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("fell.max-blocks", "lots");

        TbConfig config = TbConfig.load(sourceOver(section), name -> true, warnings::add);

        assertEquals(256, config.fell().maxBlocks());
        assertSingleWarningNaming("fell.max-blocks", "lots", "256");
    }

    private void assertSingleWarningNaming(String key, String value, String fallback) {
        assertEquals(1, warnings.size(), () -> "expected exactly one warning, got " + warnings);
        String message = warnings.get(0);
        assertTrue(message.contains(key), () -> "warning must name the key: " + message);
        assertTrue(message.contains(value), () -> "warning must quote the offending value: " + message);
        assertTrue(message.contains(fallback), () -> "warning must name the substituted default: " + message);
    }
}
