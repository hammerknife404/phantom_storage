# Phantom Storage

A NeoForge 1.21.1 mod: summonable, ghostly flying pets that carry your stuff. The Phantom Chest holds items, the Phantom Tank holds fluids, and you can have both out at once.

## Controls

| Action | Result |
|---|---|
| Sneak + scroll with the **Phantom Charm** | Switch its mode: Chest or Tank (the charm's art changes to match) |
| Use the charm | Summon that pet, or recall it to your side if it's already out (5 s cooldown) |
| Sneak + use the charm | Dismiss that pet (same cooldown) |
| Right-click a pet | Open its storage |
| Right-click the tank with a bucket, bottle (water, 250 mB) or other fluid container | Pour into, or fill from, the active tank without opening the GUI; the action bar shows that tank's level |
| Sneak + right-click the tank holding a fluid container | Cycle the active tank |
| Sneak + right-click a pet (empty-handed) | Toggle stay / follow |
| Look at a pet ~½ s (while idle) | It comes within reach |

In either GUI, hover any icon or gauge for a short description.

Chest:

| Icon | Does |
|---|---|
| Sort (bars, after the title) | Click: merge stacks and sort. Right-click or scroll: mode (Name, Amount, Mod). Shift + click: ascending / descending (the bars flip). Saved with your storage |
| Trash (by the void grid) | Destroys everything in the void slots |
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

**Recipe:** Phantom Membrane ×3 around an Ender Chest, Soul Campfire below, Gold Block in each corner. One charm summons every pet.

**Unlocks:** the chest is available from the start. Other pets unlock through vanilla advancements, with no extra recipes:

| Pet | Unlocked by |
|---|---|
| Phantom Tank | *Not Quite "Nine" Lives* (charge a respawn anchor) |

Until then the charm skips that mode. Creative mode ignores unlocks.

## What you get

- 108 slots (12×9) of personal item storage and 4 fluid tanks of 32 buckets each, tied to you rather than to the pets, so they survive death, logout and dimension changes.
- A 3×3 crafting grid, with JEI or REI recipe fill when either is installed.
- A 9-slot void filter: the trash-can button destroys whatever is in it. Items left there when you close the GUI go back to you.
- The tank holds any fluid, modded ones included. It's tinted glass in a ghostly ender chest base and lid, with flapping phantom wings and glowing phantom eyes, and rings like amethyst when you open it.
- The tank shows every tank's fluid stacked inside its glass, each layer filling a quarter of the height, with the active tank on top.
- Buckets used on the tank work with the active tank only, unless you tick Overfill or Drain all in the GUI.
- Both pets float like an Allay: trail and bob while you travel, drift 2-5 blocks around and above you when you stop, then settle beside you at your foot level.
- Never render darker than a redstone torch (light level 7). With [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) installed they also light their surroundings at level 7.
- One chest and one tank per player. Only the owner can open or move them, and only `/kill` can kill them.
- Pets are dismissed automatically on logout or dimension change; re-summon them with the charm.

## Client config

Per player, in `config/phantomstorage-client.toml` or in-game under **Mods → Phantom Storage → Config**. Changes show at once.

| Option | Default | Does |
|---|---|---|
| Opacity: All pets | 100% | Multiplies the two values below |
| Opacity: Phantom Chest | 80% | How see-through the chest is (10–100%) |
| Opacity: Phantom Tank | 80% | The tank's frame and wings; glass and fluid scale with it |
| Glow: Phantom Chest / Tank | on | Never render darker than light level 7 (the tank's eyes always glow) |

Light cast onto surroundings comes from LambDynamicLights and is toggled in its own settings.

## Build

```sh
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
```

Builds are versioned `X.Y.Z-dev` locally and `X.Y.Z-dev.<commit>` in CI; only the Release workflow produces a plain `X.Y.Z` jar, so a test build can't be mistaken for a release.

JEI and REI are optional compile-only dependencies. Uncomment the `localRuntime` line in `build.gradle` to run the dev client with JEI.
