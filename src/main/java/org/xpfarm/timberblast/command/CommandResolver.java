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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Every decision {@code /timberblast} makes, as pure functions over {@code String}s.
 *
 * <p>This class imports nothing from {@code org.bukkit}. Argument parsing, permission
 * gating, the console-has-no-inventory case, and tab completion all resolve to an
 * {@link Outcome} here, and {@link TimberBlastCommand} is left with dispatch and message
 * formatting. That split is what makes the command's rules testable without a server --
 * the same seam {@code TreeScanner} and {@code TbConfig} use.
 *
 * <p>Parsing rejects surplus arguments rather than ignoring them: {@code /tb reload now}
 * fails instead of quietly reloading, because silently dropping a token hides typos in
 * exactly the commands an operator runs least often.
 */
public final class CommandResolver {

    /** The permission both subcommands require. */
    public static final String ADMIN_PERMISSION = "timberblast.admin";

    private CommandResolver() {
    }

    /** The two subcommands. */
    public enum Sub {
        GIVE("give"),
        RELOAD("reload");

        private final String token;

        Sub(String token) {
            this.token = token;
        }

        /** The lowercase token a player types. */
        public String token() {
            return token;
        }
    }

    /** What the command should do, or why it should not. */
    public enum Outcome {

        /** No arguments at all: show usage. */
        USAGE,

        /** First argument is not a known subcommand. */
        UNKNOWN_SUBCOMMAND,

        /** A known subcommand with more arguments than it accepts. */
        TOO_MANY_ARGUMENTS,

        /** Sender lacks the permission the resolved subcommand requires. */
        NO_PERMISSION,

        /** {@code give} with no name from a sender that has no inventory. */
        CONSOLE_NEEDS_TARGET,

        /** {@code give} with no name from a player: the sender is the target. */
        GIVE_SELF,

        /** {@code give <name>}: {@link Resolution#targetName()} holds the name to resolve. */
        GIVE_NAMED,

        /** {@code reload}. */
        RELOAD;

        /** Whether this outcome is an error to report rather than an action to run. */
        public boolean isError() {
            return this == USAGE || this == UNKNOWN_SUBCOMMAND
                    || this == TOO_MANY_ARGUMENTS || this == NO_PERMISSION
                    || this == CONSOLE_NEEDS_TARGET;
        }
    }

    /**
     * A resolved invocation.
     *
     * @param outcome    what to do, or why not
     * @param targetName the typed {@code give} target, only for {@link Outcome#GIVE_NAMED}
     * @param message    the message to show, for every {@linkplain Outcome#isError() error}
     *                   outcome and {@code null} otherwise
     */
    public record Resolution(Outcome outcome, String targetName, String message) {
    }

    /** The usage line. */
    public static String usage() {
        return "Usage: /timberblast <give [player] | reload>";
    }

    /** The permission {@code sub} requires. */
    public static String permissionFor(Sub sub) {
        Objects.requireNonNull(sub, "sub");
        return switch (sub) {
            case GIVE, RELOAD -> ADMIN_PERMISSION;
        };
    }

    /** Resolves a typed token to a subcommand, case-insensitively. */
    public static Optional<Sub> subOf(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        for (Sub sub : Sub.values()) {
            if (sub.token().equals(normalized)) {
                return Optional.of(sub);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a raw invocation. Never throws.
     *
     * <p>Order matters: the subcommand is resolved first so that an unauthorized sender
     * typing a typo is told about the typo, then permission is checked against the
     * resolved subcommand's own node so a renamed node fails the tests rather than
     * silently opening the command up.
     *
     * @param senderIsPlayer whether the sender has an inventory to receive an axe
     * @param hasPermission  the sender's permission check
     */
    public static Resolution resolve(String[] args, boolean senderIsPlayer,
                                     Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (args == null || args.length == 0) {
            return error(Outcome.USAGE, usage());
        }
        Optional<Sub> resolved = subOf(args[0]);
        if (resolved.isEmpty()) {
            return error(Outcome.UNKNOWN_SUBCOMMAND,
                    "Unknown subcommand '" + args[0] + "'. " + usage());
        }
        Sub sub = resolved.get();
        if (!hasPermission.test(permissionFor(sub))) {
            return error(Outcome.NO_PERMISSION, "You don't have permission to do that.");
        }
        return switch (sub) {
            case GIVE -> resolveGive(args, senderIsPlayer);
            case RELOAD -> args.length > 1
                    ? error(Outcome.TOO_MANY_ARGUMENTS, "'reload' takes no arguments. " + usage())
                    : new Resolution(Outcome.RELOAD, null, null);
        };
    }

    private static Resolution resolveGive(String[] args, boolean senderIsPlayer) {
        if (args.length > 2) {
            return error(Outcome.TOO_MANY_ARGUMENTS, "Too many arguments. " + usage());
        }
        if (args.length == 2) {
            return new Resolution(Outcome.GIVE_NAMED, args[1], null);
        }
        if (!senderIsPlayer) {
            return error(Outcome.CONSOLE_NEEDS_TARGET,
                    "Console has no inventory; name a player, e.g. /timberblast give <player>.");
        }
        return new Resolution(Outcome.GIVE_SELF, null, null);
    }

    private static Resolution error(Outcome outcome, String message) {
        return new Resolution(outcome, null, message);
    }

    /**
     * The subcommand tokens a sender may run, in declaration order.
     *
     * <p>Tab completion must not advertise a subcommand the sender cannot use.
     */
    public static List<String> allowedSubcommandTokens(Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        List<String> tokens = new ArrayList<>();
        for (Sub sub : Sub.values()) {
            if (hasPermission.test(permissionFor(sub))) {
                tokens.add(sub.token());
            }
        }
        return List.copyOf(tokens);
    }

    /**
     * Completions for a partially typed argument array.
     *
     * @param args          the arguments so far; the last element is the partial token
     * @param onlineNames   usernames currently online, offered for {@code give}'s argument
     * @param hasPermission the sender's permission check
     * @return matching completions, never {@code null}
     */
    public static List<String> complete(String[] args, List<String> onlineNames,
                                        Predicate<String> hasPermission) {
        Objects.requireNonNull(hasPermission, "hasPermission");
        if (args == null || args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            return filterByPrefix(allowedSubcommandTokens(hasPermission), args[0]);
        }
        if (args.length == 2 && subOf(args[0]).orElse(null) == Sub.GIVE
                && hasPermission.test(permissionFor(Sub.GIVE))) {
            return filterByPrefix(onlineNames == null ? List.of() : onlineNames, args[1]);
        }
        return List.of();
    }

    /**
     * The message shown when no online player matches {@code typed}.
     *
     * <p>Naming who <em>is</em> online turns a dead end into a usable correction: it is
     * the only way an operator discovers a Floodgate-prefixed Bedrock username without
     * knowing Floodgate exists.
     */
    public static String noSuchPlayerMessage(String typed, List<String> onlineNames) {
        if (onlineNames == null || onlineNames.isEmpty()) {
            return "No player matches '" + typed + "'; no players are online.";
        }
        return "No player matches '" + typed + "'. Online: " + String.join(", ", onlineNames);
    }

    /** The Floodgate default prefix on a Bedrock account's Java-side username. */
    private static final String FLOODGATE_PREFIX = ".";

    /**
     * The usernames to try, in order, for a typed {@code give} target.
     *
     * <p>Floodgate joins a Bedrock account under a prefixed username -- {@code .acarm} for
     * a player who thinks of themselves as {@code acarm} -- and an exact-match lookup would
     * report "not online" for a player standing in front of the operator. Trying the
     * prefixed form as well costs one lookup and removes the single most confusing failure
     * this command has.
     *
     * @return candidate usernames, most likely first; empty for a null or blank input
     */
    public static List<String> targetNameCandidates(String typed) {
        if (typed == null) {
            return List.of();
        }
        String trimmed = typed.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.startsWith(FLOODGATE_PREFIX)) {
            return List.of(trimmed);
        }
        return List.of(trimmed, FLOODGATE_PREFIX + trimmed);
    }

    private static List<String> filterByPrefix(List<String> candidates, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(candidate);
            }
        }
        return List.copyOf(matches);
    }
}
