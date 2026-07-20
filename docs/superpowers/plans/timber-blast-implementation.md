# Timber Blast v1.0.0 — Implementation Plan

Executes §1 Scope of `docs/PLUGIN_CHECKLIST.md`. Read that file's §1 for the
authoritative scope; this plan slices it into independently implementable tasks.

## Global Constraints

Binding on every task. A task that violates any of these is not complete.

- **Java 25**, **Paper `26.1.2` build 74**, `api-version: '1.21'` in `plugin.yml`.
- Maven group `org.xpfarm`, artifactId `timber-blast`, version `1.0.0`.
- Root package `org.xpfarm.timberblast`.
- **AGPL-3.0-or-later**. Every new `.java` file carries the project's license header
  in the same form the sibling repo `../electric-furnace` uses. Match that file's
  header verbatim in structure.
- **No external services.** This plugin makes zero outbound network calls. Do not add
  HTTP clients, Ollama, Umami, or telemetry of any kind.
- **No new dependencies.** `paper-api` (provided) and `junit-jupiter` (test) only.
  Do not add Mockito, MockBukkit, or any other library to `pom.xml`.
- **Tests ship with the code, not after it.** Any logic separable from the Bukkit
  runtime must be unit tested in the same task that writes it. Pure-logic classes
  (`TreeScanner`, config parsing) must not import `org.bukkit.*` at all — that is what
  makes them testable without a server.
- **Geyser/Floodgate/Bedrock safety.** Player-facing interaction must work for Bedrock
  clients via Geyser. Specifically: no Java-only chat-input prompts, no custom
  inventory GUIs in v1.0.0, and identify the axe **only** by PersistentDataContainer —
  never register it in Geyser custom item mappings (Geyser issue #5848 breaks interact
  events for mapping-registered custom-model-data items).
- Follow the house style of the sibling plugin at
  `/home/carmelo/Projects/Minecraft/Plugins/electric-furnace` — package layout,
  config class shape, naming, comment density. Read it before writing.
- Build must pass `mvn --batch-mode --no-transfer-progress clean verify`.

### Settled API facts — do not re-derive, do not "improve"

These were researched and settled at planning time. Implement against them exactly.

1. **Cancelling `EntityDamageEvent` suppresses explosion knockback.** Paper's
   `ServerExplosion#hurtEntities` sets `lastDamageCancelled` and `continue`s *before*
   computing the knockback vector. To give the wielder knockback without damage, call
   `event.setDamage(0)` — **never** `event.setCancelled(true)`.
2. **Never pass the wielder as the `source` argument of `World#createExplosion`.**
   The source entity receives neither damage nor knockback (Paper #11167, works as
   intended). Pass `null` as the source and suppress the wielder's damage in the
   listener instead.
3. **`BlockDamageEvent` is the trigger**, not `PlayerInteractEvent`. It fires for
   Bedrock players through Geyser including the instant-break path, is narrower than
   `PlayerInteractEvent`, and carries `getItemInHand()` and `getInstaBreak()`.

## Task 1 — Configuration

Create `org.xpfarm.timberblast.config`.

Implement a `TbConfig` holding validated, immutable settings, plus a
`ConfigSource` abstraction so parsing is testable without Bukkit.

```java
public interface ConfigSource {
    int getInt(String path, int def);
    double getDouble(String path, double def);
    boolean getBoolean(String path, boolean def);
    String getString(String path, String def);
}
```

Provide a `BukkitConfigSource` adapter wrapping `org.bukkit.configuration.ConfigurationSection`.
`TbConfig.load(ConfigSource, Consumer<String> warn)` returns a `TbConfig` and reports
each rejected value through `warn`.

Keys, types, defaults, and validation ranges — use these values verbatim:

| Key | Type | Default | Valid range |
| --- | --- | --- | --- |
| `fell.max-blocks` | int | 256 | 1–4096 |
| `fell.max-radius` | int | 8 | 1–64 |
| `fell.max-height` | int | 32 | 1–256 |
| `fell.drop-leaves` | bool | true | — |
| `fuel.material` | String | `GUNPOWDER` | must resolve via `Material.matchMaterial` |
| `fuel.amount` | int | 1 | 1–64 |
| `explosion.power` | float/double | 2.0 | 0.0–10.0 |
| `explosion.block-damage` | bool | false | — |
| `explosion.knockback-multiplier` | double | 1.0 | 0.0–5.0 |
| `coal.enabled` | bool | true | — |
| `coal.material` | String | `COAL` | must resolve via `Material.matchMaterial` |
| `scorch.enabled` | bool | true | — |
| `scorch.spread` | bool | false | — |

Validation rule: an out-of-range or unparseable value logs a warning through `warn`
and **falls back to the default** — it never throws and never prevents the plugin
from enabling. Material validation is the one part that needs Bukkit; keep
`Material` resolution in the adapter layer or accept a `Function<String, Boolean>`
validator so `TbConfig` itself stays Bukkit-free and testable.

Also write `src/main/resources/config.yml` with every key above, its default, and a
brief comment per section.

**Tests:** defaults when the source is empty; each bound rejected and defaulted at
min-1 and max+1; each bound accepted at exactly min and exactly max; warnings emitted
for each rejection; unparseable strings fall back.

## Task 2 — TreeScanner

Create `org.xpfarm.timberblast.tree`. **This class must not import `org.bukkit.*`.**

```java
public record BlockPos(int x, int y, int z) {}
public enum BlockKind { LOG, LEAF, OTHER }
public interface BlockQuery { BlockKind kindAt(BlockPos pos); }
```

`TreeScanner` performs a bounded flood fill from an origin log:

```java
public record ScanResult(List<BlockPos> logs, List<BlockPos> leaves, boolean truncated) {}
public ScanResult scan(BlockPos origin, BlockQuery query, int maxBlocks, int maxRadius, int maxHeight);
```

Behavior:

- Flood fill across all 26 neighbours (include diagonals — 2×2 jungle trunks and
  diagonal branches are connected only diagonally).
- Traverse **through logs only**. Leaves are collected when adjacent to a visited log
  but are never themselves traversed — otherwise a canopy touching a neighbouring tree
  chains both trees together.
- `maxRadius` bounds `|x - origin.x|` and `|z - origin.z|`. `maxHeight` bounds
  `|y - origin.y|`. A block outside either bound is not visited and not collected.
- `maxBlocks` caps the **log** count. On reaching the cap, stop expanding and set
  `truncated = true`. Leaves collected so far are still returned.
- The origin block is always included as the first element of `logs`, so callers can
  identify which block was struck.
- Deterministic ordering: use a breadth-first queue so results are stable across runs
  and testable by exact list comparison.
- Return empty logs (not null, not an exception) if the origin is not a `LOG`.

**Tests:** use a hand-built `Map<BlockPos, BlockKind>` fake `BlockQuery`. Cover a
single log; a straight 5-tall trunk; a trunk with a diagonal branch; two trees whose
canopies touch (must NOT chain); a 2×2 trunk; `maxBlocks` truncation sets the flag and
caps the count; a block beyond `maxRadius` excluded; a block beyond `maxHeight`
excluded; origin not a log returns empty; leaves adjacent to logs collected but not
traversed through.

## Task 3 — Item identity and recipe

Create `org.xpfarm.timberblast.item`.

`TimberBlastItem` provides:

- A `NamespacedKey` of `timber-blast:axe` (namespace from the plugin instance).
- `ItemStack create()` — a `Material.DIAMOND_AXE` with display name `Timber Blast`,
  a short lore line describing the gunpowder cost, and the PDC key set to
  `PersistentDataType.BYTE` value `1`.
- `boolean isTimberBlast(ItemStack)` — null-safe and meta-safe; returns false for
  null, air, and items without meta. This is the **only** identity check used anywhere
  in the plugin.
- Recipe registration: a `ShapedRecipe` keyed `timber-blast:axe_recipe` producing the
  item. Pattern:

  ```
  "GDG"
  "GSG"
  " S "
  ```

  where `G` = `Material.GUNPOWDER`, `D` = `Material.DIAMOND_AXE`, `S` = `Material.STICK`.
  Register on plugin enable; guard against duplicate registration on `/timberblast reload`.

Do **not** use custom model data — it is unnecessary and interacts badly with Geyser.

**Tests:** the PDC round-trip and recipe shape need a live server, so unit-test only
what is separable — verify the recipe pattern strings and ingredient map are what the
spec states, via a small pure helper that returns the pattern/ingredients. Do not
fabricate a Bukkit mock. State clearly in the report which behaviors are deferred to
runtime verification (gate 7a).

## Task 4 — ScorchService (WITHDRAWN 2026-07-20)

**This task is cancelled. The leaf-fire mechanic was scrapped entirely by Carmelo.**

Hitting a leaf with the Timber Blast axe now does nothing special — the axe behaves
exactly like a normal axe. No fire, no scorch, no containment, no tracking.

Everything below is retained only as the record of what was built and removed. The
implementation (`org.xpfarm.timberblast.effect`) was completed, reviewed, and then
deleted along with the `scorch.enabled` and `scorch.spread` config keys. Task 5's
leaf branch is correspondingly reduced to "return without acting."

Do not implement any of the following.

---

## Task 4 — ScorchService (original text, withdrawn)

Create `org.xpfarm.timberblast.effect`.

`ScorchService` handles the leaf-hit path:

- `void scorch(Block leafBlock)` — sets the block **above** the struck leaf to
  `Material.FIRE` if that block is air, plays `Sound.ITEM_FLINTANDSTEEL_USE`, and
  registers the fire location in an in-memory `Set`.
- Fire containment: implement `BlockSpreadEvent` and `BlockIgniteEvent` listeners that
  cancel the event when the **source** block is a registered scorch fire. This is what
  makes scorch fire non-spreading.
- When `scorch.spread` is `true` in config, skip registration entirely so vanilla fire
  behavior applies.
- When `scorch.enabled` is `false`, `scorch()` is a no-op.
- Tracked locations are in-memory only and are cleared on plugin disable. Bound the
  set's growth: drop a tracked location once its block is no longer `Material.FIRE`,
  checked opportunistically when the set is touched, so a long-running server does not
  accumulate stale entries without bound.

**Tests:** the tracking-set logic (add, contains, opportunistic eviction of
non-fire entries, clear-on-disable, and the `spread`/`enabled` config gates) must be
separable from Bukkit and unit tested. Extract that bookkeeping into a small pure
class if needed to make it testable.

## Task 5 — FellExecutor and the trigger listener

Create `org.xpfarm.timberblast.fell`. This is the integration task — it depends on
Tasks 1–4 and must use their existing APIs without modifying them.

`TimberBlastListener` on `BlockDamageEvent` (priority `NORMAL`, `ignoreCancelled = true`):

1. Return unless `TimberBlastItem.isTimberBlast(event.getItemInHand())`.
2. Return unless the player has `timberblast.use`.
3. If the struck block is a log (`Tag.LOGS`) → run the fell path below.
4. Any other block, **including leaves** → return without acting, so the axe behaves
   exactly like a normal axe. There is no leaf-specific behavior; the ScorchService
   task was withdrawn.

Fell path in `FellExecutor`:

1. Check the player has at least `fuel.amount` of `fuel.material` in their inventory.
   If not, return without acting — the axe falls through to vanilla behavior.
2. Adapt the world into a `BlockQuery` (this adapter is the Bukkit boundary; keep
   `TreeScanner` itself untouched) and scan with the configured bounds.
3. For every log in the result, construct and call a `BlockBreakEvent`, firing it
   through `Bukkit.getPluginManager().callEvent(...)`. **Skip any log whose event
   comes back cancelled** — leave that block standing. This is what gives protection
   plugins their veto.
4. Break the surviving logs. The **origin** block (first element of `logs`) drops
   `coal.material` × 1 instead of its log drop when `coal.enabled`; every other log
   drops naturally (`Block#breakNaturally`).
5. If `fell.drop-leaves`, break the scanned leaves naturally so they roll vanilla
   sapling/stick/apple tables.
6. Consume `fuel.amount` of `fuel.material` from the player's inventory.
7. Create the explosion at the origin:
   `world.createExplosion(originLocation, (float) power, false, config.explosionBlockDamage())`
   with a **null source** — see Settled API Fact 2.
8. Apply knockback to the wielder via `Player#setVelocity`, scaled by
   `explosion.knockback-multiplier`, directed away from the origin.
9. Damage the item's durability by 1 using the normal Bukkit damage path.

`WielderDamageListener` on `EntityDamageEvent`: when the entity is a player this
plugin just knocked back and the cause is `BLOCK_EXPLOSION` or `ENTITY_EXPLOSION`,
call `event.setDamage(0)`. **Never `setCancelled(true)`** — see Settled API Fact 1.
Scope this narrowly: track the wielder for the duration of the blast tick only, so
unrelated explosions are unaffected.

**Tests:** unit test whatever is separable — the knockback vector calculation, the
fuel-sufficiency check, and the origin-vs-rest drop decision. The event-firing paths
need a live server; state clearly in the report which behaviors defer to gate 7a.

## Task 6 — Plugin main class and command

Create `org.xpfarm.timberblast.TimberBlastPlugin` (the `main:` already declared in
`plugin.yml`) and `org.xpfarm.timberblast.command.TimberBlastCommand`.

`TimberBlastPlugin`:

- `onEnable`: save default config, load `TbConfig`, construct `TimberBlastItem`,
  `ScorchService`, `FellExecutor`, register all listeners, register the recipe,
  register the command executor and its tab completer.
- `onDisable`: clear `ScorchService` tracking. Do not unregister recipes.
- A `reload()` method that re-reads config and rebuilds the dependent services
  **without** a server-wide plugin reload.

`TimberBlastCommand` implements `/timberblast` with `tb` alias:

- `give [player]` — requires `timberblast.admin`. No argument targets the sender;
  a sender who is not a player and gives no argument gets a usage error. Unknown or
  offline player name gets a clear error message. Full inventory drops the item at
  the player's feet rather than silently vanishing.
- `reload` — requires `timberblast.admin`. Calls `reload()` and reports success or
  the validation warnings produced.
- Unknown subcommand or missing permission produces a clear message, never a stack trace.
- Tab completion offers `give` and `reload` for `timberblast.admin` holders, and
  online player names for `give`'s first argument.

**Bedrock safety:** all output is plain chat text. No forms, no GUIs, no
chat-input prompts.

**Tests:** unit test argument parsing and permission-gating decisions by extracting
them into a pure resolver class that returns an outcome enum rather than calling
Bukkit directly. State which behaviors defer to gate 7a.
