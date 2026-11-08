# Phantom Storage

A NeoForge 1.21.1 mod: summonable, ghostly flying pets that carry your stuff. The Phantom Chest holds items, the Phantom Tank holds fluids, the Phantom Anchor is a waypoint you can teleport to, the Phantom Collector gathers dropped items and experience into your storage, and you can have them all out at once. The same charm also saddles the chest so you can fly it, and opens a gateway to your anchor that anyone can walk through.

## Controls

| Action | Result |
|---|---|
| Sneak + scroll with the **Phantom Charm** | Switch its mode: Chest, Tank, Anchor, Collector, Saddle or Gateway (the charm's art changes to match) |
| Use the charm | Summon that pet, or recall it to your side if it's already out (5 s cooldown). Anchor mode: teleport to the anchor instead |
| Use the charm while looking at the anchor | Swap places with it (anchor mode, up to 32 blocks, through walls) |
| Sneak + use the charm | Dismiss that pet (same cooldown) |
| Use the charm in Saddle mode | Ride your Phantom Chest (summoned under you if it isn't out); use again, or sneak, to get off |
| Use the charm in Gateway mode | Open a gateway to your anchored Phantom Anchor, 2 blocks ahead of you, for 15 s; sneak + use closes it |
| Right-click a pet | Open its panel: storage for the chest and tank; for the anchor, where it is, with Stay here and Dismiss; for the collector, its filter, range and banked experience |
| Right-click the tank with a bucket, bottle (water, 250 mB) or other fluid container | Pour into, or fill from, the active tank without opening the GUI; the action bar shows that tank's level |
| Sneak + right-click the tank holding a fluid container | Cycle the active tank |
| Sneak + right-click a pet (empty-handed) | Toggle stay / follow |
| Look at a pet ~½ s (while idle) | It comes within reach |

All the panels share one look: night violet in a gold-trimmed frame, slot groups in darker wells, the pet's charm art beside the title, and cyan for whatever is active or selected. Hover any icon, gauge or checkbox for a short description.

Chest:

| Icon | Does |
|---|---|
| Sort (bars, after the title) | Click: merge stacks and sort. Right-click or scroll: mode (Name, Amount, Mod). Shift + click: ascending / descending (the bars flip). Saved with your storage |
| Void grid | A filter of ghost entries, saved with your storage: click a slot with an item to add it, empty-handed to clear it. Matching items you or your collector pick up are destroyed (matched by item, whatever its enchantments or name). Items you move by hand are never touched |
| Trash (by the void grid) | Click while holding an item to destroy it |
| `+` (by the crafting output, JEI/REI only) | Opens crafting recipes; the viewer's own `+` fills the grid from storage and inventory |

Tank:

| Control | Does |
|---|---|
| Gauge, click with an empty cursor | Makes that the active tank (outlined) |
| Gauge, left-click with a filled container (bucket, water bottle, modded tank item) | Pours it into that tank |
| Gauge, right-click with an empty container | Fills it from that tank |
| Trash (under each gauge) | Destroys the fluid in that tank |
| **Overfill** checkbox (off by default) | Buckets on the tank: when the active tank is full or holds another fluid, pour into the next tank of that fluid or an empty one |
| **Drain all** checkbox (off by default) | Buckets on the tank: when the active tank is empty, keep drawing from the next tank until all are empty |

Anchor:

| Mode | Charm use |
|---|---|
| Following (default) | Teleports you to a safe spot within 3 blocks of it |
| Anchored (told to stay) | Teleports you to a safe spot within 3 blocks of it, from anywhere, other dimensions included. Its chunk stays loaded and ticking (farms, furnaces) while you're online |
| Looking at it, either mode | You swap places; an anchored anchor stays anchored where you stood |

A safe spot is solid ground the player fits on, with nothing harmful in it or next to it (fire, lava, cactus, powder snow and the like) and no portal or cobweb; surface water if there's nothing else. If there's none, the charm says so and nothing happens. An anchored anchor stays put through your logouts, deaths, dimension changes and server restarts; nothing is loaded while you're offline. Dismiss it with the charm (sneak + use, from anywhere) or its panel's trash button; untick **Stay here** in the panel (or sneak + right-click it) to have it follow again.

Collector:

| Mode | What it does |
|---|---|
| Following (default) | Picks up dropped items within its range of you (3–12 blocks, default 8). Experience orbs in range come to you, so Mending works as usual |
| Anchored (told to stay) | Picks up items within its range of its spot, wherever you are, other dimensions included; its chunk stays loaded and ticking while you're online. Experience goes into its bank; take it from its panel |

Items go into your Phantom Chest storage, then your inventory; whatever fits nowhere stays on the ground. Items matching the chest's void filter are destroyed instead. Its own filter (9 ghost entries in its panel) lists what to take, or with **Skip** ticked, what to leave; empty, it takes everything. It never takes items still on their pickup delay or items meant for someone else. Items you throw yourself lie 5 seconds first, so a quick toss to a friend or into a hopper still lands; anything you leave comes back. An anchored collector stays put through logouts, deaths, dimension changes and restarts, like the anchor; the charm recalls it (same dimension) or dismisses it (anywhere).

Saddle (rides the Phantom Chest; no entity of its own):

| While riding | Does |
|---|---|
| Look + W / S | Fly forward or back along where you look, up and down included |
| A / D | Strafe |
| Space | Rise |
| Sprint | About twice as fast (18 blocks/s against 9) |
| No keys | Slows to a hover |
| Sneak, or use the charm again | Get off |

Ridden, the chest collides with blocks and keeps your head out of them, so you can't fly into walls or ceilings; two-block tunnels still fit. It wears a saddle while you ride, takes no fall damage and passes none to you. It can't take portals with you aboard.

Gateway:

| What | Does |
|---|---|
| Opens | 2 blocks ahead of you, facing you: a 1.5 × 2 block doorway of the End gateway's starfield in a ghostly lodestone frame, the phantom eyes peering out of the void. Needs your Phantom Anchor anchored (told to stay) |
| Goes through | Whatever steps in: you, other players, mobs, items. Everyone lands on a safe spot within 3 blocks of the anchor, in whatever dimension it is (same rules as anchor teleports) |
| Your pets | Don't go through themselves. Following pets catch up as usual; if you crossed to another dimension, they're re-summoned beside you |
| Closes | After 15 s, on sneak + use, when you open another, log out, or the anchor stops being anchored |

Bosses, spectators, and anything riding something else (except you on your chest) stay out.

**Recipe:** Phantom Membrane ×3 around an Ender Chest, Soul Campfire below, Gold Block in each corner. One charm summons every pet.

**Unlocks:** the chest is available from the start. Every other mode unlocks through a vanilla advancement, with no extra recipes:

| Mode | Unlocked by |
|---|---|
| Phantom Tank | *Local Brewery* (brew a potion). *Not Quite "Nine" Lives* (charge a respawn anchor), the unlock before 2.9.0, still counts |
| Phantom Anchor | *Remote Getaway* (enter an End gateway) |
| Phantom Collector | *You've Got a Friend in Me* (have an Allay deliver you an item) |
| Phantom Saddle | *Great View From Up Here* (levitate 50 blocks from a shulker's attacks) |
| Phantom Gateway | *The End... Again...* (respawn the Ender Dragon) |

Until then the charm skips that mode. Creative mode ignores unlocks.

For testing, `/phantomstorage unlock <tank|anchor|collector|saddle|gateway|all>` grants a mode's unlock advancement, so it stays unlocked back in survival. It only works in creative mode. `/advancement revoke` locks a mode again (for the tank, revoke both of its advancements).

## What you get

- 108 slots (12×9) of personal item storage and 4 fluid tanks of 32 buckets each, tied to you rather than to the pets, so they survive death, logout and dimension changes.
- A 3×3 crafting grid, with JEI or REI recipe fill when either is installed.
- A 9-entry void filter: what you list there is destroyed when you or your collector pick it up, and the trash can destroys whatever you hold over it.
- The tank holds any fluid, modded ones included. It's tinted glass in a ghostly ender chest base and lid, with flapping phantom wings and glowing phantom eyes, and rings like amethyst when you open it.
- The tank shows every tank's fluid stacked inside its glass, each layer filling a quarter of the height, with the active tank on top.
- Buckets used on the tank work with the active tank only, unless you tick Overfill or Drain all in the GUI.
- All pets float like an Allay: trail and bob while you travel, drift 2-5 blocks around and above you when you stop, then settle beside you at your foot level.
- Never render darker than a redstone torch (light level 7). With [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) installed they also light their surroundings at level 7.
- The anchor is a small ghostly lodestone whose sides are windows onto the void: the End portal's own starfield, drifting as in the portal block, with the same wings and eyes. The void looks the same by day and night. Anchored, it hangs still with its wings folded.
- The collector is a small orb of the End portal's starfield with the same glowing eyes and no wings; items it takes fly into it.
- One of each pet per player. Only the owner can open or move them, and only `/kill` can kill them.
- Every charm mode shows a pre-render of its pet, drawn as the game draws it, with the same outline.
- Pets are dismissed automatically on logout or dimension change, except an anchored anchor or collector (and following pets brought through your gateway); re-summon them with the charm. Same-dimension teleports (waystones, `/tp`, modded warps) are fine: a pet left behind reappears beside you within a second.

## Client config

Per player, in `config/phantomstorage-client.toml` or in-game under **Mods → Phantom Storage → Config**. Changes show at once.

| Option | Default | Does |
|---|---|---|
| Opacity: All pets | 100% | Multiplies the values below. Every part of a pet fades with it, eyes included, except the End portal void of the anchor, collector and gateway |
| Opacity: Phantom Chest | 80% | How see-through the chest is (10–100%) |
| Opacity: Phantom Tank | 80% | The tank's frame and wings; glass and fluid scale with it |
| Opacity: Phantom Anchor | 80% | How see-through the anchor's stone and wings are (its End portal windows stay solid) |
| Opacity: Phantom Collector | 80% | Dims the collector's eyes (its End portal orb stays solid) |
| Glow: Phantom Chest / Tank / Anchor | on | Never render darker than light level 7 (the tank's and anchor's eyes always glow, dimmed only by opacity) |

Light cast onto surroundings comes from LambDynamicLights and is toggled in its own settings.

## Build

```sh
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
```

Builds are versioned `X.Y.Z-dev` locally and `X.Y.Z-dev.<commit>` in CI; only the Release workflow produces a plain `X.Y.Z` jar, so a test build can't be mistaken for a release.

JEI and REI are optional compile-only dependencies. Uncomment the `localRuntime` line in `build.gradle` to run the dev client with JEI.
