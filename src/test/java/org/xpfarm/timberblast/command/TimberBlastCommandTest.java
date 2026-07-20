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

import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.testsupport.FakeBlocks;
import org.xpfarm.timberblast.testsupport.FakeItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dispatch coverage for the Bukkit-facing half of the command.
 *
 * <p>{@link CommandResolverTest} pins the rules; this pins that {@code onCommand} actually
 * consults them and reports the result, using a JDK-proxy {@link CommandSender} rather than
 * a running server.
 *
 * <p>The give paths are covered here too, through
 * {@link TimberBlastCommand.ServerAccess}: which player receives the axe, whether a full
 * inventory drops it, and what an unmatched name reports are all decisions, and they were
 * previously unreachable only because they were written against {@code Bukkit}'s statics.
 * What genuinely still defers to gate 7a is narrower than it looks -- see the report -- and
 * amounts to {@code ServerAccess.BUKKIT}'s four one-line bodies plus
 * {@code TimberBlastItem.create()}, which needs the server's item registry.
 */
class TimberBlastCommandTest {

    /** A sender that is not a {@link org.bukkit.entity.Player}, recording what it is told. */
    private static CommandSender console(List<String> said, boolean admin) {
        return FakeBlocks.stub(CommandSender.class, "the console", Map.of(
                "hasPermission", FakeBlocks.onlyFor(CommandResolver.ADMIN_PERMISSION, admin),
                "sendMessage", (FakeBlocks.Answer) args -> {
                    // Every message this command sends is a single Component.text(String),
                    // so its content is the whole line -- no serializer needed.
                    said.add(((TextComponent) args[0]).content());
                    return null;
                }));
    }

    private static boolean run(CommandSender sender, TimberBlastCommand command, String... args) {
        return command.onCommand(sender, null, "timberblast", args);
    }

    private static TimberBlastCommand commandWith(java.util.function.Supplier<List<String>> reload) {
        return new TimberBlastCommand(() -> null, reload);
    }

    @Test
    @DisplayName("reload runs the reload action and reports success")
    void reloadReportsSuccess() {
        AtomicInteger reloads = new AtomicInteger();
        List<String> said = new ArrayList<>();
        TimberBlastCommand command = commandWith(() -> {
            reloads.incrementAndGet();
            return List.of();
        });

        assertTrue(run(console(said, true), command, "reload"));

        assertEquals(1, reloads.get());
        assertEquals(1, said.size());
        assertTrue(said.get(0).contains("reloaded"), said.get(0));
    }

    @Test
    @DisplayName("reload reports the validation warnings it produced")
    void reloadReportsWarnings() {
        List<String> said = new ArrayList<>();
        TimberBlastCommand command =
                commandWith(() -> List.of("fell.max-blocks was 99999", "fuel.material was CHEESE"));

        run(console(said, true), command, "reload");

        String all = String.join("\n", said);
        assertTrue(all.contains("2 warning"), all);
        assertTrue(all.contains("fell.max-blocks was 99999"), all);
        assertTrue(all.contains("fuel.material was CHEESE"), all);
    }

    @Test
    @DisplayName("a reload without permission never runs the reload action")
    void reloadWithoutPermissionDoesNothing() {
        AtomicInteger reloads = new AtomicInteger();
        List<String> said = new ArrayList<>();
        TimberBlastCommand command = commandWith(() -> {
            reloads.incrementAndGet();
            return List.of();
        });

        run(console(said, false), command, "reload");

        assertEquals(0, reloads.get(), "the config must not be re-read for an unauthorized sender");
        assertEquals(1, said.size());
        assertTrue(said.get(0).contains("permission"), said.get(0));
    }

    @Test
    @DisplayName("give with no argument from the console explains itself and gives nothing")
    void consoleGiveWithoutTargetExplains() {
        AtomicInteger axes = new AtomicInteger();
        List<String> said = new ArrayList<>();
        TimberBlastCommand command = new TimberBlastCommand(() -> {
            axes.incrementAndGet();
            return null;
        }, List::of);

        run(console(said, true), command, "give");

        assertEquals(0, axes.get(), "no axe may be minted for a sender with no inventory");
        assertEquals(1, said.size());
        assertTrue(said.get(0).contains("/timberblast give"), said.get(0));
    }

    @Test
    @DisplayName("no arguments shows usage")
    void noArgumentsShowsUsage() {
        List<String> said = new ArrayList<>();
        run(console(said, true), commandWith(List::of));
        assertEquals(List.of(CommandResolver.usage()), said);
    }

    @Test
    @DisplayName("an unknown subcommand is reported, never thrown")
    void unknownSubcommandIsReported() {
        List<String> said = new ArrayList<>();
        run(console(said, true), commandWith(List::of), "explode");
        assertEquals(1, said.size());
        assertTrue(said.get(0).contains("explode"), said.get(0));
    }

    // ------------------------------------------------------------------ the give paths
    //
    // The layer between CommandResolver's outcome and what actually happens. It had no
    // coverage at all, and it is exactly where this project's recurring defect lives: a
    // thoroughly tested pure resolver with an untested translation layer beneath it.
    // Rewriting `case GIVE_NAMED -> giveNamed(...)` to `give(sender, (Player) sender)` --
    // /tb give Notch handing the axe to whoever typed it, and a ClassCastException from the
    // console -- used to build green.

    /** A recording {@link TimberBlastCommand.ServerAccess} over a fixed set of online players. */
    private static final class FakeServer implements TimberBlastCommand.ServerAccess {

        private final Map<String, Player> online = new LinkedHashMap<>();
        private final List<String> added = new ArrayList<>();
        private final List<String> dropped = new ArrayList<>();

        /** What {@link #addToInventory} reports as not fitting, by player name. */
        private final Map<String, Map<Integer, ItemStack>> leftover = new LinkedHashMap<>();

        FakeServer with(String name, Player player) {
            online.put(name, player);
            return this;
        }

        @Override
        public Player playerExact(String name) {
            return online.get(name);
        }

        @Override
        public List<String> onlineNames() {
            return List.copyOf(online.keySet());
        }

        @Override
        public Map<Integer, ItemStack> addToInventory(Player player, ItemStack stack) {
            added.add(player.getName() + " <- " + stack);
            return leftover.getOrDefault(player.getName(), Map.of());
        }

        @Override
        public void dropAtFeet(Player player, ItemStack stack) {
            dropped.add(player.getName() + " <- " + stack);
        }
    }

    /** A sender that *is* a {@link Player}, recording what it is told. */
    private static Player player(String name, List<String> said, boolean admin) {
        return FakeBlocks.stub(Player.class, name, Map.of(
                "getName", name,
                "hasPermission", FakeBlocks.onlyFor(CommandResolver.ADMIN_PERMISSION, admin),
                "sendMessage", (FakeBlocks.Answer) args -> {
                    said.add(((TextComponent) args[0]).content());
                    return null;
                }));
    }

    @Test
    @DisplayName("give with no argument puts the axe in the sending player's own inventory")
    void giveSelfDeliversToTheSender() {
        List<String> said = new ArrayList<>();
        ItemStack axe = new FakeItemStack("an axe");
        Player alice = player("Alice", said, true);
        FakeServer server = new FakeServer().with("Alice", alice);
        TimberBlastCommand command = new TimberBlastCommand(() -> axe, List::of, server);

        assertTrue(command.onCommand(alice, null, "timberblast", new String[]{"give"}));

        assertEquals(List.of("Alice <- an axe"), server.added);
        assertEquals(List.of(), server.dropped);
        assertTrue(said.get(0).contains("Alice"), said.get(0));
    }

    @Test
    @DisplayName("give <player> delivers to the named player, not to the sender")
    void giveNamedDeliversToTheNamedPlayer() {
        List<String> said = new ArrayList<>();
        ItemStack axe = new FakeItemStack("an axe");
        Player alice = player("Alice", said, true);
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Alice", alice).with("Bob", bob);
        TimberBlastCommand command = new TimberBlastCommand(() -> axe, List::of, server);

        command.onCommand(alice, null, "timberblast", new String[]{"give", "Bob"});

        assertEquals(List.of("Bob <- an axe"), server.added,
                "the axe must go to the named player; giving it to the sender is the "
                        + "mutation this test exists for");
        assertTrue(said.get(0).contains("Bob"), said.get(0));
    }

    @Test
    @DisplayName("give <player> from the console reaches the named player")
    void giveNamedWorksFromTheConsole() {
        List<String> said = new ArrayList<>();
        ItemStack axe = new FakeItemStack("an axe");
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob);
        TimberBlastCommand command = new TimberBlastCommand(() -> axe, List::of, server);

        // A console sender is not a Player: casting it to one is a ClassCastException in the
        // command dispatcher, which is what the GIVE_NAMED mutation produced.
        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "Bob"});

        assertEquals(List.of("Bob <- an axe"), server.added);
        assertTrue(said.get(0).contains("Bob"), said.get(0));
    }

    @Test
    @DisplayName("a Bedrock player is found under their Floodgate-prefixed name")
    void giveNamedFindsAPrefixedBedrockName() {
        List<String> said = new ArrayList<>();
        ItemStack axe = new FakeItemStack("an axe");
        Player acarm = player(".acarm", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with(".acarm", acarm);
        TimberBlastCommand command = new TimberBlastCommand(() -> axe, List::of, server);

        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "acarm"});

        assertEquals(List.of(".acarm <- an axe"), server.added,
                "an operator types the name the Bedrock player knows, without the prefix");
    }

    @Test
    @DisplayName("an unmatched name gives nothing and names who is online")
    void giveNamedReportsAnUnknownPlayer() {
        List<String> said = new ArrayList<>();
        AtomicInteger axes = new AtomicInteger();
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob);
        TimberBlastCommand command = new TimberBlastCommand(() -> {
            axes.incrementAndGet();
            return new FakeItemStack("an axe");
        }, List::of, server);

        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "Notch"});

        assertEquals(List.of(), server.added);
        assertEquals(0, axes.get(), "no axe may be minted for a player who is not there");
        assertTrue(said.get(0).contains("Notch"), said.get(0));
        assertTrue(said.get(0).contains("Bob"), said.get(0));
    }

    @Test
    @DisplayName("a full inventory drops the axe at the player's feet rather than losing it")
    void aFullInventoryDropsTheAxe() {
        List<String> said = new ArrayList<>();
        ItemStack axe = new FakeItemStack("an axe");
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob);
        // What a full inventory reports back from Inventory#addItem: the stack, unplaced.
        server.leftover.put("Bob", Map.of(0, axe));
        TimberBlastCommand command = new TimberBlastCommand(() -> axe, List::of, server);

        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "Bob"});

        assertEquals(List.of("Bob <- an axe"), server.added, "the add is still attempted first");
        assertEquals(List.of("Bob <- an axe"), server.dropped,
                "brief requirement 3: a full inventory must never silently swallow the axe");
    }

    @Test
    @DisplayName("the drop is driven by what did not fit, not run unconditionally")
    void nothingIsDroppedWhenTheAxeFits() {
        List<String> said = new ArrayList<>();
        ItemStack axe = new FakeItemStack("an axe");
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob);
        TimberBlastCommand command = new TimberBlastCommand(() -> axe, List::of, server);

        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "Bob"});

        assertEquals(List.of(), server.dropped,
                "dropping unconditionally would litter the ground on every successful give");
    }

    @Test
    @DisplayName("every leftover stack is dropped, not just the first")
    void everyLeftoverStackIsDropped() {
        List<String> said = new ArrayList<>();
        ItemStack first = new FakeItemStack("axe one");
        ItemStack second = new FakeItemStack("axe two");
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob);
        Map<Integer, ItemStack> leftover = new LinkedHashMap<>();
        leftover.put(0, first);
        leftover.put(1, second);
        server.leftover.put("Bob", leftover);
        TimberBlastCommand command =
                new TimberBlastCommand(() -> first, List::of, server);

        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "Bob"});

        assertEquals(List.of("Bob <- axe one", "Bob <- axe two"), server.dropped,
                "the leftover map drives the loop; dropping only values().iterator().next() "
                        + "would lose the rest");
    }

    @Test
    @DisplayName("an unavailable item layer explains itself instead of throwing")
    void anAbsentAxeIsReported() {
        List<String> said = new ArrayList<>();
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob);
        TimberBlastCommand command = new TimberBlastCommand(() -> null, List::of, server);

        command.onCommand(console(said, true), null, "timberblast", new String[]{"give", "Bob"});

        assertEquals(List.of(), server.added);
        assertTrue(said.get(0).contains("unavailable"), said.get(0));
    }

    @Test
    @DisplayName("tab completion offers the online player names for give")
    void tabCompletionOffersOnlineNames() {
        List<String> said = new ArrayList<>();
        Player bob = player("Bob", new ArrayList<>(), false);
        FakeServer server = new FakeServer().with("Bob", bob).with("Bella", bob);
        TimberBlastCommand command = new TimberBlastCommand(() -> null, List::of, server);

        assertEquals(List.of("Bob", "Bella"), command.onTabComplete(
                console(said, true), null, "timberblast", new String[]{"give", "B"}));
    }

    @Test
    @DisplayName("a throwing reload is reported as a failure, not propagated")
    void throwingReloadIsReported() {
        List<String> said = new ArrayList<>();
        TimberBlastCommand command = commandWith(() -> {
            throw new IllegalStateException("boom");
        });

        assertTrue(run(console(said, true), command, "reload"));

        assertEquals(1, said.size());
        assertTrue(said.get(0).contains("previous configuration is still active"), said.get(0));
    }
}
