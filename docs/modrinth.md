# Phantom Storage

**Ghostly flying pets that carry your stuff.** Summon a Phantom Chest for items, a Phantom Tank for fluids and a Phantom Anchor to teleport to; saddle the chest and fly it, or open a gateway to your anchor. They follow you like an Allay, survive everything but `/kill`, and their contents are tied to *you*, not the pet.

---

## 🧳 Phantom Chest

- **108 slots** (12×9) of personal storage
- **3×3 crafting grid**, with one-click recipe fill when JEI or REI is installed
- **Void filter**: list junk once (cobblestone, rotten flesh...) and your Collector destroys it instead of storing it; drop anything on the trash can to destroy it
- **Sort** button: by name, amount or mod, ascending or descending, all from one button

## 🫙 Phantom Tank

- **4 tanks × 32 buckets**, any fluid, modded ones included
- A tinted-glass jar in a ghostly ender chest frame, with **flapping phantom wings** and **glowing phantom eyes**. Every fluid shows as a stacked layer inside, so you can colour-arrange it.
- **Buckets, bottles and modded tank items** work in the GUI or straight on the pet
- **Active tank:** pick which tank in-world bucket use fills or drains; the action bar shows its exact level after each use
- **Overfill / Drain all** options: spill into or draw from the next tank automatically. Both are off by default, so nothing goes where you didn't expect.
- Rings like amethyst when you open it

## ⚓ Phantom Anchor

- A ghostly **lodestone with windows onto the End portal's starfield** and phantom wings, following you like the others
- **Teleport to it** with the charm: you land on a safe spot within 3 blocks of it
- Tell it to stay and it's **anchored**: its chunk stays loaded and ticking while you're online, and the charm takes you back to it from anywhere, **even another dimension**
- **Look at it** (up to 32 blocks, through walls) and use the charm to **swap places**
- An anchored anchor stays put through logouts, deaths, dimension changes and restarts
- Right-click it for its **panel**: where it is, a *Stay here* toggle and a dismiss button

## 🌀 Phantom Collector

- A small **orb of the End portal's starfield** with glowing eyes that **gathers dropped items into your Phantom Chest storage**
- Following, it collects round you; **anchored**, it keeps collecting round its spot (a mob farm, say) wherever you are, its chunk loaded while you're online
- **Experience** comes to you while it follows (Mending works); anchored, it's **banked** to take from its panel
- Its panel: **range** (3–12 blocks), a **filter** of what to take or skip, the XP bank, *Stay here* and dismiss
- What you throw yourself waits 5 seconds first (time to hand it to a friend), and the chest's void filter destroys your listed junk on the way in

## 🐎 Phantom Saddle

- A charm mode, no new entity: **ride your Phantom Chest** and fly it, a saddle on its lid
- **Fly where you look**: W/S along your view, A/D to strafe, Space to rise, sprint for double speed; let go and it hovers
- Ridden, it **collides like any mount**, keeping your head out of walls and ceilings
- No fall damage for it or you; sneak or use the charm again to get off

## 🚪 Phantom Gateway

- A **doorway of the End portal's starfield** in a ghostly lodestone frame, the phantom eyes peering out of the void
- Opens in front of you for 15 seconds and leads to your **anchored Phantom Anchor**, **across dimensions**
- **Anyone can step through**: you, friends, mobs, even dropped items
- Your following pets are brought along when you cross to another dimension

## 🔔 Phantom Bell

- One ring **summons every pet you've unlocked**; sneak + ring **sends every following pet home** and closes your gateway
- Anchored pets keep their spots

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
| Sneak + scroll | Switch mode: Chest / Tank / Anchor / Collector / Saddle / Gateway / Bell |
| Use | Summon, or recall if it's already out. Anchor: teleport to it |
| Use, looking at the anchor | Swap places with it |
| Sneak + use | Dismiss (Gateway: close it) |
| Use, Saddle mode | Ride the chest / get off |
| Use, Gateway mode | Open a gateway to your anchored anchor |
| Use / sneak + use, Bell mode | Summon every pet at once / dismiss every following pet |

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

No extra recipes; every charm mode unlocks through a vanilla advancement.

| Mode | Unlocked by |
|---|---|
| Phantom Chest | Available from the start |
| Phantom Tank | *Local Brewery* (brew a potion); *Not Quite "Nine" Lives* also still counts |
| Phantom Anchor | *Remote Getaway* (enter an End gateway) |
| Phantom Collector | *You've Got a Friend in Me* (have an Allay deliver you an item) |
| Phantom Saddle | *Great View From Up Here* (levitate 50 blocks from a shulker's attacks) |
| Phantom Gateway | *The End... Again...* (respawn the Ender Dragon) |
| Phantom Bell | *Hot Tourist Destinations* (explore all Nether biomes) |

Creative mode ignores unlocks. For testing, `/phantomstorage unlock <tank|anchor|collector|saddle|gateway|bell|all>` (creative only) grants a mode's unlock advancement.

---

## 📦 Requirements

- Minecraft **1.21.1**, NeoForge **21.1.251+**
- Required on **both client and server**
- Optional: **JEI** or **REI** (recipe lookup and crafting-grid fill), **LambDynamicLights**

> **Upgrading from 2.x:** 3.0 keeps your storage, tanks and charms.
>
> **Upgrading from 1.x:** 2.x is a rewrite. 1.x storage, summoners and blocks are not carried over; start fresh or empty your 1.x chest first.
