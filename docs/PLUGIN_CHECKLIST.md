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
of coal. Hitting leaves instead of a log only scorches them — no blast. Each fell
burns one gunpowder from the player's inventory, so the axe stays fuelled by
creeper farming rather than being a permanent upgrade.

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
| `BlockSpreadEvent` | listen | Cancels propagation from plugin-placed scorch fire so leaf hits cannot burn down a forest. |
| `BlockIgniteEvent` | listen | Same containment, for ignition sourced from tracked scorch fire. |

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
| `fuel.material` | string | `GUNPOWDER` | must resolve to a valid `Material` |
| `fuel.amount` | int | `1` | 1–64 |
| `explosion.power` | float | `2.0` | 0.0–10.0 |
| `explosion.block-damage` | bool | `false` | — |
| `explosion.knockback-multiplier` | double | `1.0` | 0.0–5.0 |
| `coal.enabled` | bool | `true` | — |
| `coal.material` | string | `COAL` | must resolve to a valid `Material` |
| `scorch.enabled` | bool | `true` | — |
| `scorch.spread` | bool | `false` | `true` restores vanilla spreading fire |

Invalid values log a warning and fall back to the default; a malformed config never
prevents the plugin from enabling.

### Persistence

No database and no flat-file state. The axe is identified by a
`PersistentDataContainer` key (`timber-blast:axe`, `BYTE`) written into the
`ItemStack` meta, so identity travels with the item through chests, hoppers, death,
and restarts with no server-side bookkeeping. Scorch-fire tracking is in-memory only
and intentionally does not survive restart.

### Dependencies

None — hard or soft. No load-order requirements. Protection-plugin compatibility is
achieved by firing cancellable `BlockBreakEvent`s rather than by depending on any
specific protection plugin, so it works with whatever is installed or none at all.

### External integrations

`none`. No Ollama, Umami, or other outside-service calls. Gate 5 is satisfied
trivially and its boxes should be ticked with that explanation rather than left blank.

### Acceptance checks

1. Crafting the recipe yields an axe whose meta carries the `timber-blast:axe` PDC key.
2. Striking a log with the axe, holding ≥1 gunpowder, removes every connected log within bounds and drops them as items.
3. Exactly one gunpowder is consumed per successful fell.
4. With zero gunpowder, striking a log performs normal vanilla axe behavior — no fell, no blast, no coal.
5. The struck log yields exactly one coal; all other logs yield their normal log drops.
6. Attached leaves are removed and roll their vanilla drop tables (saplings, sticks, apples).
7. The blast damages no blocks at any configured power.
8. The wielder is knocked back and loses no health; nearby mobs take blast damage.
9. Striking a leaf block places fire, consumes no gunpowder, and produces no blast.
10. Scorch fire does not spread to adjacent blocks and burns out in place.
11. A tree exceeding `fell.max-blocks` stops at the cap and leaves the remainder standing.
12. A wood structure wider than `fell.max-radius` is not fully consumed.
13. A `BlockBreakEvent` cancelled by another plugin leaves that specific log standing while the rest of the fell proceeds.
14. `/timberblast give` grants a functioning axe; `/timberblast reload` applies changed config without restart.
15. Both admin commands are refused without `timberblast.admin`.
16. **Bedrock/Geyser:** a Bedrock player crafts, receives, and swings the axe, and the fell triggers identically to Java. This check specifically confirms PDC identity survives the Geyser path — see limitations.

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

- [ ] Repository is `carmelosantana/minecraft-<slug>` with an SSH `origin` and `main` branch.
- [ ] Existing user-owned worktree changes were identified and preserved.
- [ ] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation.

## 3. Metadata

- [ ] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent.
- [ ] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present.
- [ ] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented.
- [ ] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented.
- [ ] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent.
- [ ] No secrets committed in source, defaults, tests, logs, history, or documentation.

## 4. Compatibility

- [ ] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '1.21'`.
- [ ] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared.
- [ ] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior.

## 5. External services

- [ ] External integrations are disabled by default or require explicit configuration and have bounded timeouts.
- [ ] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable.
- [ ] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets.

## 6. Tests and build

- [ ] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable.
- [ ] `mvn --batch-mode --no-transfer-progress clean verify` succeeds.
- [ ] The shaded releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded.

## 7. Matrix

- [ ] Fresh-volume [Legendary Java Minecraft Geyser Floodgate stack](https://github.com/TheRemote/Legendary-Java-Minecraft-Geyser-Floodgate) test covers all ten updater-managed plugins.
- [ ] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately.
- [ ] Paper, Geyser, Floodgate, and ViaVersion start successfully together.
- [ ] Java and Bedrock smoke tests cover joins plus affected commands, events, permissions, persistence, and reloads where feasible.
- [ ] Public deployment smoke tests verify `play.xpfarm.org` reaches the intended Java and Bedrock entry points.
- [ ] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable.

## 8. CI/CD

- [ ] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior.
- [ ] Successful main Actions run is recorded before tagging.
- [ ] Workflow permissions contain no broader access than the documented contract.

## 9. Release

- [ ] Semantic version matches the POM, plugin metadata, and `v<version>` tag.
- [ ] Successful tag Actions run and GitHub release are recorded.
- [ ] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR.
- [ ] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`.

## 10. Updater

- [ ] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin.
- [ ] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass.
- [ ] Updater dry-run uses a disposable directory and never a production plugin directory.
- [ ] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup.

## 11. Deployment

- [ ] Dokploy redeployment notes identify the full recreation used to rerun the one-shot updater.
- [ ] Updater completion, Minecraft startup, destination JAR, and stack/plugin logs were inspected.
- [ ] No production plugin hot reload was used.

## 12. Handoff

- [ ] Current-state documentation refreshed with release, CI, updater, deployment, and local pending state.
- [ ] Known limitations, skipped checks, configuration or migration notes, rollback guidance, and follow-up owner are recorded.
- [ ] Evidence distinguishes source commit, published tag/release, updater state, and deployed state without exposing secrets.
