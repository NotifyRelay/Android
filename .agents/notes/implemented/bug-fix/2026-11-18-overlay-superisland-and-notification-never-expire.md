# Agent Note: 叠加层超级岛与通知卡片不自动消失

Status: implemented

## Problem

Gamebar 侧叠加层里，通知卡片展开后不自动收起、图标不消失，超级岛也不会超时移除；两端日志都正常，进程不卡死。

现场日志给出了直接证据：`SUPERISLAND` 报文 498 条中 918/919 条长度完全相同（87180 字节），按 5–8 秒间隔持续重发；同期 `MEDIAPLAY` 同样恒定重发。`DesktopSuperIslandInbound.handle` 每次收到报文都调用 `putSuperIsland` 构造新的 `SuperIslandEntry`，而该数据类的 `updatedAt` 默认取 `System.currentTimeMillis()`。于是同一份内容每次到达都刷新一次更新时间，`pruneSuperIslands` 判定的「无更新」永远不成立。

`addNotification` 是同一类问题的另一种表现：它无条件 `add`，对端重发同一 id 的通知会追加出多个条目，各自独立计时，收起后的表现随之错乱。

排查中先排除了 Compose 侧：在 `OverlaySceneRecorder` 的真实组合形态下，`LaunchedEffect` 计时与状态写入都能正常推进（帧推进 57 次、计时 5 轮），`NotificationCardsState.tick` 的收起与移除时序也按 `NOTIFICATION_COLLAPSE_MS` / `NOTIFICATION_ICON_LIFETIME_MS` 精确通过。届时可确认计时链路无误，问题在输入侧。

## Decision

**「无更新」按内容判定。** `SuperIslandEntry` 新增 `hasSameContent`，比较标题、正文、`paramV2` 与图片，刻意不比较 `updatedAt`——后者表达的是内容何时变化，不是内容本身。`putSuperIsland` 在内容未变时用 `copy(updatedAt = previous.updatedAt)` 沿用原时刻，超时回收因此仍以首次到达为准。

**通知按 id 替换。** `addNotification` 先查找同 id，命中即原地替换，否则追加。

## Alternatives considered

**让对端不重发同一份报文。** 重发是远端心跳式语义的一部分，用于在链路抖动后自愈；在接收端去掉重复比在发送端约束频率更可靠，也不会因漏推导致内容永久缺失。

**给 `pruneSuperIslands` 改用「首次到达时刻」单独字段。** 会在 `SuperIslandEntry` 里引入第二个时间轴，两者需要保持同步；而内容相等性本来就是「是否算一次更新」的定义，直接用内容比较更贴近语义。

**收到报文时先比较 JSON 原始串再决定是否写入。** 原始串包含远端时钟等无关字段，同样一次内容的重发会因字段微差被判为变化；比较解析后的展示字段才是稳定的口径。

**周期性地无条件清空过期条目。** 会误删仍在持续更新的条目，例如正在播放的媒体超级岛。

## Consequences

同一份内容被反复重发时，条目不再刷新存活时间，12 秒后正常移除。内容真正变化（新通知、播放进度文案变化、图片更换）仍会重置计时并重新展开。

`SuperIslandEntry` 的 `updatedAt` 从「最近一次报文到达」变为「最近一次内容变化」。任何依赖前者语义的读取方需要改用内容比较；当前仅 `pruneSuperIslands` 与 `LocalSuperIslandReceivedAtMs` 读取它，后者用于把远端时钟换算到本地，内容未变的重发本就不该改变这个基准。

`addNotification` 改为替换后，同一 id 的重发不会重置该条目的展开计时——这正是期望行为，否则持续重发会让卡片永不收起。

## Testing

`OverlayAutoDismissTest` 覆盖六条：到达阈值后收起、存活期满后移除、重复同内容不刷新 `updatedAt`、内容变化才刷新、重复通知不重复入队，以及过期条目被真正回收。`OverlayTimingTest` 固定了录制场景内 `LaunchedEffect` 计时与状态写入可用的前提。`:desktop:jvmTest`、`:desktop:ktlintCheck` 与 `pnpm run test:docs` 全部通过。
