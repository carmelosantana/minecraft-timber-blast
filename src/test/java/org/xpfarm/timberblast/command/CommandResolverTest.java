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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xpfarm.timberblast.command.CommandResolver.Outcome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive coverage of every rule {@code /timberblast} enforces.
 *
 * <p>The permission predicates here are argument-checking on purpose: a stub that answers
 * {@code true} to everything would pass whatever node the production code asked for,
 * including a typo'd or renamed one, so these tests would prove nothing about the gating
 * they exist to pin.
 */
class CommandResolverTest {

    private static final boolean PLAYER = true;
    private static final boolean CONSOLE = false;

    /** Grants exactly {@code node} and nothing else. */
    private static Predicate<String> holding(String node) {
        return asked -> node.equals(asked);
    }

    /** Grants nothing, and records what was asked for. */
    private static Predicate<String> denyingAndRecording(List<String> asked) {
        return node -> {
            asked.add(node);
            return false;
        };
    }

    private static final Predicate<String> ADMIN = holding(CommandResolver.ADMIN_PERMISSION);
    private static final Predicate<String> NOBODY = node -> false;

    @Nested
    @DisplayName("permission gating")
    class PermissionGating {

        @Test
        @DisplayName("give is refused without timberblast.admin")
        void giveRefusedWithoutAdmin() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give"}, PLAYER, NOBODY);
            assertEquals(Outcome.NO_PERMISSION, resolved.outcome());
        }

        @Test
        @DisplayName("reload is refused without timberblast.admin")
        void reloadRefusedWithoutAdmin() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"reload"}, PLAYER, NOBODY);
            assertEquals(Outcome.NO_PERMISSION, resolved.outcome());
        }

        @Test
        @DisplayName("give asks for exactly timberblast.admin, not a near miss")
        void giveAsksForExactNode() {
            List<String> asked = new ArrayList<>();
            CommandResolver.resolve(new String[]{"give"}, PLAYER, denyingAndRecording(asked));
            assertEquals(List.of("timberblast.admin"), asked);
        }

        @Test
        @DisplayName("reload asks for exactly timberblast.admin, not a near miss")
        void reloadAsksForExactNode() {
            List<String> asked = new ArrayList<>();
            CommandResolver.resolve(new String[]{"reload"}, PLAYER, denyingAndRecording(asked));
            assertEquals(List.of("timberblast.admin"), asked);
        }

        @Test
        @DisplayName("holding timberblast.use alone is not enough for either subcommand")
        void useNodeIsNotEnough() {
            Predicate<String> user = holding("timberblast.use");
            assertEquals(Outcome.NO_PERMISSION,
                    CommandResolver.resolve(new String[]{"give"}, PLAYER, user).outcome());
            assertEquals(Outcome.NO_PERMISSION,
                    CommandResolver.resolve(new String[]{"reload"}, PLAYER, user).outcome());
        }

        @Test
        @DisplayName("holding timberblast.admin permits both subcommands")
        void adminPermitsBoth() {
            assertEquals(Outcome.GIVE_SELF,
                    CommandResolver.resolve(new String[]{"give"}, PLAYER, ADMIN).outcome());
            assertEquals(Outcome.RELOAD,
                    CommandResolver.resolve(new String[]{"reload"}, PLAYER, ADMIN).outcome());
        }

        @Test
        @DisplayName("a permission refusal never leaks a target name")
        void refusalCarriesNoTarget() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give", "Notch"}, PLAYER, NOBODY);
            assertEquals(Outcome.NO_PERMISSION, resolved.outcome());
            assertNull(resolved.targetName());
        }
    }

    @Nested
    @DisplayName("argument counts")
    class ArgumentCounts {

        @Test
        @DisplayName("no arguments shows usage")
        void noArgumentsShowsUsage() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{}, PLAYER, ADMIN);
            assertEquals(Outcome.USAGE, resolved.outcome());
            assertEquals(CommandResolver.usage(), resolved.message());
        }

        @Test
        @DisplayName("a null argument array shows usage rather than throwing")
        void nullArgumentsShowUsage() {
            assertEquals(Outcome.USAGE, CommandResolver.resolve(null, PLAYER, ADMIN).outcome());
        }

        @Test
        @DisplayName("give with one name resolves that name")
        void giveWithNameResolvesIt() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give", "Notch"}, PLAYER, ADMIN);
            assertEquals(Outcome.GIVE_NAMED, resolved.outcome());
            assertEquals("Notch", resolved.targetName());
        }

        @Test
        @DisplayName("give with a surplus argument is rejected, not silently ignored")
        void giveRejectsSurplusArgument() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give", "Notch", "4"}, PLAYER, ADMIN);
            assertEquals(Outcome.TOO_MANY_ARGUMENTS, resolved.outcome());
        }

        @Test
        @DisplayName("reload with a surplus argument is rejected, not silently reloaded")
        void reloadRejectsSurplusArgument() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"reload", "now"}, PLAYER, ADMIN);
            assertEquals(Outcome.TOO_MANY_ARGUMENTS, resolved.outcome());
        }

        @Test
        @DisplayName("an unknown subcommand names the offending token")
        void unknownSubcommandNamesToken() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"giv"}, PLAYER, ADMIN);
            assertEquals(Outcome.UNKNOWN_SUBCOMMAND, resolved.outcome());
            assertTrue(resolved.message().contains("giv"),
                    "message should quote the typo: " + resolved.message());
        }

        @Test
        @DisplayName("subcommands are case-insensitive")
        void subcommandsAreCaseInsensitive() {
            assertEquals(Outcome.RELOAD,
                    CommandResolver.resolve(new String[]{"ReLoAd"}, PLAYER, ADMIN).outcome());
            assertEquals(Outcome.GIVE_SELF,
                    CommandResolver.resolve(new String[]{"GIVE"}, PLAYER, ADMIN).outcome());
        }
    }

    @Nested
    @DisplayName("the console sender")
    class ConsoleSender {

        @Test
        @DisplayName("give with no argument from the console is a usage error, not a crash")
        void consoleGiveWithoutTargetIsAnError() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give"}, CONSOLE, ADMIN);
            assertEquals(Outcome.CONSOLE_NEEDS_TARGET, resolved.outcome());
            assertTrue(resolved.message().contains("/timberblast give"),
                    "message should show the fix: " + resolved.message());
        }

        @Test
        @DisplayName("give with a name from the console is accepted")
        void consoleGiveWithTargetIsAccepted() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give", "Notch"}, CONSOLE, ADMIN);
            assertEquals(Outcome.GIVE_NAMED, resolved.outcome());
            assertEquals("Notch", resolved.targetName());
        }

        @Test
        @DisplayName("reload from the console is accepted")
        void consoleReloadIsAccepted() {
            assertEquals(Outcome.RELOAD,
                    CommandResolver.resolve(new String[]{"reload"}, CONSOLE, ADMIN).outcome());
        }

        @Test
        @DisplayName("give with no argument from a player targets the player")
        void playerGiveWithoutTargetTargetsSelf() {
            CommandResolver.Resolution resolved =
                    CommandResolver.resolve(new String[]{"give"}, PLAYER, ADMIN);
            assertEquals(Outcome.GIVE_SELF, resolved.outcome());
            assertNull(resolved.targetName());
        }
    }

    @Nested
    @DisplayName("outcome classification")
    class OutcomeClassification {

        @Test
        @DisplayName("every error outcome carries a message and every action outcome does not")
        void errorOutcomesCarryMessages() {
            record Case(String[] args, boolean isPlayer, Predicate<String> perms) {
            }
            List<Case> cases = List.of(
                    new Case(new String[]{}, PLAYER, ADMIN),
                    new Case(new String[]{"nope"}, PLAYER, ADMIN),
                    new Case(new String[]{"give", "a", "b"}, PLAYER, ADMIN),
                    new Case(new String[]{"reload", "x"}, PLAYER, ADMIN),
                    new Case(new String[]{"give"}, PLAYER, NOBODY),
                    new Case(new String[]{"give"}, CONSOLE, ADMIN),
                    new Case(new String[]{"give"}, PLAYER, ADMIN),
                    new Case(new String[]{"give", "Notch"}, PLAYER, ADMIN),
                    new Case(new String[]{"reload"}, PLAYER, ADMIN));

            for (Case c : cases) {
                CommandResolver.Resolution resolved =
                        CommandResolver.resolve(c.args(), c.isPlayer(), c.perms());
                if (resolved.outcome().isError()) {
                    assertNotNull(resolved.message(),
                            resolved.outcome() + " must carry a message");
                    assertFalse(resolved.message().isBlank(),
                            resolved.outcome() + " message must not be blank");
                } else {
                    assertNull(resolved.message(),
                            resolved.outcome() + " must not carry an error message");
                }
            }
        }

        @Test
        @DisplayName("the three action outcomes are not classified as errors")
        void actionOutcomesAreNotErrors() {
            assertFalse(Outcome.GIVE_SELF.isError());
            assertFalse(Outcome.GIVE_NAMED.isError());
            assertFalse(Outcome.RELOAD.isError());
        }

        @Test
        @DisplayName("all five failure outcomes are classified as errors")
        void failureOutcomesAreErrors() {
            assertTrue(Outcome.USAGE.isError());
            assertTrue(Outcome.UNKNOWN_SUBCOMMAND.isError());
            assertTrue(Outcome.TOO_MANY_ARGUMENTS.isError());
            assertTrue(Outcome.NO_PERMISSION.isError());
            assertTrue(Outcome.CONSOLE_NEEDS_TARGET.isError());
        }
    }

    @Nested
    @DisplayName("tab completion")
    class TabCompletion {

        private static final List<String> ONLINE = List.of("Notch", "notchy", ".acarm");

        @Test
        @DisplayName("an admin is offered both subcommands")
        void adminSeesBoth() {
            assertEquals(List.of("give", "reload"),
                    CommandResolver.complete(new String[]{""}, ONLINE, ADMIN));
        }

        @Test
        @DisplayName("a non-admin is offered nothing")
        void nonAdminSeesNothing() {
            assertEquals(List.of(), CommandResolver.complete(new String[]{""}, ONLINE, NOBODY));
        }

        @Test
        @DisplayName("the first argument filters by prefix, case-insensitively")
        void firstArgumentFiltersByPrefix() {
            assertEquals(List.of("reload"),
                    CommandResolver.complete(new String[]{"RE"}, ONLINE, ADMIN));
            assertEquals(List.of("give"),
                    CommandResolver.complete(new String[]{"g"}, ONLINE, ADMIN));
        }

        @Test
        @DisplayName("give's argument offers online player names")
        void giveOffersOnlineNames() {
            assertEquals(ONLINE, CommandResolver.complete(new String[]{"give", ""}, ONLINE, ADMIN));
        }

        @Test
        @DisplayName("give's argument filters names by prefix")
        void giveFiltersNamesByPrefix() {
            assertEquals(List.of("Notch", "notchy"),
                    CommandResolver.complete(new String[]{"give", "not"}, ONLINE, ADMIN));
        }

        @Test
        @DisplayName("a non-admin is offered no player names")
        void nonAdminSeesNoNames() {
            assertEquals(List.of(),
                    CommandResolver.complete(new String[]{"give", ""}, ONLINE, NOBODY));
        }

        @Test
        @DisplayName("reload offers no second argument")
        void reloadOffersNoSecondArgument() {
            assertEquals(List.of(),
                    CommandResolver.complete(new String[]{"reload", ""}, ONLINE, ADMIN));
        }

        @Test
        @DisplayName("nothing is offered past the second argument")
        void nothingPastSecondArgument() {
            assertEquals(List.of(),
                    CommandResolver.complete(new String[]{"give", "Notch", ""}, ONLINE, ADMIN));
        }

        @Test
        @DisplayName("a null or empty argument array completes to nothing rather than throwing")
        void nullArgsCompleteToNothing() {
            assertEquals(List.of(), CommandResolver.complete(null, ONLINE, ADMIN));
            assertEquals(List.of(), CommandResolver.complete(new String[]{}, ONLINE, ADMIN));
        }
    }

    @Nested
    @DisplayName("Bedrock target names")
    class BedrockTargetNames {

        @Test
        @DisplayName("a plain name is also tried with the Floodgate prefix")
        void plainNameTriesPrefixedForm() {
            assertEquals(List.of("acarm", ".acarm"),
                    CommandResolver.targetNameCandidates("acarm"));
        }

        @Test
        @DisplayName("an already-prefixed name is not double-prefixed")
        void prefixedNameIsNotDoublePrefixed() {
            assertEquals(List.of(".acarm"), CommandResolver.targetNameCandidates(".acarm"));
        }

        @Test
        @DisplayName("blank and null names yield no candidates")
        void blankNamesYieldNothing() {
            assertEquals(List.of(), CommandResolver.targetNameCandidates(null));
            assertEquals(List.of(), CommandResolver.targetNameCandidates("   "));
        }

        @Test
        @DisplayName("the offline-player message lists who is online")
        void offlineMessageListsOnlinePlayers() {
            String message = CommandResolver.noSuchPlayerMessage("acarm", List.of("Notch", ".acarm"));
            assertTrue(message.contains("acarm"), message);
            assertTrue(message.contains("Notch"), message);
            assertTrue(message.contains(".acarm"), message);
        }

        @Test
        @DisplayName("the offline-player message says so when nobody is online")
        void offlineMessageHandlesEmptyServer() {
            assertTrue(CommandResolver.noSuchPlayerMessage("acarm", List.of())
                    .contains("no players are online"));
            assertTrue(CommandResolver.noSuchPlayerMessage("acarm", null)
                    .contains("no players are online"));
        }
    }
}
