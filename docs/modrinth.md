# Phantom Storage

**Ghostly flying pets that carry your stuff.** Summon a Phantom Chest for items, a Phantom Tank for fluids and a Phantom Anchor to teleport to. They follow you like an Allay, survive everything but `/kill`, and their contents are tied to *you*, not the pet.

---

## 🧳 Phantom Chest

- **108 slots** (12×9) of personal storage
- **3×3 crafting grid**, with one-click recipe fill when JEI or REI is installed
- **Void filter**: list junk once (cobblestone, rotten flesh...) and it's destroyed whenever you or your Collector pick it up; drop anything on the trash can to destroy it
- **Sort** button: by name, amount or mod, ascending or descending, all from one button

## 🫙 Phantom Tank

- **4 tanks × 32 buckets**, any fluid, modded ones included
- A tinted-glass jar in a ghostly ender chest frame, with **flapping phantom wings** and **glowing phantom eyes**. Every fluid shows as a stacked layer inside, so you can colour-arrange it.
- **Buckets, bottles and modded tank items** work in the GUI or straight on the pet
- **Active tank:** pick which tank in-world bucket use fills or drains; the action bar shows its exact level after each use
- **Overfill / Drain all** options: spill into or draw from the next tank automatically. Both are off by default, so nothing goes where you didn't expect.
- Rings like amethyst when you open it

## ⚓ Phantom Anchor

- A ghostly **lodestone with windows onto the End's sky** and phantom wings, following you like the others
- **Teleport to it** with the charm: you land on a safe spot within 3 blocks of it
- Tell it to stay and it's **anchored**: its chunk stays loaded and ticking while you're online, and the charm takes you back to it from anywhere, **even another dimension**
- **Look at it** (up to 32 blocks, through walls) and use the charm to **swap places**
- An anchored anchor stays put through logouts, deaths, dimension changes and restarts
- Right-click it for its **panel**: where it is, a *Stay here* toggle and a dismiss button

## 🌀 Phantom Collector

- A small **orb of the End's sky** with glowing eyes that **gathers dropped items into your Phantom Chest storage**
- Following, it collects round you; **anchored**, it keeps collecting round its spot (a mob farm, say) wherever you are, its chunk loaded while you're online
- **Experience** comes to you while it follows (Mending works); anchored, it's **banked** to take from its panel
- Its panel: **range** (3–12 blocks), a **filter** of what to take or skip, the XP bank, *Stay here* and dismiss
- Never takes what you threw away, and the chest's void filter destroys your listed junk on the way in

## ✨ All pets

- Float like an Allay: trail you while you travel, drift around when you stop, settle at your side
- Look at one for half a second and it comes within reach
- Stay / follow toggle, recall to your side, name them with a name tag
- Owner-only: nobody else can open, move or lead them
- Invulnerable to everything but `/kill`
- Storage lives on the player, so it survives death, logout and dimension changes
- Never darker than light level 7; light up their surroundings with [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights)
- **One look across every panel**: night violet with gold trim, the pet's charm art by the title, cyan for what's active
- **Client config** (in-game from the Mods list): opacity for all pets or each one, and glow on/off per pet

---

## 🎮 Controls

| With the **Phantom Charm** | |
|---|---|
| Sneak + scroll | Switch mode: Chest / Tank / Anchor / Collector |
| Use | Summon, or recall if it's already out. Anchor: teleport to it |
| Use, looking at the anchor | Swap places with it |
| Sneak + use | Dismiss |

| On a pet | |
|---|---|
| Right-click | Open its panel |
| Right-click the tank with a bucket / bottle | Pour in or fill, no GUI |
| Sneak + right-click the tank with a bucket | Cycle the active tank |
| Sneak + right-click (empty hand) | Stay / follow |

## 🛠️ Recipe

One charm for every pet:

| | | |
|:-:|:-:|:-:|
| Gold Block | Phantom Membrane | Gold Block |
| Phantom Membrane | Ender Chest | Phantom Membrane |
| Gold Block | Soul Campfire | Gold Block |

## 🔓 Unlocks

No extra recipes; new pets unlock through vanilla advancements.

| Pet | Unlocked by |
|---|---|
| Phantom Chest | Available from the start |
| Phantom Tank | *Local Brewery* (brew a potion); *Not Quite "Nine" Lives* also still counts |
| Phantom Anchor | *Remote Getaway* (enter an End gateway) |
| Phantom Collector | *You've Got a Friend in Me* (have an Allay deliver you an item) |

Creative mode ignores unlocks. For testing, `/phantomstorage unlock <tank|anchor|collector|all>` (creative only) grants a pet's unlock advancement.

---

## 📦 Requirements

- Minecraft **1.21.1**, NeoForge **21.1.251+**
- Required on **both client and server**
- Optional: **JEI** or **REI** (recipe lookup and crafting-grid fill), **LambDynamicLights**

> **Upgrading from 1.x:** 2.x is a rewrite. 1.x storage, summoners and blocks are not carried over; start fresh or empty your 1.x chest first.
