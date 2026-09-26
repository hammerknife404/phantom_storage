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
- One chest per player. Only its owner can open or move it, and only `/kill` can kill it.
- The chest is dismissed automatically on logout or dimension change; re-summon it with the charm.

## Build

```sh
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
```

JEI and REI are optional compile-only dependencies. Uncomment the `localRuntime` line in `build.gradle` to run the dev client with JEI.
