# Changelog

All notable changes to Cobblemon Cinematics are documented here.

## [1.1.0] - 2026-08-04

### Added

- Expanded the initial 1.0.0 release with additional battle presentation and compatibility features.
- Added procedural Trainer battle introductions with NPC model rendering, names, flashes, backgrounds, and send-out animation support.
- Added double-battle introductions that present two opposing Trainers in a split layout with independent names, palettes, and staggered animations.
- Added smooth directed battle camera movement with native Minecraft camera collision handling.
- Added automatic third-person switching during battle camera sequences and restoration of the player's previous perspective afterward.
- Added dynamic battle-camera key hints that read the player's current key binding.
- Added configurable badge-item detection using exact item IDs, regular expressions, and item tags.
- Added default badge matching rules for Badge Box and Cobblemon Pokemon Badges items without requiring either mod.
- Added procedural badge-acquisition cinematics using the actual badge item model, code-drawn effects, screen overlay support, and configurable sound playback.
- Added optional Mega Showdown compatibility for Mega Evolution, Dynamax, Z-Moves, and Terastallization cinematics.
- Added independent client configuration toggles for each battle, badge, camera, and Mega Showdown feature.
- Added the `pauseDuringCinematics` option to control whether supported cinematic screens pause the game world.
- Added English and Simplified Chinese localization for the new controls, messages, and cinematic titles.
- Added README documentation covering installation, configuration, compatibility, and development commands.

### Fixed

- Prevented the battle camera from selecting unrelated or defeated Pokemon after a battle target faints.
- Prevented camera movement from jumping when active Pokemon entities disappear during battle cleanup.
- Added interpolation for camera position, yaw, pitch, and distance to make automated movement smoother.
- Fixed the final-frame cinematic text fade so titles do not briefly flash before disappearing.
- Kept first-person player arms out of battle intro camera shots by switching to third person during the sequence.
- Synchronized the second Trainer's NPC send-out animation with its delayed double-battle entrance.
- Fixed the battle-camera key toggle not being handled reliably while the Cobblemon battle screen was focused.
- Kept optional badge and Mega Showdown integrations from becoming hard dependencies.

### Verification

- `./gradlew clean build` passes successfully.
- Client startup was verified both with optional integrations installed and with Badge Box, Cobblemon Pokemon Badges, Mega Showdown, and Accessories excluded.

## 简体中文摘要

### 1.1.0

- 在初始 1.0.0 版本基础上扩展战斗演出与模组兼容功能。
- 新增训练师战斗开场、双打训练家分栏开场、NPC 持球动画和动态名称显示。
- 新增平滑战斗运镜、相机碰撞、第三人称切换与原视角恢复。
- 新增读取实际按键绑定的运镜提示。
- 徽章匹配支持精确物品 ID、正则表达式和物品 Tag，Badge Box 与 Cobblemon Pokemon Badges 均为可选联动。
- 新增使用真实徽章物品模型的程序化获得徽章演出。
- 新增 Mega Showdown 可选联动，支持 Mega Evolution、Dynamax、Z-Moves 和 Terastalization 四类演出。
- 新增 `pauseDuringCinematics` 配置，可控制支持暂停的过场界面是否暂停游戏世界。
- 修复宝可梦倒下后的镜头乱飞、镜头插值、文字末帧闪烁及双打动画错位问题。
- 修复战斗 Screen 获得焦点时按键无法稳定切换战斗运镜的问题。
- `./gradlew clean build` 构建通过，并验证了有无可选联动模组两种客户端启动环境。
