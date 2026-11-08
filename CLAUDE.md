# Phantom Storage: notes for the next agent

NeoForge 1.21.1 mod (Java 21, ModDevGradle). One item, the **Phantom Charm**, with seven modes: four pets (chest, tank, anchor, collector) and three modes that use them (saddle, gateway, bell). Released as **3.0.0** (tag `v3.0.0`). Player docs are in `docs/wiki/`, synced to the GitHub wiki; read [Home](docs/wiki/Home.md) for what each mode does.

## Working here

- **Network:** cloud sessions need the `phantom_storage` environment, which allows `maven.neoforged.net`, the Mojang hosts and the JEI/REI/Architectury mavens. On the Default environment Gradle can't resolve NeoForge.
- **Build:** `./gradlew build` compiles and runs the unit tests (234, game-free: panel layouts and stack compacting). CI (`build.yml`) runs the same on every PR and push to `main`.
- **PRs:** squash-merged into `main`, titled like `3.0.0: summary (#21)`. Bump `mod_version` in `gradle.properties` with user-visible changes. Keep `docs/wiki/`, `docs/modrinth.md` and the release notes body in `.github/workflows/release.yml` in step with behavior changes.
- **Release:** pushing tag `v<mod_version>` runs `release.yml`. Sessions here can't push tags (the push is cut off), so run the Release workflow manually on `main` instead (GitHub MCP `actions_run_trigger`, `release.yml`, ref `main`). It creates the tag on that commit and publishes the jar. It refuses to overwrite an existing release.
- **Modrinth:** the project description is `docs/modrinth.md`, pasted by hand. No Modrinth token is configured.
- **Wiki:** edit `docs/wiki/*.md` (page = file name, `_Sidebar.md` is the sidebar); `wiki.yml` mirrors it to the wiki on push to `main`. Never edit the wiki on github.com: the next sync overwrites it.
- **Force pushes are refused here.** After a squash merge, restart the work branch from `main` and, if the remote branch still holds the merged commits, merge that tip in (its content equals `main`) so the push is a fast-forward.

## Testing in the real game

Unit tests can't touch Minecraft, so behavior was checked in the dev client under Xvfb with a scripted harness:

1. `cp tools/devharness/Harness.java src/main/java/com/phantomstorage/devharness/` (that folder is gitignored)
2. `mkdir -p run && printf 'onboardAccessibility:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n' > run/options.txt` (else the first-launch screen blocks it)
3. `PHANTOM_HARNESS=collector timeout 700 xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient`
4. Read `[HARNESS]` lines in the output (or `run/harness.log`) and `run/screenshots/*.png`
5. **Delete the harness folder before any release build**; it compiles into the jar.

Harness notes: `step(n, r)` waits n ticks, then runs r. The charm's cooldown is shared, so `use()` clears it first. The server only knows the selected slot if you set it on the server player too. Server-side checks go through `server(...)` (blocks on the server thread).

## Code map (`src/main/java/com/phantomstorage`)

| Where | What |
|---|---|
| `summon/CharmMode` | The charm's modes: unlock advancements, display names. Saved by name, sent by ordinal; new modes go **at the end** (the item model picks art by ordinal) |
| `summon/PetKind` | The four pets: entity type, menu, whether it can anchor |
| `item/PhantomCharmItem` | Use / sneak-use dispatch per mode, tooltip, mode cycling, shared cooldown |
| `summon/PetManager` | One pet of each kind per player; summon, recall, dismiss, stranded-pet rescue, `bringAlong` after a gateway crossing |
| `summon/AnchorManager`, `AnchorTravel` | Anchored pets: spot on the player (`AnchorSpot`), chunk tickets per kind, re-created after login; safe-landing search |
| `summon/CollectorWork` | What the collector takes and where it goes |
| `summon/PhantomSaddle`, `GatewayManager`, `PhantomBell` | The non-pet modes |
| `entity/PhantomPetEntity` | Base pet: flight, never saved, only `/kill` hurts it; riding (look + WASD, collides only while ridden) |
| `entity/PhantomGatewayEntity` | The gateway doorway; travel logic is in `GatewayManager` |
| `registry/ModRegistries` | Items, entities, menus, the charm-mode component, player attachments (storage, tanks, anchor and collector spots, collector settings) |
| `client/` | Renderers (`GhostQuads` shared wings/eyes/quads; `PhantomRenderTypes` ghost passes; the voids draw `RenderType.endPortal()`), screens on `PhantomTheme`, client config |
| `menu/` | Menus; pixel layouts (`PhantomLayout`, `TankLayout`, `AnchorLayout`, `CollectorLayout`) are game-free and unit-tested |

Pets hold no contents; everything lives on the player as attachments. That's why a pet can vanish, be re-created or be replaced at any time without loss.

## Adding a charm mode

1. `CharmMode`: a constant at the end, with its unlock advancement.
2. A handler returning a cooldown in ticks or `PhantomCharmItem.FAILED`; wire it into `PhantomCharmItem.use` and the tooltip switch.
3. Lang: `pet.phantomstorage.<mode>` (short name), `charm.phantomstorage.<mode>` (full name), tooltip and message keys.
4. Item model `phantom_charm_<mode>.json` and an override in `phantom_charm.json` at the new ordinal; a sprite (below).
5. `/phantomstorage unlock` picks it up on its own. Update `docs/wiki`, `docs/modrinth.md` and the release notes body.

## Charm sprites

Every mode's art is a 64×64 pre-render "as the game draws it": a 2:1 isometric view at a 30° pitch (vertical squashed to 0.875), face shading top 1.0 / front 0.82 / side 0.62, a 1-px `#120C1E` outline, front (+z) on the left. `tools/sprites/make_sprites.py` regenerates the chest, saddle, gateway and bell sprites pixel-identically into `build/sprites/`; `void_sprites.py` paints the End portal starfield into the anchor's and collector's. Both read vanilla textures from the NeoForge artifacts jar, so run a build first; they need Python 3 with Pillow.

## Open items

- Not yet checked: riding on a dedicated server with others watching; other players through the gateway; Iris/Sodium with the End portal shader on entities; earning the Saddle/Gateway/Bell advancements in survival.
- The bell's sneak-use leaves anchored pets alone; the owner accepted that "for now".
- The End portal void is opaque by design of the vanilla shader, so it ignores the opacity setting (documented).
