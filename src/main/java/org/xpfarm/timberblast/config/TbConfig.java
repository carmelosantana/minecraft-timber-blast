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
 * Immutable, fully validated snapshot of {@code config.yml}.
 *
 * <p>Built via {@link #load}, which never throws and never prevents the plugin from
 * enabling: any missing, out-of-range, or unresolvable value is replaced with its
 * documented default and reported through the supplied warning sink. A server
 * operator with a typo in {@code config.yml} still gets a fully working plugin.
 *
 * <p>This class deliberately imports nothing from {@code org.bukkit}. Reading is
 * delegated to a {@link ConfigSource} and material resolution to an injected
 * validator, so the whole parsing contract is unit testable without a server; see
 * {@link BukkitConfigSource} for the production wiring of both.
 *
 * @param fell      validated {@code fell} section
 * @param fuel      validated {@code fuel} section
 * @param explosion validated {@code explosion} section
 * @param coal      validated {@code coal} section
 * @param scorch    validated {@code scorch} section
 */
public record TbConfig(
        FellSettings fell,
        FuelSettings fuel,
        ExplosionSettings explosion,
        CoalSettings coal,
        ScorchSettings scorch
) {

    /** Default for {@code fuel.material}, per the shipping {@code config.yml}. */
    public static final String DEFAULT_FUEL_MATERIAL = "GUNPOWDER";

    /** Default for {@code coal.material}, per the shipping {@code config.yml}. */
    public static final String DEFAULT_COAL_MATERIAL = "COAL";

    /**
     * Loads and validates configuration from {@code source}.
     *
     * @param source            the configuration to read from; an empty source yields
     *                          the documented defaults with no warnings
     * @param materialValidator resolves a material name to {@code true} when the server
     *                          knows it; in production {@code BukkitConfigSource::isValidMaterial}
     * @param warn              sink for human-readable warnings naming the offending key,
     *                          value, and substituted default
     * @return a fully validated, immutable configuration snapshot
     */
    public static TbConfig load(ConfigSource source, Function<String, Boolean> materialValidator,
                                Consumer<String> warn) {
        FellSettings fell = new FellSettings(
                ConfigValidator.requireInRange("fell.max-blocks",
                        source.getInt("fell.max-blocks", 256), 1, 4096, 256, warn),
                ConfigValidator.requireInRange("fell.max-radius",
                        source.getInt("fell.max-radius", 8), 1, 64, 8, warn),
                ConfigValidator.requireInRange("fell.max-height",
                        source.getInt("fell.max-height", 32), 1, 256, 32, warn),
                source.getBoolean("fell.drop-leaves", true)
        );

        FuelSettings fuel = new FuelSettings(
                ConfigValidator.requireMaterial("fuel.material",
                        source.getString("fuel.material", DEFAULT_FUEL_MATERIAL),
                        materialValidator, DEFAULT_FUEL_MATERIAL, warn),
                ConfigValidator.requireInRange("fuel.amount",
                        source.getInt("fuel.amount", 1), 1, 64, 1, warn)
        );

        ExplosionSettings explosion = new ExplosionSettings(
                ConfigValidator.requireInRange("explosion.power",
                        source.getDouble("explosion.power", 2.0), 0.0, 10.0, 2.0, warn),
                source.getBoolean("explosion.block-damage", false),
                ConfigValidator.requireInRange("explosion.knockback-multiplier",
                        source.getDouble("explosion.knockback-multiplier", 1.0), 0.0, 5.0, 1.0, warn)
        );

        CoalSettings coal = new CoalSettings(
                source.getBoolean("coal.enabled", true),
                ConfigValidator.requireMaterial("coal.material",
                        source.getString("coal.material", DEFAULT_COAL_MATERIAL),
                        materialValidator, DEFAULT_COAL_MATERIAL, warn)
        );

        ScorchSettings scorch = new ScorchSettings(
                source.getBoolean("scorch.enabled", true),
                source.getBoolean("scorch.spread", false)
        );

        return new TbConfig(fell, fuel, explosion, coal, scorch);
    }
}
