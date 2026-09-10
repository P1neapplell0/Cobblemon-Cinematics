# Cobblemon Cinematics

[English](README.md) | 简体中文

Cobblemon Cinematics 是一个面向 Minecraft 1.21.1、NeoForge 和 Cobblemon 1.7.3 的纯客户端演出模组。它为训练师对战和徽章获得流程增加程序化动画、动态镜头与音效，同时避免打包大型背景序列帧。

## 功能

- **训练师对战开场**：进入 Cobblemon 训练师战斗时播放宝可梦风格的高速背景、闪切、训练师名称和 NPC 模型演出。
- **双打战斗开场**：对方阵营存在两位训练家时，使用专用分栏布局同时显示两位 NPC 模型、名称和出战动画。
- **训练师持球动画**：Cobblemon NPC 使用自身的 `send_out` 骨骼动画和精灵球物品挂点；其他训练师实体使用兼容回退动画。
- **战斗镜头**：根据全部出战宝可梦的碰撞箱体型、当前 FOV 和屏幕宽高比自动构图，并在选择招式后依次聚焦攻击方和目标。
- **动态运镜提示**：自动运镜工作时显示当前实际绑定的镜头开关键。
- **过场音效**：复用 Cobblemon 与 Minecraft 的现有音效事件，为战斗开场、超级进化、极巨化、Z力量和太晶化提供音效，不额外打包宝可梦原游戏音频。
- **相机碰撞**：不再因撞墙收近镜头，挡视线的方块按手电筒光锥的形状淡出——每只宝可梦、每位训练师各一束，从相机张开、到目标处截止、边缘柔和衰减，所有目标都保持清晰且没有生硬边界；宝可梦脚下的地面永不处理。相关开关与参数在 `battleOcclusion` 分组，关闭后回退到原先"把镜头换到不被遮挡角度"的机位选择。
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
| `general.pauseDuringCinematics` | `true` | 播放过场时在当前界面支持暂停的情况下暂停游戏世界 |
| `battleIntro.trainerEnabled` | `true` | 启用训练师对战开场 |
| `battleIntro.trainerNpcWhitelist` | `["*"]` | 允许播放训练师开场的 NPC 资源 ID；空列表或 `*` 表示允许全部 NPC |
| `battleIntro.trainerNpcBlacklist` | `[]` | 禁止播放训练师开场的 NPC 资源 ID；优先级高于白名单 |
| `battleIntro.wildPokemonWhitelist` | 神兽 ID | 播放野生开场的物种 ID；默认使用配置内置的神兽与幻兽列表 |
| `battleIntro.wildPokemonBlacklist` | `[]` | 禁止播放野生开场的物种 ID；优先级高于白名单 |
| `battleIntro.delayPokemonSounds` | `true` | 将出战与叫声音效延迟到开场结束 |
| `battleCamera.enabled` | `true` | 启用动态战斗镜头 |
| `battleCamera.attackFocus` | `true` | 选择行动后聚焦攻击方与目标 |
| `battleCamera.hint` | `true` | 自动运镜时显示实际绑定的镜头开关键 |
| `battleOcclusion.enabled` | `true` | 淡出挡视线的方块，而不是把镜头拉近 |
| `battleOcclusion.coneAngle` | `30.0` | 清除视线的光锥半角（度） |
| `battleOcclusion.edgeSoftness` | `0.45` | 光锥半径中用于柔和边缘的比例 |
| `battleOcclusion.fadeWidth` | `1.5` | 宝可梦之后多少格内光锥完全淡出 |
| `battleOcclusion.floorMargin` | `0.5` | 宝可梦脚下以上多少格内的方块永不淡出 |
| `megaShowdown.megaEvolution` | `true` | 启用可选的超级进化演出 |
| `megaShowdown.dynamax` | `true` | 启用可选的极巨化演出 |
| `megaShowdown.zMove` | `true` | 启用可选的 Z力量演出 |
| `megaShowdown.terastalization` | `true` | 启用可选的太晶化演出 |
| `badges.enabled` | `true` | 启用徽章获得演出 |
| `badges.oncePerType` | `true` | 每个存档/服务器和玩家的每种徽章只播放一次 |
| `badges.sound` | `true` | 播放徽章获得音效 |
| `badges.itemMatchers` | 见下方 | 定义哪些物品视为徽章 |
| 徽章进度文件 | `config/cobblemoncinematics-badges.json` | 按存档/服务器和玩家分别保存一次性徽章状态 |

开场黑白名单使用 `cobblemon:mew` 这类完整资源 ID。黑名单始终优先；白名单为空或包含 `*` 时允许全部 ID，黑名单包含 `*` 时禁止全部 ID。野生开场不再有单独开关，由宝可梦黑白名单直接决定哪些物种播放。

### 徽章匹配规则

`badges.itemMatchers` 支持三种规则：

```toml
[badges]
itemMatchers = [
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

### 过场测试命令

以下客户端命令可在进入任意世界后直接从聊天栏执行。命令会打开独立测试界面并播放完整过场，包括模型、程序化特效、标题和音效：

```text
/cobblemoncinematics test battle_intro
/cobblemoncinematics test badge
/cobblemoncinematics test mega
/cobblemoncinematics test dynamax
/cobblemoncinematics test zmove
/cobblemoncinematics test terastalization
/cobblemoncinematics test all
```

四种特殊机制命令都支持可选的宝可梦物种 ID，例如 `/cobblemoncinematics test mega cobblemon:charizard`。训练师开场使用本地玩家作为测试训练师，徽章测试使用临时下界之星，特殊机制测试会渲染指定的仅客户端宝可梦（默认皮卡丘）。显式测试命令会绕过功能开关，也不要求安装 Mega Showdown，因为它们只测试本模组自身的演出层；真实战斗中的自动触发仍仅在安装 Mega Showdown 后可用，并遵守各自的配置项。`all` 会依次播放全部测试过场。

## 构建

```bash
./gradlew clean build
```

构建产物位于 `build/libs/cobblemoncinematics-<version>.jar`。

开发环境默认加载可选徽章模组和 Mega Showdown 用于联调。使用以下命令验证完全不安装可选联动模组时的启动：

```bash
./gradlew runClient -PwithoutBadgeMods -PwithoutMegaShowdown
```
