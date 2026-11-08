# Phantom Charm

One charm calls up every pet and mode. Its art changes to show the current mode.

## Recipe

| | | |
|:-:|:-:|:-:|
| Gold Block | Phantom Membrane | Gold Block |
| Phantom Membrane | Ender Chest | Phantom Membrane |
| Gold Block | Soul Campfire | Gold Block |

## Controls

| Action | Result |
|---|---|
| Sneak + scroll | Switch mode: Chest, Tank, Anchor, Collector, Saddle, Gateway, Bell. Locked modes are skipped |
| Use | Pet modes: summon that pet, or recall it to your side if it's out. Other modes: see their pages |
| Sneak + use | Pet modes: dismiss that pet. Other modes: see their pages |

Summoning, recalling and dismissing share a 5-second cooldown; getting on or off the saddled chest uses a short one. A pet never comes across dimensions with the charm: it's dismissed when you change dimension (unless anchored) and you summon it again.

## Unlocks

The chest is available from the start. Every other mode unlocks with a vanilla advancement; there are no extra recipes. Until then the charm skips that mode, and switching to it names the advancement it needs. Creative mode ignores unlocks.

| Mode | Advancement |
|---|---|
| Phantom Tank | *Local Brewery* (brew a potion). *Not Quite "Nine" Lives* (charge a respawn anchor), the unlock before 2.9.0, still counts |
| Phantom Anchor | *Remote Getaway* (enter an End gateway) |
| Phantom Collector | *You've Got a Friend in Me* (have an Allay deliver you an item) |
| Phantom Saddle | *Great View From Up Here* (levitate 50 blocks from a shulker's attacks) |
| Phantom Gateway | *The End... Again...* (respawn the Ender Dragon) |
| Phantom Bell | *Hot Tourist Destinations* (explore all Nether biomes) |

For testing, `/phantomstorage unlock <tank|anchor|collector|saddle|gateway|bell|all>` grants a mode's advancement so it stays unlocked back in survival. It only works in creative mode. `/advancement revoke` locks a mode again (for the tank, revoke both of its advancements).
