# New or Edited Plugin Checklist

Copy this file for one plugin and replace every `<...>` field. Leave an unchecked box with a short explanation when a gate is not complete; do not silently remove inapplicable checks.

- Plugin name: `Timber Blast`
- Slug: `timber-blast`
- Repository: `carmelosantana/minecraft-timber-blast`
- Owner: `Carmelo Santana`
- Target version: `1.0.0`
- Paper version: `26.1.2 build 74`
- Java version: `25`
- Updater destination: `timber-blast.jar`
- External services: `none`
- Status: `active`
- Autonomy: `autonomous`

## 1. Scope

- [x] Status is explicitly recorded as active, experimental, or excluded.
- [x] Purpose, commands, events, permissions, configuration, persistence, and acceptance checks are defined.
- [x] Known limitations and any intentionally withheld gates are recorded.

### Naming chain

Established at gate 1; `minecraft-plugin-scaffold` verifies consistency at gate 3.

| Link | Value |
| --- | --- |
| Slug | `timber-blast` |
| Repository | `carmelosantana/minecraft-timber-blast` |
| Maven group | `org.xpfarm` |
| Maven artifactId | `timber-blast` |
| Java package | `org.xpfarm.timberblast` |
| Releasable JAR | `timber-blast-1.0.0.jar` (shaded) |
| Updater destination | `timber-blast.jar` |
| `plugin.yml` name | `TimberBlast` |

### Player-facing purpose

A craftable axe that fells an entire tree in one swing. Hitting a log detonates a
blast that drops every connected log, leaf, sapling, and stick to the ground and
launches the player backward; the struck log itself is charred into a single piece
of coal. Hitting anything other than a log — leaves included — does nothing special;
the axe simply behaves like a normal axe. Each fell burns one gunpowder from the
player's inventory, so the axe stays fuelled by creeper farming rather than being a
permanent upgrade.

### Commands

| Command | Arguments | Who | Purpose |
| --- | --- | --- | --- |
| `/timberblast give` | `[player]` | op | Grants the axe. Defaults to sender when `[player]` is omitted. Required for gate 7a runtime verification. |
| `/timberblast reload` | none | op | Re-reads `config.yml` and re-validates. |

Alias: `/tb`. No player-facing commands — the axe is obtained by crafting.

### Events

| Event | Direction | Why |
| --- | --- | --- |
| `BlockDamageEvent` | listen | Primary trigger. Fires when a player starts breaking a block, before break completion, and carries `getItemInHand()` and `getInstaBreak()`. Confirmed to fire for Bedrock players through Geyser (see research findings below). |
| `BlockBreakEvent` | fire | One cancellable event per log in the fell set, so any protection plugin can veto individual blocks without this plugin taking a hard dependency on one. Vetoed blocks are dropped from the set and left standing. |
| `EntityDamageEvent` | listen | Sets the wielder's damage to `0` (never cancels) so the wielder keeps explosion knockback but loses no health. |

### Permissions

| Node | Default | Gates |
| --- | --- | --- |
| `timberblast.use` | `true` | Triggering a fell with the axe. |
| `timberblast.craft` | `true` | Crafting the axe. |
| `timberblast.admin` | `op` | `/timberblast give` and `/timberblast reload`. |

### Configuration

| Key | Type | Default | Validation |
| --- | --- | --- | --- |
| `fell.max-blocks` | int | `256` | 1–4096; clamps to bound on violation |
| `fell.max-radius` | int | `8` | 1–64; horizontal distance from struck trunk |
| `fell.max-height` | int | `32` | 1–256; vertical distance from struck trunk |
| `fell.drop-leaves` | bool | `true` | — |
| `fell.max-leaves` | int | `256` | 0–4096; caps leaves broken per fell |
| `fuel.material` | string | `GUNPOWDER` | must resolve to a valid `Material` |
| `fuel.amount` | int | `1` | 1–64 |
| `explosion.power` | float | `2.0` | 0.0–10.0 |
| `explosion.block-damage` | bool | `false` | — |
| `explosion.knockback-multiplier` | double | `1.0` | 0.0–5.0 |
| `coal.enabled` | bool | `true` | — |
| `coal.material` | string | `COAL` | must resolve to a valid `Material` |

Invalid values log a warning and fall back to the default; a malformed config never
prevents the plugin from enabling.

### Persistence

No database and no flat-file state. The axe is identified by a
`PersistentDataContainer` key (`timberblast:axe`, `BYTE`) written into the
`ItemStack` meta, so identity travels with the item through chests, hoppers, death,
and restarts with no server-side bookkeeping. The plugin keeps no in-memory state
beyond loaded configuration.

The namespace is `timberblast`, not `timber-blast`: the key is built with
`new NamespacedKey(plugin, "axe")`, and Bukkit derives the namespace from the
`plugin.yml` name (`TimberBlast`) lowercased. An earlier draft of this checklist said
`timber-blast:axe`, which the plugin-instance constructor cannot produce. Settled at
gate 4 in favour of the idiomatic form. Nothing has shipped, so no migration is needed —
but this value is baked into every crafted axe and must not change after v1.0.0
without a PDC migration.

### Dependencies

None — hard or soft. No load-order requirements. Protection-plugin compatibility is
achieved by firing cancellable `BlockBreakEvent`s rather than by depending on any
specific protection plugin, so it works with whatever is installed or none at all.

### External integrations

`none`. No Ollama, Umami, or other outside-service calls. Gate 5 is satisfied
trivially and its boxes should be ticked with that explanation rather than left blank.

### Acceptance checks

1. Crafting the recipe yields an axe whose meta carries the `timberblast:axe` PDC key.
   Verify the **write and read paths together**, not just marker presence: `create()`
   writes through `meta.getPersistentDataContainer()` while `isTimberBlast` reads
   through `stack.getPersistentDataContainer()` — two different API surfaces onto the
   same `custom_data` component. Their agreement is the thing under test.
2. Striking a log with the axe, holding ≥1 gunpowder, removes every connected log within bounds and drops them as items.
3. Exactly one gunpowder is consumed per successful fell.
4. With zero gunpowder, striking a log performs normal vanilla axe behavior — no fell, no blast, no coal.
5. The struck log yields exactly one coal; all other logs yield their normal log drops.
6. Attached leaves are removed and roll their vanilla drop tables (saplings, sticks, apples).
7. The blast damages no blocks at any configured power.
8. The wielder is knocked back and loses no health; nearby mobs take blast damage.
9. Striking a leaf block does nothing special: no fell, no blast, no fire, no gunpowder consumed — identical to a vanilla axe.
10. A tree exceeding `fell.max-blocks` stops at the cap and leaves the remainder standing.
11. A wood structure wider than `fell.max-radius` is not fully consumed.
12. A `BlockBreakEvent` cancelled by another plugin leaves that specific log standing while the rest of the fell proceeds.
12a. Leaves are removed through the same cancellable break path as logs — a fell inside a protected claim strips no canopy the player could not break by hand.
12b. A fell in which every log is vetoed costs the player nothing: no gunpowder consumed, no blast, no knockback, no durability loss.
13. `/timberblast give` grants a functioning axe; `/timberblast reload` applies changed config without restart.
14. Both admin commands are refused without `timberblast.admin`.
15. **Bedrock/Geyser:** a Bedrock player crafts, receives, and swings the axe, and the fell triggers identically to Java. This check specifically confirms PDC identity survives the Geyser path — see limitations.

### Research findings settled at gate 1

Recorded so gate 4 implements against verified behavior rather than rediscovering it.

- **Cancelling `EntityDamageEvent` suppresses explosion knockback.** Paper's `ServerExplosion#hurtEntities` sets `lastDamageCancelled` and `continue`s before computing the knockback vector. Use `setDamage(0)` — which leaves the event uncancelled and so preserves knockback — never `setCancelled(true)`. Widely repeated advice to the contrary is stale (pre-SPIGOT-5339).
- **Do not pass the wielder as the `source` argument of `World#createExplosion`.** The source entity receives neither damage nor knockback (Paper #11167, works-as-intended), which would silently remove the item's signature feel. Pass `null` and suppress the wielder's damage in the listener instead.
- **Suppressing damage via `setDamage(0)` still plays the hurt animation, sound, and i-frames** on the wielder. Accepted as cosmetic.
- **`BlockDamageEvent` fires for Bedrock players through Geyser,** including the instant-break path. Geyser sends a real `START_DIGGING` packet and Paper fires `BlockDamageEvent` before evaluating insta-break.
- **Prefer `BlockDamageEvent` over `PlayerInteractEvent(LEFT_CLICK_BLOCK)`.** Both originate from the same Geyser packet, but `PlayerInteractEvent` is cancelled aggressively by protection plugins and fires in unrelated contexts. `BlockDamageEvent` is strictly downstream — if `PlayerInteractEvent` is cancelled, `BlockDamageEvent` never fires, which is the desired behavior.
- **Do not register the axe in Geyser's custom-item mappings** (`custom_mappings.json`). Geyser issue #5848 reports interact events failing to fire for custom-model-data items registered that way, fixed only in the unreleased Custom Item API v2. PDC identification on a plain vanilla item avoids this entirely.

### Known limitations

1. **PDC-under-Geyser is inferred, not documented.** No citable source confirms `PersistentDataContainer` data survives for Bedrock players. It is architecturally near-certain — PDC is server-side NBT, stripped before reaching any client — but acceptance check 16 exists to verify it in-game at gate 7a rather than assume it.
2. **Adventure mode may suppress the trigger for Bedrock players.** Geyser evaluates the `can_break` block predicate client-side and returns without sending a packet, so on an adventure-mode server a Bedrock player may produce no `BlockDamageEvent` at all. Not applicable to `play.xpfarm.org` in survival, and not handled in v1.0.0.
3. **Re-striking the same block without moving off it may not re-fire.** Geyser synthesizes `START_BREAK` on position or held-item change rather than on each click, so rapid re-clicks on one block can be swallowed. Cosmetic given the block is destroyed by the first successful fell.
4. **No cooldown in v1.0.0.** Fell rate is limited only by gunpowder supply. A cooldown key can be added if play testing shows abuse.
5. **Nether fungus stems and huge mushrooms are out of scope.** v1.0.0 recognizes overworld log types only.
6. **Leaf drop rates use vanilla tables, not tuned rates.** Sapling yield is therefore no better than hand-breaking.
7. **The Widowmaker tier is deliberately deferred** to a later version — TNT-fuelled, real block damage, real self-damage, larger bounds. `FellExecutor` takes an explosion profile as a parameter from day one so the tier is an additive change rather than a refactor. This is a planned follow-up, not a v1.0.0 gap.

No gates are withheld. Status is `active`; all twelve gates apply.

## 2. Repository

- [x] Repository is `carmelosantana/minecraft-<slug>` with an SSH `origin` and `main` branch.
      `carmelosantana/minecraft-timber-blast`, `origin` =
      `git@github.com:carmelosantana/minecraft-timber-blast.git`, default branch `main`.
      Pushed by the user on 2026-07-20 after the harness blocked the agent's push;
      local `main` tracks `origin/main` at `e9b878c`.
- [x] Existing user-owned worktree changes were identified and preserved.
      No pre-existing worktree: the repository was created fresh at this gate. The only
      prior file was `docs/PLUGIN_CHECKLIST.md` from gate 1, which is preserved and committed.
- [x] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation.
      `rg -n 'herobrinesystems' . --hidden -g '!target/**' -g '!.git/**'` returns only this
      checklist's own checkbox text — no real references.

### Repository visibility — RESOLVED

The repository is **`PUBLIC`**, verified 2026-07-20 via
`gh repo view --json visibility`. It was created private in error at gate 2 and
corrected by the user the same day.

Recorded because the failure mode is non-obvious: the updater downloads release assets
unauthenticated, so a private repository fails gate 10 enrollment even when every
earlier gate is green. New plugin repositories should be created public from the start.

## 3. Metadata

- [x] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent.
      Full AGPL-3.0 text in `LICENSE`; `pom.xml` `<licenses>` names "GNU Affero General
      Public License v3.0 or later" pointing at `https://www.gnu.org/licenses/agpl-3.0.html`.
- [x] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present.
      `pom.xml` `<url>` and `<developers>`; `plugin.yml` `website:` and `author:`.
- [x] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented.
      Recorded in `README.md` (Java and Bedrock, via Geyser + Floodgate).
- [x] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented.
      `org.xpfarm:timber-blast:1.0.0`. No existing-coordinate carve-out applies — this is new work.
- [x] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent.
      Verified: project `<artifactId>timber-blast</artifactId>`, `plugin.yml` `name: TimberBlast`,
      slug `timber-blast`, updater destination `timber-blast.jar`, releasable JAR
      `timber-blast-1.0.0.jar`. Matches the gate 1 naming chain exactly.
- [x] No secrets committed in source, defaults, tests, logs, history, or documentation.
      Secret scan returned only this checklist's own text and the workflow's
      `GH_TOKEN: ${{ github.token }}` expression, which is the documented Actions contract,
      not a committed credential.

## 4. Compatibility

- [x] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '1.21'`.
      `mvn clean verify` green on Java 25 against `paper-api 26.1.2.build.74-stable`;
      embedded `plugin.yml` shows `api-version: '1.21'`.
- [x] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared.
      None — no `depend`/`softdepend`/`loadbefore`/`loadafter`. Protection-plugin
      compatibility is achieved by firing cancellable `BlockBreakEvent`s, not by depending
      on any plugin. Confirmed at runtime: TimberBlast enabled with no dependency warnings.
- [x] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior.
      PDC-only identity (no custom model data, no Geyser item mapping — avoids Geyser
      #5848); `BlockDamageEvent` trigger (fires for Bedrock incl. instant-break);
      plain-text command output, no forms/GUIs/chat-input; `/tb give` resolves
      Floodgate-prefixed Bedrock usernames. Runtime-verified: Paper, Geyser, Floodgate,
      and ViaVersion all enabled together on the disposable stack (see §7).

## 5. External services

- [x] External integrations are disabled by default or require explicit configuration and have bounded timeouts.
      Not applicable — zero external integrations. Verified: no `java.net`, `HttpClient`,
      `URL`, or `Socket` anywhere in source; no outbound calls.
- [x] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable.
      Not applicable — no external endpoints exist.
- [x] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets.
      Not applicable — no endpoints, no secrets. Plugin enabled cleanly with no network activity.

## 6. Tests and build

- [x] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable.
      231 unit tests across config, tree scan, item identity, fell execution, listeners,
      and command. Every load-bearing rule was mutation-tested during review — surviving
      mutants were treated as coverage gaps and closed, catching (among others) an
      unguarded radius bound, an invertible event accessor, and the wielder-self-damage
      invariant that had zero coverage after an implementer error.
- [x] `mvn --batch-mode --no-transfer-progress clean verify` succeeds.
      BUILD SUCCESS on merged `main`, 231 tests, 0 failures.
- [x] The shaded releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded.
      `timber-blast-1.0.0.jar`: embedded `plugin.yml` correct (name `TimberBlast`,
      version `1.0.0`, main `org.xpfarm.timberblast.TimberBlastPlugin`, api-version `1.21`,
      commands + permissions intact); main class present; 43 plugin classes only; no
      `org.bukkit`/`io.papermc` server API bundled; no test classes or `testsupport`
      helpers leaked. The CI workflow excludes `original-*` from release assets and checksums.

## 7. Matrix

**7a (single-plugin runtime verification — this skill's portion):** partially completed
on a fresh-volume disposable Legendary stack (`xpfarm-slot` slot 0), 2026-07-20.

- [ ] Fresh-volume Legendary stack test covers all ten updater-managed plugins.
      **7b — out of band, not this gate.** Belongs to `minecraft-plugin-matrix`, not
      required for this plugin's release. Left unchecked deliberately.
- [ ] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately.
      **7b — out of band.** Not this gate.
- [x] Paper, Geyser, Floodgate, and ViaVersion start successfully together.
      Verified live: all four enabled in one boot (Paper "Done (19.551s)", Geyser 2.11.0,
      Floodgate 2.2.5, ViaVersion 5.11.0), and TimberBlast v1.0.0 enabled alongside them —
      "registered 3 listeners", zero exceptions on enable.
- [~] Java and Bedrock smoke tests cover joins plus affected commands, events, permissions, persistence, and reloads where feasible.
      **Partially verified — command path yes, swing-driven behaviors NOT observed on a
      live client.** Verified live via RCON console (real command pipeline on the running
      server): `/timberblast reload` and `/tb reload` succeed and re-read config; bare
      `/timberblast` returns usage; `/timberblast give` from console returns the correct
      "console has no inventory, name a player" error (a mutation-tested branch, confirmed
      live); `/timberblast bogus` is handled gracefully with no stack trace. All with zero
      server-log exceptions.

      **NOT observed on a live client, and honestly recorded as such:** the actual
      swing-driven fell and its consequences — tree felling through the real
      `BlockDamageEvent` pipeline, PDC identity surviving the Geyser path, the struck log
      becoming coal, gunpowder consumption, the null-source explosion, wielder knockback
      **without self-damage**, the vetoed-log-stays-standing behavior, one-fell-per-reload,
      and the leaf-event cost on a jungle canopy. These require an authenticated
      Minecraft client swinging the axe. This headless environment has no interactive
      client, and an automated `mineflayer` bot could not connect: `minecraft-protocol`
      has no packet schema for the very new Paper 26.1.2, and ViaVersion could not bridge
      a 1.21.4 bot through the changed login/configuration protocol. This is a tooling gap,
      not a plugin defect. These behaviors are backed by the 231 unit tests and the
      mutation-tested review rounds (§6) rather than by live-client observation.

      **Required live-client checks to run before or at first play-server exposure**
      (from the review rounds — the surfaces unit tests structurally cannot reach):
      1. Swing the axe at a log and confirm a tree falls and the wielder **survives** the
         blast (the one failure no type system closes: forgetting `trigger.damageListener()`).
      2. Reload three times, swing once, confirm exactly one tree and one explosion (listener leak).
      3. Craft the axe and confirm PDC identity survives; confirm a Bedrock client via
         Geyser triggers the fell identically to Java.
      4. Fell a full jungle canopy with a protection/logging plugin loaded and measure the
         `fell.max-leaves`-bounded event cost.
- [ ] Public deployment smoke tests verify `play.xpfarm.org` reaches the intended Java and Bedrock entry points.
      Deferred to gate 11 (deployment). Not reachable from a disposable stack.
- [x] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable.
      Not applicable — no external endpoints.

### 7b — ecosystem matrix (12 plugins) — PASSED 2026-07-21

Trigger: the updater manifest changed — Timber Blast `v1.0.0` was enrolled
(`carmelosantana/minecraft-plugin-updater` commit `6065b03`), taking the roster from 11 to 12.

- [x] Fresh-volume Legendary stack test covers all updater-managed plugins. **12/12 PRESENT.**
      Run via the shared rig (`xpfarm-test-stack matrix up --from-releases`) on a fresh volume,
      roster read from the live `plugins.json` rather than a hardcoded list. The rig cross-checks
      the plugin count the server announces against what it parsed, and asserts each plugin is
      **enabled**, not merely listed.
- [x] Each updater-managed plugin's manifest `enabled` value, default state, and expected
      fresh-volume behavior are recorded separately. All 12 entries have `enabled` absent
      (equivalent to `true`) and no `pin`; every one was therefore expected to install and enable,
      and every one did. No entry was disabled, so there is no intentional-absence row this run.
- [x] Paper, Geyser, Floodgate, and ViaVersion start successfully together.
      Paper reached `Done (15.543s)! For help, type "help"`; the Java port answered a real
      protocol handshake reporting `Paper 26.1.2 | protocol 775`, `PLAYERS: 0 / 20`. Companions:
      Geyser-Spigot 2.11.0-SNAPSHOT, floodgate 2.2.5-SNAPSHOT, ViaVersion 5.11.0.
- [ ] Java and Bedrock smoke tests cover joins. **Not performed — no client attaches to this
      stack by design.** Per `PLUGIN_LIFECYCLE.md` §7 this is not a blocker; client behavior is a
      tracked gate-12 play-test obligation, not a matrix result.
- [x] `play.xpfarm.org` reaches the intended Java and Bedrock entry points.
      Read-only production check, separate from the disposable stack: DNS `168.231.74.113`;
      Java `25565` answered a real handshake (`Paper 26.1.2 | protocol 775`, 1 player online);
      Bedrock UDP `19132` reachable.
- [x] Ollama and Umami unavailable-endpoint tests keep the server and plugins available.
      Neither service exists in this stack, so this is the negative path by construction. Both
      self-disabled cleanly: `Ollama integration is disabled; no API client or listeners were
      started.` and `Umami analytics is disabled; no tracking listeners or network clients were
      started.` Server stayed healthy (`list` responded) with all 12 enabled.

This plugin's row: the updater reported `TimberBlast: installed v1.0.0` from the published release
asset and Paper enabled it alongside the other 11. `--from-releases` was used deliberately — it
installs the real published assets through the real updater, so this is what production installs.

Co-resident: AguaDeFlorida 2.0.0, CopperKingdom 0.2.1, TheCurse 0.2.2, DeathDepot 1.1.1, ElectricFurnace 0.2.1, GlutenFreeBread 1.1.3, Ollama 0.2.1, StarterPack 1.1.2, Umami 1.1.1, WildWeatherUpdate 1.0.2, WorldCRUD 1.1.2.

Zero exceptions, SEVERE lines, or enable failures attributable to any plugin. No secrets in any
log line. Stack torn down with `matrix down`; lease released, no orphaned containers.

## 8. CI/CD

- [x] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior.
      `.github/workflows/build.yml` matches `GITHUB_ACTIONS.md`: push to `main`, `v*` tags,
      `pull_request` to `main`, `workflow_dispatch`; `actions/checkout@v7`;
      `actions/setup-java@v5` Temurin 25 with Maven cache;
      `mvn --batch-mode --no-transfer-progress clean verify`;
      `actions/upload-artifact@v7`; and `gh release view`/`create`/`upload --clobber` on `v*`.
      Checksum generation uses the corrected bare-filename form
      (`cd target && find . -maxdepth 1 ... -printf '%f\0' | ... sha256sum`), not the
      `target/`-prefixed variant `GITHUB_ACTIONS.md` documents as defective.
- [x] Successful main Actions run is recorded before tagging.
      Run [29778591066](https://github.com/carmelosantana/minecraft-timber-blast/actions/runs/29778591066)
      on `7f76f66` (the tagged commit) concluded `success`, built from the full plugin
      source with all 231 tests passing. This is the run built from actual plugin code —
      superseding the earlier scaffold-only run.
- [x] Workflow permissions contain no broader access than the documented contract.
      `permissions: contents: write` only.

## 9. Release

- [x] Semantic version matches the POM, plugin metadata, and `v<version>` tag.
      `1.0.0` in `pom.xml`, embedded `plugin.yml` (`version: '1.0.0'`), and tag `v1.0.0`.
- [x] Successful tag Actions run and GitHub release are recorded.
      Tag run [29790862820](https://github.com/carmelosantana/minecraft-timber-blast/actions/runs/29790862820)
      concluded `success`; release [v1.0.0](https://github.com/carmelosantana/minecraft-timber-blast/releases/tag/v1.0.0)
      published, stable (prerelease=false, draft=false).
- [x] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR.
      Assets: `timber-blast-1.0.0.jar` (61426 b) + `SHA256SUMS.txt` (89 b). No `original-*`.
- [x] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`.
      `timber-blast-1.0.0.jar: OK`.

## 10. Updater

- [x] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin.
      `plugins.json` entry: repo `carmelosantana/minecraft-timber-blast`, destination
      `timber-blast.jar`, `asset_regex` `^timber-blast-[0-9].*\.jar$` (verified to select
      `timber-blast-1.0.0.jar` only, rejecting `original-*`, `SHA256SUMS.txt`, and the
      destination name), `legacy_globs` `["timber-blast-[0-9]*.jar"]`, enabled by default
      (no `enabled` key = true, matching siblings), no pin. `json.tool` valid; 13 unit tests pass.
      Committed `6065b03` in `carmelosantana/minecraft-plugin-updater`.
- [x] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass.
      Exercised against a disposable sandbox: fresh install ("installed v1.0.0", 61426 b);
      no-op ("already current (v1.0.0)"); replacement of stale bytes (reinstalled + backup
      written); legacy archival (`timber-blast-0.9.0.jar` matched the glob and moved to
      backups as `.legacy.bak`); endpoint failure (404 on a bogus repo → warn-and-continue,
      exit 0); checksum failure via `test_bad_checksum_preserves_installed_jar` (passes).
- [x] Updater dry-run uses a disposable directory and never a production plugin directory.
      All runs used `/tmp/minecraft-plugin-updater-dry-run` and `/tmp/mc-upd-tb*` with
      `--plugins-dir`, `--state-file`, and `--backup-dir` all inside the sandbox root.
      No path touched `/minecraft`. Sandboxes discarded after.
- [x] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup.
      Confirmed: after the 404 failure the installed `timber-blast.jar` SHA was byte-identical
      before and after, and the run exited 0 (fail-open) — a plugin-level failure never aborts
      the batch or blocks startup.

## 11. Deployment

- [ ] Dokploy redeployment notes identify the full recreation used to rerun the one-shot updater.
- [ ] Updater completion, Minecraft startup, destination JAR, and stack/plugin logs were inspected.
- [ ] No production plugin hot reload was used.

## 12. Handoff

- [ ] Current-state documentation refreshed with release, CI, updater, deployment, and local pending state.
- [ ] Known limitations, skipped checks, configuration or migration notes, rollback guidance, and follow-up owner are recorded.
- [ ] Evidence distinguishes source commit, published tag/release, updater state, and deployed state without exposing secrets.
