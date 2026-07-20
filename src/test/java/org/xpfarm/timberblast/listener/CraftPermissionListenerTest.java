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

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.testsupport.FakeBlocks;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fires real {@link PrepareItemCraftEvent}s at the listener. The recipe is matched by key,
 * so the important assertions are that someone else's recipe survives and that the axe's
 * result is cleared for a player without {@code timberblast.craft}.
 */
class CraftPermissionListenerTest {

    private static final NamespacedKey AXE_RECIPE = new NamespacedKey("timberblast", "axe_recipe");
    private static final NamespacedKey OTHER_RECIPE = new NamespacedKey("minecraft", "diamond_axe");

    /** Records the arguments of every {@code setResult} call on the crafting inventory. */
    private final List<Object> results = new ArrayList<>();

    private CraftingInventory inventoryFor(Recipe recipe) {
        return (CraftingInventory) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{CraftingInventory.class}, (self, method, args) -> switch (method.getName()) {
                    case "getRecipe" -> recipe;
                    case "setResult" -> {
                        results.add(args[0]);
                        yield null;
                    }
                    case "toString" -> "the crafting grid";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A keyed recipe that is nothing but its key -- that is all the listener may look at. */
    private static Recipe keyedRecipe(NamespacedKey key) {
        return (Recipe) Proxy.newProxyInstance(CraftPermissionListenerTest.class.getClassLoader(),
                new Class<?>[]{Recipe.class, org.bukkit.Keyed.class}, (self, method, args) -> switch (method.getName()) {
                    case "getKey" -> key;
                    case "toString" -> "recipe " + key;
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PrepareItemCraftEvent prepare(NamespacedKey recipeKey, boolean permitted) {
        CraftingInventory inventory = inventoryFor(keyedRecipe(recipeKey));
        // The node is a literal, not a reference to the constant: the stub answers true only
        // for this exact string, so a renamed or typo'd node fails the build.
        Player viewer = FakeBlocks.stub(Player.class, "the crafter",
                Map.of("hasPermission", FakeBlocks.onlyFor("timberblast.craft", permitted)));
        InventoryView view = FakeBlocks.stub(InventoryView.class, "the crafting view",
                Map.of("getPlayer", viewer));
        return new PrepareItemCraftEvent(inventory, view, false);
    }

    private CraftPermissionListener listener() {
        return new CraftPermissionListener(AXE_RECIPE);
    }

    @Test
    void aCrafterWithoutPermissionGetsAnEmptyResultSlot() {
        listener().onPrepareItemCraft(prepare(AXE_RECIPE, false));

        assertEquals(1, results.size(), "the result must be cleared exactly once");
        assertEquals(java.util.Collections.singletonList(null), results);
    }

    @Test
    void aCrafterWithPermissionKeepsTheirAxe() {
        listener().onPrepareItemCraft(prepare(AXE_RECIPE, true));

        assertTrue(results.isEmpty());
    }

    @Test
    void someoneElsesRecipeIsNeverTouched() {
        listener().onPrepareItemCraft(prepare(OTHER_RECIPE, false));

        assertTrue(results.isEmpty(), "only this plugin's recipe may be gated");
    }

    @Test
    void theCraftPermissionIsTheNodeDeclaredInPluginYml() {
        assertEquals("timberblast.craft", CraftPermissionListener.CRAFT_PERMISSION,
                "plugin.yml declares this node; a rename here would gate on nothing");
    }

    @Test
    void anUnkeyedRecipeIsIgnoredRatherThanCrashing() {
        Recipe unkeyed = (Recipe) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Recipe.class}, (self, method, args) -> switch (method.getName()) {
                    case "toString" -> "an unkeyed recipe";
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        InventoryView view = FakeBlocks.stub(InventoryView.class, "the crafting view", Map.of());

        listener().onPrepareItemCraft(new PrepareItemCraftEvent(inventoryFor(unkeyed), view, false));

        assertTrue(results.isEmpty());
    }
}
