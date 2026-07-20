/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.fell;

import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** A {@link Wielder} carrying a fixed amount of one material, recording what happens to it. */
final class RecordingWielder implements Wielder {

    private final Material carried;
    private final int carriedAmount;
    private final Vector position;

    /** Every {@code consumeFuel} call, as {@code "MATERIAL xN"}. */
    final List<String> consumed = new ArrayList<>();

    Vector velocity;
    int toolDamage;

    RecordingWielder(Material carried, int carriedAmount, Vector position) {
        this.carried = carried;
        this.carriedAmount = carriedAmount;
        this.position = position;
    }

    @Override
    public boolean hasFuel(Material material, int amount) {
        return material == carried && carriedAmount >= amount;
    }

    @Override
    public void consumeFuel(Material material, int amount) {
        consumed.add(material + " x" + amount);
    }

    @Override
    public Vector position() {
        return position.clone();
    }

    @Override
    public void setVelocity(Vector velocity) {
        this.velocity = velocity;
    }

    @Override
    public void damageTool(int amount) {
        toolDamage += amount;
    }
}
