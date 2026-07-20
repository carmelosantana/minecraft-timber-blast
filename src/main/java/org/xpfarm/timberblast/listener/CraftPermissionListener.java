/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.listener;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;

import java.util.Objects;

/**
 * Enforces {@code timberblast.craft}.
 *
 * <p>Clearing the result of {@link PrepareItemCraftEvent} is what actually stops the craft:
 * the player sees an empty output slot and there is nothing to take. Only this plugin's own
 * recipe is inspected, matched by its {@link NamespacedKey}, so no other recipe on the
 * server is affected.
 */
public final class CraftPermissionListener implements Listener {

    /** Permission required to craft the axe. */
    public static final String CRAFT_PERMISSION = "timberblast.craft";

    private final NamespacedKey recipeKey;

    /**
     * @param recipeKey the axe recipe's key, from {@code TimberBlastItem#recipeKey()}
     */
    public CraftPermissionListener(NamespacedKey recipeKey) {
        this.recipeKey = Objects.requireNonNull(recipeKey, "recipeKey");
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !recipeKey.equals(keyed.getKey())) {
            return;
        }
        HumanEntity viewer = event.getView().getPlayer();
        if (viewer.hasPermission(CRAFT_PERMISSION)) {
            return;
        }
        event.getInventory().setResult(null);
    }
}
