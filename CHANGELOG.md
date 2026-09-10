# Changelog

All notable changes to Cobblemon Cinematics are documented here.

## [1.3.1] - 2026-09-09

### Changed

- The battle camera no longer reacts to walls at all. It keeps its framing distance and angle, so the camera can end up inside or behind a wall instead of being pushed away from it.
- Removed the wall-aware shot selection that scored alternate yaw and pitch angles for clearance and line of sight. That is what made the camera swing away from a wall instead of passing through it.
- The blocks between the battle camera and the fight now fade out over a beam shaped like a torch: a cone opening from the camera that stops at its target and falls off softly at its rim, so the opening has no hard edge. Its core is fully clear, everything past the target renders completely normally, and the ground it stands on is never touched. One beam is cast per subject, so a Pokemon or trainer standing inside a building still gets cleared while the camera is out in the open behind it.
- Faded blocks still cull against each other, so a fading wall stays a single surface rather than a mess of unlit inner faces, and they are reported as air to their unfaded neighbours so those emit the faces that were culled against them - which is what makes the opening see-through.
- Added a `battleOcclusion` config group. The beam is on by default and can be turned off, and its angle, rim softness, depth fade and floor margin are configurable. Turning it off falls back to the original shot selection, which re-aims the camera at an angle that is not blocked instead of pulling the boom into the wall.

### Verification

- `./gradlew build` passes successfully.
- Wall, corner, and low-ceiling scenes still need in-game visual verification, including the chunk-rebuild cost while the camera moves behind walls.

## [1.3.0] - 2026-08-30

### Added

- Added staged Mega Showdown presentations with trainer victory poses and delayed Pokemon reveals.
- Added optional species IDs to the four gimmick test commands, allowing a specific client-side Pokemon to be previewed instead of the default Pikachu.

### Changed

- Updated gimmick presentations to advance Cobblemon poser animations during the cinematic and use the active battle presentation flow for Pokemon reveals and cries.

### Verification

- `./gradlew build` and `git diff --check` pass successfully.

## [1.2.0] - 2026-08-11

### Added

- Added wild-Pokemon battle introductions using the actual opposing Pokemon models and names. Species whitelists and blacklists directly control which wild encounters receive an intro, with legendary and mythical species selected by default.
- Added Trainer NPC intro whitelists and blacklists based on `NPCEntity.resourceIdentifier`; all NPCs are allowed by default and blacklists take priority.
- Added complete client-side test commands for Trainer intros, badge acquisition, Mega Evolution, Dynamax, Z-Moves, Terastallization, and sequential playback. Tests use simulated Trainer, item, and Pokemon inputs and do not require a battle GUI or Mega Showdown installation.
- Added layered, timed sound sequences for Trainer intros and all optional Mega Showdown presentations using existing Minecraft and Cobblemon sound events.

### Changed

- Reworked Mega Showdown presentation actors to use a client-only Cobblemon standard NPC proxy with the native `win` animation, eliminating the vanilla player arm-swing fallback. Pokemon cry playback now uses each rendered species' `PokemonClientDelegate` CryProvider and waits for model initialization before triggering the animation or sound.
- Reorganized the client configuration into `general`, `battleIntro`, `battleCamera`, `megaShowdown`, and `badges` TOML groups. Existing flat 1.1.x configuration keys are replaced with grouped defaults and must be customized again after upgrading.
- Removed the separate `wildBattleIntros` toggle; `battleIntro.wildPokemonWhitelist` and `battleIntro.wildPokemonBlacklist` are now the sole controls for wild intros.
- Reworked battle-camera framing around all active Pokemon using their bounding boxes, current FOV, display aspect ratio, and the actual framing pivot.
- Added environment-aware camera positioning that scores alternate yaw and pitch candidates for clearance and subject visibility while retaining Minecraft's native collision rays as the final safeguard.
- Made collision pull-back smooth while keeping inward collision response immediate, reducing camera popping without allowing wall clipping.
- Scaled intro models to their allocated screen region using entity width and height so oversized Pokemon remain fully visible in single and double presentations.
- Normalized the badge-acquisition audio from approximately -29.7 LUFS to -14.0 LUFS while retaining peak headroom.

### Fixed

- Kept the NeoForge mod metadata version SemVer-compatible while applying the `neoforge1.21.1` platform marker only to the JAR filename, fixing `runClient` startup rejection.
- Kept the dynamic-camera key hint visible during battles even when the camera is disabled, allowing players to discover how to re-enable it.
- Prevented the battle-intro sound-delay filter from swallowing the cinematic's own audio and replaced the inaudible Z-Move cue with a multi-stage activation sequence.
- Fixed wild battles entering the Trainer-intro path and producing an empty presentation without a Pokemon model.
- Continued tracking a fainted Pokemon while its exact UUID remains in the rendered entity list. When one side disappears, the camera never falls back to nearby or name-matched Pokemon, preventing end-of-battle camera flight.

### Verification

- `./gradlew build` and `git diff --check` pass successfully.
- Client initialization and automatic grouped-config correction were verified without Badge Box, Cobblemon Pokemon Badges, Mega Showdown, or Accessories installed.

## [1.1.4] - 2026-08-06

### Fixed

- Restricted the battle-camera toggle key to active Cobblemon battle screens, preventing chat and unrelated screens from changing camera state.

### Verification

- `./gradlew build` passes successfully.

## [1.1.3] - 2026-08-06

### Fixed

- Isolated one-time badge cinematic progress per singleplayer save or multiplayer server and player, so badges obtained in one save no longer suppress the cinematic in another save.

### Verification

- `./gradlew build` passes successfully.

## [1.1.2] - 2026-08-06

### Fixed

- Fixed the badge acquisition sound playing at one quarter of its intended volume by passing the UI sound volume explicitly.

### Verification

- `./gradlew build` passes successfully.

## [1.1.1] - 2026-08-06

### Added

- Added the `pauseDuringCinematics` option to control whether supported cinematic screens pause the game world.

### Fixed

- Fixed the battle-camera key toggle not being handled reliably while the Cobblemon battle screen was focused.
- Fixed duplicate Trainer panels when both double-battle participants resolve to the same NPC or player.

### Verification

- `./gradlew build` passes successfully.

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
- Added English and Simplified Chinese localization for the new controls, messages, and cinematic titles.
- Added README documentation covering installation, configuration, compatibility, and development commands.

### Fixed

- Prevented the battle camera from selecting unrelated or defeated Pokemon after a battle target faints.
- Prevented camera movement from jumping when active Pokemon entities disappear during battle cleanup.
- Added interpolation for camera position, yaw, pitch, and distance to make automated movement smoother.
- Fixed the final-frame cinematic text fade so titles do not briefly flash before disappearing.
- Kept first-person player arms out of battle intro camera shots by switching to third person during the sequence.
- Synchronized the second Trainer's NPC send-out animation with its delayed double-battle entrance.
- Kept optional badge and Mega Showdown integrations from becoming hard dependencies.

### Verification

- `./gradlew clean build` passes successfully.
- Client startup was verified both with optional integrations installed and with Badge Box, Cobblemon Pokemon Badges, Mega Showdown, and Accessories excluded.

## 简体中文摘要

### 1.3.1

- 战斗镜头完全不再对墙壁做出反应：保持原有距离与角度，镜头可以进入或越过墙体，而不是被墙推开。
- 遮挡方块在约半秒内平滑淡出到全透明，而不是瞬间消失：开始挡住构图的方块由原版区块构建器写入半透明渲染层，alpha 从不透明平滑降到全透明，不再遮挡时再平滑恢复；对未淡出的邻居报告为空气，让原本被剔除的相邻面重新生成；完全透明后从网格中彻底移除，避免写入深度遮挡后面的绘制。淡入淡出直接烘焙进区块网格，不依赖额外渲染批次，因此不会出现"没有绘制"的情况。
- 移除按净空与视线评估偏航/俯仰机位的避墙逻辑——正是它让镜头绕开墙而不是穿过去。
- 相机与对战之间挡视线的方块改为按"手电筒光锥"的形状淡出：从相机张开、到目标处截止的圆锥，边缘平滑衰减，所以开口没有生硬的边界。光锥核心完全透明，目标之后的一切完全正常渲染，目标脚下的地面永不处理。每只宝可梦、每位训练师各投一束光锥，因此即使一只在屋内、相机在屋外另一只身后，屋内那只也会被清出视野。
- 淡出的方块彼此之间仍然互相剔除，墙面只呈现为一层表面而不是一堆未受光的内面；同时对未淡出的邻居报告为空气，让原本被剔除的面重新生成——这正是开口能透视的原因。
- 新增 `battleOcclusion` 配置分组。光锥默认开启且可关闭，锥角、边缘柔和度、深度渐变与地面余量均可配置。关闭后回退到原先的机位选择：把镜头换到一个不被墙遮挡的角度，而不是把镜头拉近。
- `./gradlew build` 通过；墙角、低天花板等场景以及镜头贴墙移动时的区块重建开销仍需游戏内目视验证。

### 1.3.0

- 新增分阶段的 Mega Showdown 特效演出，包含训练师胜利姿态与延迟宝可梦显现。
- 四种特殊机制测试命令新增可选物种 ID，可预览指定的仅客户端宝可梦（默认仍为皮卡丘）。
- 更新特殊机制演出流程，在特效期间推进 Cobblemon poser 动画，并按战斗演出流程处理宝可梦显现与吼叫。
- `./gradlew build` 与 `git diff --check` 通过。

### 1.2.0

- 新增野生宝可梦战斗开场，渲染实际对方模型与名称；由物种黑白名单直接控制，默认白名单为神兽与幻兽。
- 新增基于 `NPCEntity.resourceIdentifier` 的训练师 NPC 黑白名单，NPC 默认全部允许，黑名单优先。
- 新增训练师开场、徽章、超级进化、极巨化、Z力量、太晶化及连续播放的完整客户端测试命令；使用模拟参数，不要求战斗 GUI 或安装 Mega Showdown。
- 使用 Minecraft 与 Cobblemon 现有声音事件，为训练师开场和四种特殊机制加入分阶段音效。
- 将客户端配置整理为 `general`、`battleIntro`、`battleCamera`、`megaShowdown` 和 `badges` 五个 TOML 分组；升级后旧版扁平配置会被分组默认值替换，需要重新应用自定义设置。
- 移除独立的 `wildBattleIntros`，改由 `battleIntro.wildPokemonWhitelist` 与 `battleIntro.wildPokemonBlacklist` 完全控制野生开场。
- 战斗镜头根据全部 active Pokémon 的碰撞箱、当前 FOV、屏幕比例和实际焦点自动构图，并评估多个偏航、俯仰机位的净空与主体可见性。
- 保留 Minecraft 原生碰撞射线作为最终保护；撞墙立即收近，离墙平滑拉远。
- 开场模型按实体宽高与分配区域动态缩放，避免大型宝可梦在单打或双打画面中显示不完整。
- 将徽章获得音频从约 -29.7 LUFS 归一化至 -14.0 LUFS，并保留峰值余量。
- 模组元数据继续使用 NeoForge 可解析的语义版本，仅在 JAR 文件名中加入 `neoforge1.21.1` 平台标识，修复 `runClient` 因非法版本号而拒绝启动的问题。
- 在运镜关闭时继续显示动态按键提示，方便玩家发现如何重新启用运镜。
- 修复开场音效被延迟逻辑误拦截、Z 招式提示音不明显，以及野生战斗错误进入训练师开场的问题。
- 宝可梦倒下后，只要同 UUID 模型仍在渲染列表中就继续跟踪；一方消失时不会匹配附近或同名实体，避免战斗结束阶段镜头乱飞。
- `./gradlew build` 与 `git diff --check` 通过，并验证了无全部可选联动模组时的客户端初始化及分组配置自动修正。

### 1.1.4

- 将战斗运镜开关键限制在 Cobblemon 战斗界面，避免在聊天或其他非战斗窗口输入时误触发。
- `./gradlew build` 构建通过。

### 1.1.3

- 将一次性徽章演出进度按单人存档或多人服务器及玩家分别保存，避免旧存档的徽章状态阻止新存档播放特效。
- `./gradlew build` 构建通过。

### 1.1.2

- 修复获得徽章音效实际以四分之一音量播放的问题，现在显式传入音量参数。
- `./gradlew build` 构建通过。

### 1.1.1

- 新增 `pauseDuringCinematics` 配置，可控制支持暂停的过场界面是否暂停游戏世界。
- 修复战斗 Screen 获得焦点时按键无法稳定切换战斗运镜的问题。
- 修复多人双打中同一 NPC 或玩家被重复渲染为两名训练师的问题。
- `./gradlew build` 构建通过。

### 1.1.0

- 在初始 1.0.0 版本基础上扩展战斗演出与模组兼容功能。
- 新增训练师战斗开场、双打训练家分栏开场、NPC 持球动画和动态名称显示。
- 新增平滑战斗运镜、相机碰撞、第三人称切换与原视角恢复。
- 新增读取实际按键绑定的运镜提示。
- 徽章匹配支持精确物品 ID、正则表达式和物品 Tag，Badge Box 与 Cobblemon Pokemon Badges 均为可选联动。
- 新增使用真实徽章物品模型的程序化获得徽章演出。
- 新增 Mega Showdown 可选联动，支持 Mega Evolution、Dynamax、Z-Moves 和 Terastalization 四类演出。
- 修复宝可梦倒下后的镜头乱飞、镜头插值、文字末帧闪烁及双打动画错位问题。
- `./gradlew clean build` 构建通过，并验证了有无可选联动模组两种客户端启动环境。
