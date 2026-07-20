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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * The live-server {@link Wielder}. Like {@link BukkitFellWorld}, every method is a thin
 * delegation to a Bukkit call and is verified at gate 7a rather than in unit tests.
 */
public final class BukkitWielder implements Wielder {

    private final Player player;

    public BukkitWielder(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    @Override
    public boolean hasFuel(Material material, int amount) {
        // Inventory#contains(Material, int) sums across stacks, so a player holding two
        // stacks of eight satisfies a fuel cost of sixteen.
        return player.getInventory().contains(material, amount);
    }

    @Override
    public void consumeFuel(Material material, int amount) {
        player.getInventory().removeItem(new ItemStack(material, amount));
    }

    @Override
    public Vector position() {
        return player.getLocation().toVector();
    }

    @Override
    public void setVelocity(Vector velocity) {
        player.setVelocity(velocity);
    }

    @Override
    public void damageTool(int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        // ItemStack#damage honours Unbreaking and returns the resulting stack, which is
        // empty once the axe breaks -- that is the normal Bukkit durability path.
        inventory.setItemInMainHand(held.damage(amount, player));
    }
}
