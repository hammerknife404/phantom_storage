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

**Recipe:** Phantom Membrane ×3 around an Ender Chest, Soul Sand below.

## What you get

- 108 slots (12×9) of personal storage, tied to you rather than to the chest, so it survives death, logout and dimension changes.
- A 3×3 crafting grid, with JEI's `+` recipe fill when JEI is installed.
- A 9-slot void filter: items inserted there are shown briefly, then destroyed.
- One chest per player. Only its owner can open or move it, and only `/kill` can kill it.
- The chest is dismissed automatically on logout or dimension change; re-summon it with the charm.

## Build

```sh
./gradlew build          # jar in build/libs/, runs unit tests
./gradlew runClient      # dev client
```

JEI is an optional compile-only dependency. Uncomment the `localRuntime` line in `build.gradle` to run the dev client with JEI.
