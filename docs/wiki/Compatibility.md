# Compatibility

| Mod | Notes |
|---|---|
| JEI / REI (optional) | Recipe lookup, and the chest's crafting grid fills from storage and inventory through the viewer's `+` |
| LambDynamicLights (optional) | Pets and the gateway light their surroundings |
| Item-transport mods | Items they mark as not to be moved (`PreventRemoteMovement`) are left alone by the collector |
| Waystones and other warps | Same-dimension teleports are fine; a pet left behind reappears beside you within a second |
| Iris / Sodium | The void of the anchor, collector and gateway is drawn with the same render passes as the pets' eyes, which shader packs handle. Up to 3.0.0 it used vanilla's End portal shader, which shader packs don't support on entities, so it could vanish until a restart |

The mod is required on both client and server.

## Upgrading

- **From 2.x:** 3.0 keeps your storage, tanks and charms.
- **From 1.x:** 2.x was a rewrite. 1.x storage, summoners and blocks are not carried over; empty your 1.x chest first.
