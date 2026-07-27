# Cobblemon Cinematics

[English](README.md) | 简体中文

Cobblemon Cinematics 是一个面向 Minecraft 1.21.1、NeoForge 和 Cobblemon 1.7.3 的纯客户端演出模组。它为训练师对战和徽章获得流程增加程序化动画、动态镜头与音效，同时避免打包大型背景序列帧。

## 功能

- **训练师对战开场**：进入 Cobblemon 训练师战斗时播放宝可梦风格的高速背景、闪切、训练师名称和 NPC 模型演出。
- **训练师持球动画**：Cobblemon NPC 使用自身的 `send_out` 骨骼动画和精灵球物品挂点；其他训练师实体使用兼容回退动画。
- **战斗镜头**：围绕双方出战宝可梦平滑运镜，并在选择招式后依次聚焦攻击方和目标。
- **相机碰撞**：镜头距离使用 Minecraft 原生相机碰撞检测缩短，避免穿入墙体或方块。
- **徽章获得演出**：获得匹配物品时播放程序化金色背景、粒子、光环、真实物品模型和获得音效。
- **窗口兼容**：没有打开 Screen 时使用暂停界面播放；已有 Screen 时只在最上层绘制，不阻止原界面交互。
- **可选模组兼容**：不依赖任何特定徽章模组。默认配置可识别 Badge Box 和 Cobblemon Pokemon Badges，也能匹配整合包自定义物品。

所有背景、速度线、光柱、粒子、光环和闪光均由代码实时绘制。模组不包含原参考模组的背景图片或徽章贴图。

## 依赖

必需：

- Minecraft 1.21.1
- NeoForge 21.1.x
- Kotlin for Forge 5.12+
- Cobblemon 1.7.3

Badge Box 和 Cobblemon Pokemon Badges 均为可选。安装后会通过默认物品匹配规则自动生效，不存在 API 或加载依赖。

## 配置

客户端配置位于 `config/cobblemoncinematics-client.toml`。

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `battleIntros` | `true` | 启用训练师对战开场 |
| `delayBattleIntroSounds` | `true` | 将出战与叫声音效延迟到开场结束 |
| `battleCamera` | `true` | 启用动态战斗镜头 |
| `attackCamera` | `true` | 选择行动后聚焦攻击方与目标 |
| `badgeCinematics` | `true` | 启用徽章获得演出 |
| `badgeOncePerType` | `true` | 每种徽章只播放一次 |
| `badgeSound` | `true` | 播放徽章获得音效 |
| `badgeItemMatchers` | 见下方 | 定义哪些物品视为徽章 |
| `obtainedBadgeIds` | `[]` | 已播放过的一次性徽章 ID |

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

修改规则后 NeoForge 会重新加载客户端配置。清空 `obtainedBadgeIds` 可重新播放已经看过的徽章演出。

## 操作

- `V`：在战斗中临时启用或关闭动态镜头，可在 Minecraft 控制设置中重新绑定。

## 构建

```bash
./gradlew clean build
```

构建产物位于 `build/libs/cobblemoncinematics-<version>.jar`。

开发环境默认加载两个可选徽章模组用于联调。使用以下命令验证不安装徽章模组时的启动：

```bash
./gradlew runClient -PwithoutBadgeMods
```