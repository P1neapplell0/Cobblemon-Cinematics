# Cobblemon Cinematics

English | [简体中文](README_zh_CN.md)

Cobblemon Cinematics is a client-side cinematic mod for Minecraft 1.21.1, NeoForge, and Cobblemon 1.7.3. It adds procedural animation, dynamic cameras, and sound effects to Trainer battles and badge acquisition without bundling large background frame sequences.

## Features

- **Trainer battle intros**: Plays a Pokemon-inspired sequence with high-speed backgrounds, flashes, the Trainer's name, and an animated NPC model when a Cobblemon Trainer battle begins.
- **Double-battle intros**: When the opposing side contains two Trainers, both NPC models, names, and send-out animations are presented in a dedicated split layout.
- **Trainer Poke Ball animation**: Cobblemon NPCs use their own `send_out` skeletal animation and Poke Ball item attachment. Other Trainer entities use a compatible fallback animation.
- **Battle camera**: Smoothly orbits the active Pokemon on both sides and focuses on the attacker and target after a move is selected.
- **Dynamic camera hint**: Shows the currently bound camera-toggle key during automatic camera operation.
- **Camera collision**: Uses Minecraft's native camera collision detection to shorten camera distance and prevent clipping into walls or blocks.
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
| `battleIntros` | `true` | Enables Trainer battle intros |
| `pauseDuringCinematics` | `true` | Pauses the game world while a cinematic is playing when the active screen supports pausing |
| `delayBattleIntroSounds` | `true` | Delays send-out and cry sounds until the intro finishes |
| `battleCamera` | `true` | Enables the dynamic battle camera |
| `attackCamera` | `true` | Focuses on the attacker and target after an action is selected |
| `battleCameraHint` | `true` | Shows the actual bound camera-toggle key during automatic camera operation |
| `megaEvolutionCinematic` | `true` | Enables the optional Mega Evolution presentation |
| `dynamaxCinematic` | `true` | Enables the optional Dynamax presentation |
| `zMoveCinematic` | `true` | Enables the optional Z-Power presentation |
| `terastalizationCinematic` | `true` | Enables the optional Terastallization presentation |
| `badgeCinematics` | `true` | Enables badge acquisition cinematics |
| `badgeOncePerType` | `true` | Plays the cinematic only once for each badge type |
| `badgeSound` | `true` | Plays the badge acquisition sound |
| `badgeItemMatchers` | See below | Defines which items are treated as badges |
| `obtainedBadgeIds` | `[]` | Stores badge IDs whose one-time cinematic has already played |

### Badge Matching Rules

`badgeItemMatchers` supports three rule types:

```toml
badgeItemMatchers = [
  "item:examplemod:league_badge",
  "regex:^examplemod:[a-z0-9_]+_badge$",
  "tag:examplemod:badges"
]
```

- `item:` matches one exact item ID.
- `regex:` matches a regular expression against the complete `namespace:path` ID.
- `tag:` matches any item in the specified item tag.

NeoForge reloads the client configuration after the rules are changed. Clear `obtainedBadgeIds` to replay cinematics for badges that have already been shown.

## Controls

- `V`: Temporarily enables or disables the dynamic camera during battle. This key can be rebound in Minecraft's control settings.

## Building

```bash
./gradlew clean build
```

The resulting JAR is written to `build/libs/cobblemoncinematics-<version>.jar`.

The development environment loads the optional badge mods and Mega Showdown by default for integration testing. Use the following command to verify startup without any optional integration mod:

```bash
./gradlew runClient -PwithoutBadgeMods -PwithoutMegaShowdown
```
