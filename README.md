# TimberBlast

A gunpowder-fuelled axe for Paper servers that fells an entire tree in one swing.
Hit a log and the whole thing comes down in a blast that scatters every log, leaf,
sapling, and stick across the ground — and knocks you clean off your feet.

Running on **`play.xpfarm.org`** (Java and Bedrock, via Geyser + Floodgate).

## How it works

Swing the axe at a **log** and the tree it belongs to is felled in a single hit.
The blast has no block damage — it will not crater your terrain or blow up your
chests — but it launches you backward and hurts anything standing too close.

| You hit | What happens |
|---|---|
| A log | Whole tree falls, 1 gunpowder burned, struck log becomes coal |
| A leaf | The leaves scorch. No blast, no fuel burned |
| Anything else | Behaves like a normal axe |

Each fell burns **one gunpowder** from your inventory. Out of gunpowder, the axe
still works — it just chops like any other axe. The tax is deliberate: this is a
tool you keep fuelled from a creeper farm, not a permanent upgrade you craft once
and forget.

### The coal

The specific log you struck doesn't drop as a log. It comes out the other side as
a single piece of **coal**, charred by the blast. Every other log in the tree
drops normally.

### The leaves

Missing the trunk and clipping a leaf instead sets the leaves alight — but the
fire will not spread. It burns out where it started. Bad aim costs you a few
leaves, not a biome.

## Limits

A single swing is bounded so one hit can't stall the server:

| Limit | Default |
|---|---|
| Blocks felled | 256 |
| Horizontal reach from the trunk | 8 |
| Vertical reach from the trunk | 32 |

Every log is broken through a cancellable break event, so land claims and
protection plugins keep their veto — a tree straddling a claim boundary loses only
the blocks you were allowed to break.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/timberblast give [player]` | `timberblast.admin` | Grants the axe |
| `/timberblast reload` | `timberblast.admin` | Reloads configuration |

Aliased to `/tb`.

## Permissions

| Node | Default |
|---|---|
| `timberblast.use` | everyone |
| `timberblast.craft` | everyone |
| `timberblast.admin` | operators |

## License

AGPL-3.0-or-later. See [LICENSE](LICENSE).
