# Pet Behavior

What every pet (chest, tank, anchor, collector) shares.

## Following

- Pets float like an Allay: they trail and bob while you travel, drift 2-5 blocks around and above you when you stop, then settle beside you at your foot level.
- Look at a pet for about half a second while it's idle and it comes within reach.
- Beyond 12 blocks a pet blinks back to you. Same-dimension teleports (waystones, `/tp`, modded warps) are fine: a pet left behind reappears beside you within a second.

## Controls on a pet

| Action | Result |
|---|---|
| Right-click | Open its panel |
| Sneak + right-click (empty hand) | Toggle stay / follow. The anchor and collector are *anchored* when told to stay |
| Name tag | Name it. An anchored pet keeps its name through logouts and restarts; a dismissed pet comes back unnamed |

## Ownership and durability

- One of each pet per player. Only the owner can open, move or lead it.
- Only `/kill` can kill a pet. Its contents are never at risk: storage and tanks live on you, not on the pet, so they survive death, logout and dimension changes.
- Pets are never saved with the world. They're dismissed on logout or dimension change and summoned again with the charm, except an anchored anchor or collector, which keeps its spot (see [Phantom Anchor](Phantom-Anchor)).

## Panels

All panels share one look: night violet in a gold-trimmed frame, slot groups in darker wells, the pet's charm art beside the title, and cyan for whatever is active or selected. Hover any icon, gauge or checkbox for a short description.

## Light

Pets never render darker than a redstone torch (light level 7); each pet's glow can be turned off in the [Client Config](Client-Config). With [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) they also light their surroundings.
