# Cobblemon Cinematics

English | [简体中文](README_zh_CN.md)

Cobblemon Cinematics is a client-side cinematic mod for Minecraft 1.21.1, NeoForge, and Cobblemon 1.7.3. It adds procedural animation, dynamic cameras, and sound effects to Trainer battles and badge acquisition without bundling large background frame sequences.

## Features

- **Trainer battle intros**: Plays a Pokemon-inspired sequence with high-speed backgrounds, flashes, the Trainer's name, and an animated NPC model when a Cobblemon Trainer battle begins.
- **Double-battle intros**: When the opposing side contains two Trainers, both NPC models, names, and send-out animations are presented in a dedicated split layout.
- **Trainer Poke Ball animation**: Cobblemon NPCs use their own `send_out` skeletal animation and Poke Ball item attachment. Other Trainer entities use a compatible fallback animation.
- **Battle camera**: Frames all active Pokemon using their bounding-box size, the current FOV and display aspect ratio, then focuses on the attacker and target after a move is selected.
- **Dynamic camera hint**: Shows the currently bound camera-toggle key during automatic camera operation.
- **Cinematic audio**: Reuses Cobblemon and Minecraft sound events for battle introductions, Mega Evolution, Dynamax, Z-Power, and Terastallization without bundling copyrighted Pokémon game audio.
- **Camera collision**: Does not shorten the boom against walls. The blocks in the way fade out over torch-shaped beams - one per Pokemon and trainer in the fight - that open from the camera, stop at their subject and fall off softly at the rim, so every subject stays clear without a hard edge. The ground the Pokemon stands on is never touched. Configured in the `battleOcclusion` group; disabling it falls back to the original shot selection that re-aims the camera away from walls.
- **Badge acquisition cinematic**: Plays a procedural golden backdrop, particles, rings, the actual badge item model, and an acquisition sound when a matching item is obtained.
- **Screen compatibility**: Pauses the game and opens a dedicated screen when no screen is active. If another screen is already open, the cinematic renders above it without blocking its interaction.
- **Optional mod compatibility**: Does not depend on a specific badge mod. The default configuration recognizes Badge Box and Cobblemon Pokemon Badges items and can also match custom modpack items.
- **Mega Showdown cinematics**: When Mega Showdown is installed, Mega Evolution, Dynamax, Z-Power, and Terastallization receive distinct code-drawn battle presentations.

All backgrounds, speed lines, light beams, particles, rings, and flashes are drawn in real time by code. The mod does not include background images or badge textures from the reference mod.

## Requirements

Required:

- Minecraft 1.21.1
- NeoForge 21.1.x
- Kotlin for Forge 5.12+
- Cobblemon 1.7.3

Badge Box, Cobblemon Pokemon Badges, and Mega Showdown are optional. Badge items are recognized through configurable matching rules. Mega Showdown is detected at runtime and is not loaded or referenced when absent.

## Configuration

The client configuration is stored in `config/cobblemoncinematics-client.toml`.

| Option | Default | Description |
| --- | --- | --- |
| `general.pauseDuringCinematics` | `true` | Pauses the game world while a cinematic is playing when the active screen supports pausing |
| `battleIntro.trainerEnabled` | `true` | Enables Trainer battle intros |
| `battleIntro.trainerNpcWhitelist` | `["*"]` | NPC resource IDs allowed to play Trainer intros; empty or `*` allows all NPCs |
| `battleIntro.trainerNpcBlacklist` | `[]` | NPC resource IDs blocked from Trainer intros; takes priority over the whitelist |
| `battleIntro.wildPokemonWhitelist` | Legendary IDs | Species IDs that receive wild intros; defaults to the configured legendary and mythical list |
| `battleIntro.wildPokemonBlacklist` | `[]` | Species IDs blocked from wild intros; takes priority over the whitelist |
| `battleIntro.delayPokemonSounds` | `true` | Delays send-out and cry sounds until the intro finishes |
| `battleCamera.enabled` | `true` | Enables the dynamic battle camera |
| `battleCamera.attackFocus` | `true` | Focuses on the attacker and target after an action is selected |
| `battleCamera.hint` | `true` | Shows the actual bound camera-toggle key during automatic camera operation |
| `battleOcclusion.enabled` | `true` | Fades the blocks in the way instead of pulling the camera in |
| `battleOcclusion.coneAngle` | `30.0` | Half angle in degrees of the beam that clears the view |
| `battleOcclusion.edgeSoftness` | `0.45` | Fraction of the beam's radius spent on the soft rim |
| `battleOcclusion.fadeWidth` | `1.5` | Blocks past the Pokemon over which the beam fades out |
| `battleOcclusion.floorMargin` | `0.5` | Blocks above the Pokemon's feet that are never faded |
| `megaShowdown.megaEvolution` | `true` | Enables the optional Mega Evolution presentation |
| `megaShowdown.dynamax` | `true` | Enables the optional Dynamax presentation |
| `megaShowdown.zMove` | `true` | Enables the optional Z-Power presentation |
| `megaShowdown.terastalization` | `true` | Enables the optional Terastallization presentation |
| `badges.enabled` | `true` | Enables badge acquisition cinematics |
| `badges.oncePerType` | `true` | Plays the cinematic only once for each badge type per save/server and player |
| `badges.sound` | `true` | Plays the badge acquisition sound |
| `badges.itemMatchers` | See below | Defines which items are treated as badges |
| Badge progress file | `config/cobblemoncinematics-badges.json` | Stores one-time badge state separately for each save/server and player |

Intro whitelist and blacklist entries use complete resource IDs such as `cobblemon:mew`. Blacklists always take priority. An empty whitelist or a whitelist containing `*` allows every ID; a blacklist containing `*` blocks every ID. Wild intros have no separate toggle: the Pokemon lists directly determine which species receive them.

### Badge Matching Rules

`badges.itemMatchers` supports three rule types:

```toml
[badges]
itemMatchers = [
  "item:examplemod:league_badge",
  "regex:^examplemod:[a-z0-9_]+_badge$",
  "tag:examplemod:badges"
]
```

- `item:` matches one exact item ID.
- `regex:` matches a regular expression against the complete `namespace:path` ID.
- `tag:` matches any item in the specified item tag.

NeoForge reloads the client configuration after the rules are changed. One-time badge progress is stored per save/server and player, so a new save starts with its own badge presentations. Delete `config/cobblemoncinematics-badges.json` to clear all local progress.

## Controls

- `V`: Temporarily enables or disables the dynamic camera during battle. This key can be rebound in Minecraft's control settings.

### Cinematic Test Commands

These client commands can be run from chat anywhere in a loaded world. They open an isolated test screen and replay the complete visual cinematic, including its model rendering, procedural effects, title, and sound:

```text
/cobblemoncinematics test battle_intro
/cobblemoncinematics test badge
/cobblemoncinematics test mega
/cobblemoncinematics test dynamax
/cobblemoncinematics test zmove
/cobblemoncinematics test terastalization
/cobblemoncinematics test all
```

The four gimmick commands also accept an optional species ID, for example `/cobblemoncinematics test mega cobblemon:charizard`. The intro uses the local player as a test Trainer, the badge test uses a temporary Nether Star, and gimmick tests render the requested client-only Pokemon (Pikachu by default). Explicit test commands bypass feature toggles and do not require Mega Showdown because they only exercise this mod's presentation layer; automatic triggering from real battles remains available only when Mega Showdown is installed and respects every corresponding configuration option. `all` plays every test cinematic sequentially.

## Building

```bash
./gradlew clean build
```

The resulting JAR is written to `build/libs/cobblemoncinematics-<version>.jar`.

The development environment loads the optional badge mods and Mega Showdown by default for integration testing. Use the following command to verify startup without any optional integration mod:

```bash
./gradlew runClient -PwithoutBadgeMods -PwithoutMegaShowdown
```
