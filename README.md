# Phantom Storage

A NeoForge 1.21.1 mod: a summonable, ghostly flying chest that follows you like a pet.

## Controls

| Action | Result |
|---|---|
| Use **Phantom Charm** | Summon your chest (5 s cooldown) |
| Sneak + use **Phantom Charm** | Dismiss your chest |
| Right-click chest | Open storage |
| Sneak + right-click chest | Toggle stay / follow |
| Look at chest ~½ s (while idle) | Chest comes within reach |

In the chest's GUI, hover any icon for a short description:

| Icon | Does |
|---|---|
| Sort (bars, after the title) | Merges stacks and orders storage by item |
| Trash (by the void grid) | Destroys everything in the void slots |
| `+` (by the crafting output, JEI/REI only) | Opens crafting recipes; the viewer's own `+` fills the grid from storage and inventory |

**Recipe:** Phantom Membrane ×3 around an Ender Chest, Soul Torch below.

## What you get

- 108 slots (12×9) of personal storage, tied to you rather than to the chest, so it survives death, logout and dimension changes.
- A 3×3 crafting grid, with JEI or REI recipe fill when either is installed.
- A 9-slot void filter: the trash-can button destroys whatever is in it. Items left there when you close the GUI go back to you.
- Floats like an Allay: trails and bobs while you travel, drifts 2-5 blocks around and above you when you stop, then settles beside you at your foot level.
- Never renders darker than a redstone torch (light level 7). With [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) installed it also lights its surroundings at level 7.
- One chest per player. Only its owner can open or move it, and only `/kill` can kill it.
- The chest is dismissed automatically on logout or dimension change; re-summon it with the charm.

## Upgrading from 1.x

1.x chest contents move into v2 storage automatically the first time each player logs in; v2 has 108 slots to 1.x's 54, so they normally all fit. Anything that doesn't fit, or can't be read (e.g. from a removed mod), stays where it was and is retried on every login and chest open. 1.x summoners of any tier load as the Phantom Charm. 1.x filter/refill slots were templates, not items, so they aren't carried over. The wrench, anchor and link blocks have no v2 equivalent and are removed.

## Build

```sh
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
```

Builds are versioned `X.Y.Z-dev` locally and `X.Y.Z-dev.<commit>` in CI; only the Release workflow produces a plain `X.Y.Z` jar, so a test build can't be mistaken for a release.

JEI and REI are optional compile-only dependencies. Uncomment the `localRuntime` line in `build.gradle` to run the dev client with JEI.
