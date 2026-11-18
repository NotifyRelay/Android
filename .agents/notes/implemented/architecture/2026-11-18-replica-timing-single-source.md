# Agent Note: 超级岛时限与过期判定收归 `:domain` 单一实现

Status: implemented

## Problem

超级岛展示时限在两端各有一份：Android 由 `ReplicaTiming.MEDIA_VIEW_TTL_MS` 与 `FloatingReplicaNotificationManager` 内的 `NON_MEDIA_VIEW_TTL_MS` 定义，桌面端由 `DesktopSuperIslandCard` 内的 `SUPER_ISLAND_MEDIA_TIMEOUT_MS` / `SUPER_ISLAND_TIMEOUT_MS` 另行定义。数值当时一致，但没有任何机制保证它们此后再一致；`ReplicaTiming` 自称「唯一来源」，对桌面端却不成立。`FloatingWindowManager` 里还存在第三份 `nonMediaViewTtlMs = 12_000L`，与 `FloatingReplicaNotificationManager` 的私有常量重复。

两端判定机制也不同：Android 是「内容到达即取消旧任务、重新 `delay`」，到点删除；桌面端是固定周期轮询加时间戳比较。后者存在相位误差——条目到达时刻与轮询相位无关，`delay(12s)` 后判定 `now - updatedAt` 可能刚不足阈值，实际存活最长翻倍到 24 秒（媒体条目 90 秒）。

## Decision

**时限集中在 `ReplicaTiming`。** 新增 `NON_MEDIA_VIEW_TTL_MS` 与 `EXPANDED_DURATION_MS`，并提供 `viewTtlMs(isMedia)` 供调用方按业务取用时不必自行分支。两端所有该契约内的时长都指向它：Android 的 `FloatingWindowManager`（展开时长与自动移除时限）、`FloatingReplicaNotificationManager`（撤回时限），桌面端的超级岛卡片、电池岛与媒体卡默认收起时长。

**过期判据集中在 `ReplicaExpiry`。** `isExpired(updatedAtMs, isMedia, nowMs)` 只比较「最近一次内容变化」与当前时刻，`remainingMs` 给出剩余存活供单循环驱动方精确休眠。桌面端的 `pruneSuperIslands` 与 `nextSuperIslandExpiryInMs` 都调用它，不再自己算阈值。

**桌面端回收改为按到期精确休眠。** 每轮先清理，再按 `nextSuperIslandExpiryInMs` 决定休眠时长，上下界钳在 `[50ms, NON_MEDIA_VIEW_TTL_MS]`：下限避免剩余存活极短时忙等，上限保证空转期间新到达的条目也会被及时判定。

## Alternatives considered

**照搬 Android 的「每条目一个协程」。** 桌面端是单循环驱动整个叠加层渲染，一条目一起协程会让取消与回收分散在多处，还需另外维护任务表；按最近到期时刻休眠能以单循环达到同样的无相位误差，代码量更小。

**让两端各自保留数值、只加一条校验测试断言相等。** 断言能让不一致被发现，但仍然是两份定义要同步修改；把常量放在共享模块里从结构上消除这种同步需求，成本更低。

**把 `ReplicaExpiry` 做成类以携带策略。** 当前只有两个纯函数，无状态可言；Kotlin `object` 足以表达，引入实例只会让调用方多一层持有。

## Consequences

时限与判据只剩一处定义，改数值或改判定口径会同时作用于两端。桌面端此前最长 24 秒（媒体 90 秒）的实际存活收敛到契约值。

`ReplicaExpiry` 只表达**判定**，不表达**动作**：删除条目、取消通知、关闭浮窗仍由各端自行实现。这是有意的边界——两端的载体不同（Android 是通知与浮窗，桌面端是叠加层状态集合），强行统一动作会把平台差异塞进共享层。

桌面端回收循环的休眠时长依赖条目集合的当前状态，空集合时退化为 `NON_MEDIA_VIEW_TTL_MS`。新条目在上一轮休眠期间到达时，最迟要等这次休眠结束才被纳入判定；上限钳制保证该延迟不超过非媒体时限。

## Testing

`ReplicaExpiryTest`（`:domain:commonTest`）覆盖阈值边界、媒体时限必须长于刷新周期、`viewTtlMs` 分支与 `remainingMs` 的零钳制。`OverlayAutoDismissTest` 覆盖桌面端收起与移除时序、重复同内容不刷新存活、内容变化才刷新。`:domain:jvmTest`、`:desktop:jvmTest`、`:desktop:ktlintCheck` 与 `:app:compileDebugKotlin` 全部通过。
