# Phantom Storage

A NeoForge 1.21.1 mod: summonable, ghostly flying pets that carry your stuff. The Phantom Chest holds items, the Phantom Tank holds fluids, and you can have both out at once.

## Controls

| Action | Result |
|---|---|
| Use **Phantom Charm** / **Phantom Flask** | Summon your chest / tank, or recall it to your side if it's already out (5 s cooldown) |
| Sneak + use the charm / flask | Dismiss that pet (same cooldown) |
| Right-click a pet | Open its storage |
| Right-click the tank with a bucket (or any fluid container) | Pour in, or fill an empty one, without opening the GUI |
| Sneak + right-click a pet | Toggle stay / follow |
| Look at a pet ~½ s (while idle) | It comes within reach |

In either GUI, hover any icon or gauge for a short description.

Chest:

| Icon | Does |
|---|---|
| Sort (bars, after the title) | Merges stacks and orders storage by item |
| Trash (by the void grid) | Destroys everything in the void slots |
| `+` (by the crafting output, JEI/REI only) | Opens crafting recipes; the viewer's own `+` fills the grid from storage and inventory |

Tank:

| Control | Does |
|---|---|
| Gauge, left-click with a filled container | Pours it into that tank |
| Gauge, right-click with an empty container | Fills it from that tank |
| Trash (under each gauge) | Destroys the fluid in that tank |

**Recipes:** Phantom Membrane ×3 around the centre item, Soul Torch below, Gold Ingot in each corner. Centre: Ender Chest for the Phantom Charm, Cauldron for the Phantom Flask.

## What you get

- 108 slots (12×9) of personal item storage and 4 fluid tanks of 32 buckets each, tied to you rather than to the pets, so they survive death, logout and dimension changes.
- A 3×3 crafting grid, with JEI or REI recipe fill when either is installed.
- A 9-slot void filter: the trash-can button destroys whatever is in it. Items left there when you close the GUI go back to you.
- The tank shows its fullest fluid inside its glass, filled to the overall level.
- Both pets float like an Allay: trail and bob while you travel, drift 2-5 blocks around and above you when you stop, then settle beside you at your foot level.
- Never render darker than a redstone torch (light level 7). With [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) installed they also light their surroundings at level 7.
- One chest and one tank per player. Only the owner can open or move them, and only `/kill` can kill them.
- Pets are dismissed automatically on logout or dimension change; re-summon them with the charm or flask.

## Build

```sh
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
```

Builds are versioned `X.Y.Z-dev` locally and `X.Y.Z-dev.<commit>` in CI; only the Release workflow produces a plain `X.Y.Z` jar, so a test build can't be mistaken for a release.

JEI and REI are optional compile-only dependencies. Uncomment the `localRuntime` line in `build.gradle` to run the dev client with JEI.
