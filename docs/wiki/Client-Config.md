# Client Config

Per player, in `config/phantomstorage-client.toml` or in-game under **Mods → Phantom Storage → Config**. Changes show at once.

| Option | Default | Does |
|---|---|---|
| Opacity: All pets | 100% | Multiplies the values below. Every part of a pet fades with it, eyes included, except the End portal void of the anchor, collector and gateway |
| Opacity: Phantom Chest | 80% | How see-through the chest is (10–100%) |
| Opacity: Phantom Tank | 80% | The tank's frame and wings; glass and fluid scale with it |
| Opacity: Phantom Anchor | 80% | The anchor's stone and wings, and the gateway's frame (End portal windows stay solid) |
| Opacity: Phantom Collector | 80% | Dims the collector's eyes (its End portal orb stays solid) |
| Glow: Phantom Chest / Tank / Anchor | on | Never render darker than light level 7; the anchor's setting also covers the gateway's frame (the eyes always glow, dimmed only by opacity) |

Light cast onto surroundings comes from [LambDynamicLights](https://modrinth.com/mod/lambdynamiclights) and is toggled in its own settings.
