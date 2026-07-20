/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mints the Timber Blast axe and owns the single identity check for the whole plugin.
 *
 * <p>The axe is a plain {@link Material#DIAMOND_AXE} carrying a
 * {@code PersistentDataContainer} marker. <b>No custom model data, and the item is
 * never registered in Geyser's custom item mappings</b> -- Geyser issue #5848 breaks
 * interact events for mapping-registered custom-model-data items, which would silently
 * kill this plugin's core mechanic for Bedrock players. PDC is the only identity
 * signal; display name and lore are cosmetic and must never be matched on.
 *
 * <p>The keys are namespaced from the injected {@link Plugin}, so this class needs no
 * static state and can be constructed once on enable and shared with the listener and
 * the command.
 */
public final class TimberBlastItem {

    /** The byte stamped under {@link #key()} to mark an axe. */
    private static final byte MARKER = 1;

    private static final Component DISPLAY_NAME = Component.text("Timber Blast")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false);

    private static final List<Component> LORE = List.of(
            Component.text("Burns gunpowder to fell a whole tree in one swing.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

    private final Plugin plugin;
    private final NamespacedKey key;
    private final NamespacedKey recipeKey;

    /**
     * @param plugin the owning plugin; supplies the key namespace and the logger
     */
    public TimberBlastItem(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.key = new NamespacedKey(plugin, "axe");
        this.recipeKey = new NamespacedKey(plugin, "axe_recipe");
    }

    /** The PDC key marking an item as a Timber Blast axe. */
    public NamespacedKey key() {
        return key;
    }

    /** The crafting recipe's stable key. */
    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    /** Builds one Timber Blast axe. */
    public ItemStack create() {
        ItemStack stack = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(DISPLAY_NAME);
        meta.lore(LORE);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, MARKER);

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Whether {@code stack} is a Timber Blast axe.
     *
     * <p>This is the plugin's <b>only</b> identity check, and it runs on every
     * {@code BlockDamageEvent}, so it is deliberately cheap: {@code hasItemMeta} rejects
     * an ordinary tool without cloning any meta, and only a stack that carries some meta
     * pays for a container lookup. Null, air and meta-less stacks are false, never a
     * thrown exception -- an event handler must not blow up on an empty hand.
     */
    public boolean isTimberBlast(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return false;
        }
        Byte marker = stack.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return marker != null && marker == MARKER;
    }

    /** Builds the shaped recipe from {@link TimberBlastRecipeShape}. Registers nothing. */
    public ShapedRecipe buildRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create());
        recipe.shape(TimberBlastRecipeShape.PATTERN.toArray(new String[0]));
        for (Map.Entry<Character, Material> ingredient : TimberBlastRecipeShape.INGREDIENTS.entrySet()) {
            recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
        }
        return recipe;
    }

    /**
     * Registers the crafting recipe, replacing any previous registration under the same
     * key. Never throws: a recipe failure must not prevent the plugin from enabling.
     *
     * <h2>No recipe resend</h2>
     *
     * <p>An earlier version took a {@code resendRecipes} flag and passed {@code true} from
     * the reload path, to refresh every connected player's client-side recipe book. That was
     * built on a false premise. This recipe is a compile-time constant: the shape and the
     * ingredients are hardcoded in {@link TimberBlastRecipeShape}, this class reads no
     * configuration at all, and {@code config.yml} has no recipe keys -- only {@code fell.*},
     * {@code explosion.*}, {@code fuel.*} and {@code coal.*}. <b>A {@code /timberblast reload}
     * therefore cannot change the recipe.</b> Resending pushed a full recipe-book resync to
     * every connected player on every reload for a guaranteed no-op, so the flag is gone.
     *
     * @return {@code true} if the recipe is now registered
     */
    public boolean registerRecipe() {
        try {
            // Removed first unconditionally: Bukkit.addRecipe throws on a duplicate key and
            // recipes outlive a /timberblast reload.
            Bukkit.removeRecipe(recipeKey);
            return Bukkit.addRecipe(buildRecipe());
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register the crafting recipe ("
                    + t.getClass().getName() + ": " + t.getMessage()
                    + "). The axe is still obtainable via /timberblast give.");
            return false;
        }
    }

    /** Removes the crafting recipe. Never throws. */
    public void unregisterRecipe() {
        try {
            Bukkit.removeRecipe(recipeKey);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not remove the crafting recipe ("
                    + t.getClass().getName() + ": " + t.getMessage() + ").");
        }
    }
}
