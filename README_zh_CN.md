# Cobblemon Cinematics

[English](README.md) | 简体中文

Cobblemon Cinematics 是一个面向 Minecraft 1.21.1、NeoForge 和 Cobblemon 1.7.3 的纯客户端演出模组。它为训练师对战和徽章获得流程增加程序化动画、动态镜头与音效，同时避免打包大型背景序列帧。

## 功能

- **训练师对战开场**：进入 Cobblemon 训练师战斗时播放宝可梦风格的高速背景、闪切、训练师名称和 NPC 模型演出。
- **双打战斗开场**：对方阵营存在两位训练家时，使用专用分栏布局同时显示两位 NPC 模型、名称和出战动画。
- **训练师持球动画**：Cobblemon NPC 使用自身的 `send_out` 骨骼动画和精灵球物品挂点；其他训练师实体使用兼容回退动画。
- **战斗镜头**：围绕双方出战宝可梦平滑运镜，并在选择招式后依次聚焦攻击方和目标。
- **动态运镜提示**：自动运镜工作时显示当前实际绑定的镜头开关键。
- **相机碰撞**：镜头距离使用 Minecraft 原生相机碰撞检测缩短，避免穿入墙体或方块。
- **徽章获得演出**：获得匹配物品时播放程序化金色背景、粒子、光环、真实物品模型和获得音效。
- **窗口兼容**：没有打开 Screen 时使用暂停界面播放；已有 Screen 时只在最上层绘制，不阻止原界面交互。
- **可选模组兼容**：不依赖任何特定徽章模组。默认配置可识别 Badge Box 和 Cobblemon Pokemon Badges，也能匹配整合包自定义物品。
- **Mega Showdown 过场**：安装 Mega Showdown 后，为超级进化、极巨化、Z力量和太晶化分别播放不同的纯代码战斗演出。

所有背景、速度线、光柱、粒子、光环和闪光均由代码实时绘制。模组不包含原参考模组的背景图片或徽章贴图。

## 依赖

必需：

- Minecraft 1.21.1
- NeoForge 21.1.x
- Kotlin for Forge 5.12+
- Cobblemon 1.7.3

Badge Box、Cobblemon Pokemon Badges 和 Mega Showdown 均为可选。徽章物品通过可配置规则识别；Mega Showdown 使用运行时检测，未安装时不会加载或引用其代码。

## 配置

客户端配置位于 `config/cobblemoncinematics-client.toml`。

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `battleIntros` | `true` | 启用训练师对战开场 |
| `pauseDuringCinematics` | `true` | 播放过场时在当前界面支持暂停的情况下暂停游戏世界 |
| `delayBattleIntroSounds` | `true` | 将出战与叫声音效延迟到开场结束 |
| `battleCamera` | `true` | 启用动态战斗镜头 |
| `attackCamera` | `true` | 选择行动后聚焦攻击方与目标 |
| `battleCameraHint` | `true` | 自动运镜时显示实际绑定的镜头开关键 |
| `megaEvolutionCinematic` | `true` | 启用可选的超级进化演出 |
| `dynamaxCinematic` | `true` | 启用可选的极巨化演出 |
| `zMoveCinematic` | `true` | 启用可选的 Z力量演出 |
| `terastalizationCinematic` | `true` | 启用可选的太晶化演出 |
| `badgeCinematics` | `true` | 启用徽章获得演出 |
| `badgeOncePerType` | `true` | 每个存档/服务器和玩家的每种徽章只播放一次 |
| `badgeSound` | `true` | 播放徽章获得音效 |
| `badgeItemMatchers` | 见下方 | 定义哪些物品视为徽章 |
| 徽章进度文件 | `config/cobblemoncinematics-badges.json` | 按存档/服务器和玩家分别保存一次性徽章状态 |

### 徽章匹配规则

`badgeItemMatchers` 支持三种规则：

```toml
badgeItemMatchers = [
  "item:examplemod:league_badge",
  "regex:^examplemod:[a-z0-9_]+_badge$",
  "tag:examplemod:badges"
]
```

- `item:`：精确匹配一个物品 ID。
- `regex:`：对完整的 `namespace:path` 进行正则表达式匹配。
- `tag:`：匹配物品标签中的任意物品。

修改规则后 NeoForge 会重新加载客户端配置。一次性徽章进度按存档/服务器和玩家分别保存，因此新存档会独立触发徽章演出。删除 `config/cobblemoncinematics-badges.json` 可清除本地全部进度。

## 操作

- `V`：在战斗中临时启用或关闭动态镜头，可在 Minecraft 控制设置中重新绑定。

## 构建

```bash
./gradlew clean build
```

构建产物位于 `build/libs/cobblemoncinematics-<version>.jar`。

开发环境默认加载可选徽章模组和 Mega Showdown 用于联调。使用以下命令验证完全不安装可选联动模组时的启动：

```bash
./gradlew runClient -PwithoutBadgeMods -PwithoutMegaShowdown
```
