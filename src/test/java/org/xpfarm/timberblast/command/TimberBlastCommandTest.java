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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.testsupport.FakeBlocks;

import java.util.ArrayList;
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
 * a running server. The give path is not covered here -- it needs a real {@code ItemStack}
 * and a real inventory, so it defers to gate 7a.
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
