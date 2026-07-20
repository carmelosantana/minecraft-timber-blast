/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * {@code /timberblast <give [player] | reload>}, with tab completion.
 *
 * <p>Every decision this command makes lives in {@link CommandResolver} as a pure
 * function; what is left here is dispatch, message formatting, and the two things that
 * genuinely need a running server: looking a player up by name and putting an item in
 * their inventory.
 *
 * <p><b>Bedrock safety.</b> All output is plain chat text. No forms, no inventory GUIs,
 * no chat-input prompts -- every one of those is either Java-only or breaks under Geyser,
 * and an operator on a Bedrock client must be able to run every subcommand here.
 */
public final class TimberBlastCommand implements CommandExecutor, TabCompleter {

    /**
     * Deliberately {@code java.util.logging} rather than {@code Bukkit.getLogger()}: the
     * failure this logs is a reload that threw, and reaching through {@code Bukkit} for a
     * logger makes the one path that must never itself blow up depend on a live server.
     */
    private static final Logger LOG = Logger.getLogger(TimberBlastCommand.class.getName());

    private final Supplier<ItemStack> axeFactory;
    private final Supplier<List<String>> reloadAction;

    /**
     * @param axeFactory   mints one Timber Blast axe, in production {@code TimberBlastItem::create}
     * @param reloadAction re-reads the configuration and returns the validation warnings it
     *                     produced, empty when the config was clean
     */
    public TimberBlastCommand(Supplier<ItemStack> axeFactory, Supplier<List<String>> reloadAction) {
        this.axeFactory = Objects.requireNonNull(axeFactory, "axeFactory");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        CommandResolver.Resolution resolved =
                CommandResolver.resolve(args, sender instanceof Player, sender::hasPermission);
        if (resolved.outcome().isError()) {
            error(sender, resolved.message());
            return true;
        }
        switch (resolved.outcome()) {
            case GIVE_SELF -> give(sender, (Player) sender);
            case GIVE_NAMED -> giveNamed(sender, resolved.targetName());
            case RELOAD -> reload(sender);
            default -> error(sender, CommandResolver.usage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return CommandResolver.complete(args, onlineNames(), sender::hasPermission);
    }

    private void giveNamed(CommandSender sender, String typed) {
        Player target = resolveTarget(typed);
        if (target == null) {
            error(sender, CommandResolver.noSuchPlayerMessage(typed, onlineNames()));
            return;
        }
        give(sender, target);
    }

    private void give(CommandSender sender, Player target) {
        ItemStack axe = axeFactory.get();
        if (axe == null) {
            // Only reachable when the item layer failed to wire on enable, which onEnable
            // already logged. Say so rather than throwing into the command dispatcher.
            error(sender, "The axe item is unavailable; see the server log. No item was given.");
            return;
        }
        giveOrDrop(target, axe);
        sender.sendMessage(Component.text("Gave a Timber Blast axe to " + target.getName() + ".")
                .color(NamedTextColor.GREEN));
    }

    private void reload(CommandSender sender) {
        List<String> warnings;
        try {
            warnings = reloadAction.get();
        } catch (Throwable t) {
            // A reload failure must leave the plugin running on its previous configuration,
            // not take the server down -- the same "config problems never break anything"
            // contract that governs startup.
            error(sender, "Reload failed; the previous configuration is still active. See the server log.");
            LOG.warning("TimberBlast: reload failed (" + t.getClass().getName()
                    + ": " + t.getMessage() + ").");
            return;
        }
        if (warnings == null || warnings.isEmpty()) {
            sender.sendMessage(Component.text("TimberBlast configuration reloaded.")
                    .color(NamedTextColor.GREEN));
            return;
        }
        sender.sendMessage(Component.text("TimberBlast configuration reloaded with "
                + warnings.size() + " warning(s); defaults were substituted:")
                .color(NamedTextColor.YELLOW));
        for (String warning : warnings) {
            sender.sendMessage(Component.text("  " + warning).color(NamedTextColor.YELLOW));
        }
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
    }

    /**
     * Adds the axe to the player's inventory, dropping at their feet whatever does not
     * fit. A full inventory must never silently swallow the item an operator just issued.
     */
    private static void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    /**
     * The online player matching {@code typed}, or {@code null}. Tries the exact name and
     * its Floodgate-prefixed form first, then a case-insensitive sweep -- which also covers
     * a server that has changed Floodgate's username prefix away from the default.
     */
    private static Player resolveTarget(String typed) {
        for (String candidate : CommandResolver.targetNameCandidates(typed)) {
            Player exact = Bukkit.getPlayerExact(candidate);
            if (exact != null) {
                return exact;
            }
        }
        for (String candidate : CommandResolver.targetNameCandidates(typed)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(candidate)) {
                    return online;
                }
            }
        }
        return null;
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }
}
