# Agent Note: 录制器重建清空叠加层计时状态

Status: implemented

## Problem

叠加层里通知卡片展开后不收起、图标不消失，超级岛的展开态偶尔也会卡住。两端日志正常，进程也不卡死，`NotificationCardsState.tick` 的时序在单元测试里全部精确通过。

根因在状态的归属：`rememberNotificationCardsState` 把 `NotificationCardsState` 放在组合树的 `remember` 里，巡检循环是组合内的 `LaunchedEffect`。而 `OverlaySceneBridge.publishFrame` 在内容尺寸变化时会整体重建 `OverlaySceneRecorder`——通知到达、卡片收起、媒体卡展开都会改变内容高度，重建因此很频繁。

重建带来两重破坏：`remember` 里的状态随旧组合树一起丢弃，`expandedAt` 归零；组合作用域被取消，巡检 `LaunchedEffect` 停摆，新状态再没有人推进。实测连续重建 6 次后条目仍停在展开态，`expandedAt` 为空——展开起始时刻每次都被刷新，永远攒不满 3 秒阈值，图标也就永远到不了 12 秒存活上限。

超级岛受影响较轻：它的展开态同样用 `remember` 持有、同样被重建重置，但条目移除由进程级的 `pruneSuperIslands` 循环驱动，不依赖组合内的计时，所以表现为「该收起时不收起」而非「该消失时不消失」。

## Decision

**计时状态移出组合树。** `NotificationCardsHolder` 作为进程级对象持有唯一的状态实例，组合树每次只读它。状态的创建与销毁因此不再与渲染生命周期绑定。

**巡检在独立作用域推进。** `NotificationCardsHolder` 自持 `CoroutineScope(SupervisorJob() + Dispatchers.Default)`，`ensureStarted()` 以 `@Synchronized` 保证只启动一次。不复用组合作用域：那正是随录制器重建被取消的对象。

## Alternatives considered

**让 `OverlaySceneBridge` 不再按尺寸重建录制器。** `ImageComposeScene` 的尺寸在构造时固定，改尺寸只能重建；要让尺寸稳定得先让内容高度不再变化，而高度变化本身就是通知到达与收起的必然结果。绕开症状而非解决归属问题。

**把计时状态放进 `DesktopOverlayStore`（已是进程级）。** 可行，但会把「UI 展示时序」混进「显示数据」的职责里；`NotificationCardsHolder` 单独承载计时，边界更清楚。

**用 `rememberSaveable` 或 key 化 `remember` 让状态跨重建存活。** 这两者只在同一组合树内的重排里保状态，重建是整棵树丢弃，两者都不跨作用域生命周期。

**靠固定的 `Dispatchers.Main` 单协程在进程级重扫状态。** 与所选方案等价但更绕：仍需一个不随组合销毁的持有者与作用域，直接引入 `NotificationCardsHolder` 语义更直白。

## Consequences

通知卡片的收起与移除恢复按墙钟推进，与录制器重建次数无关。超级岛的展开态同样受益。

代价是通知计时状态不再随窗口关闭而释放：窗口销毁后 `NotificationCardsHolder` 仍持有已移除条目的时刻表。`tick` 末尾的 `retainAll` 会随条目移除逐步清理，残留规模受当前通知数约束，不随运行时长增长。

巡检循环在进程生命周期内常驻，即使叠加层窗口重建也只有一个实例。这是有意的：它本就是全局单例状态，多实例会重复推进同一份时刻表。

## Testing

`NotificationRecorderRebuildTest` 复现了现场：每 1 秒重建录制器、连续 6 秒（收起阈值两倍），断言卡片已退出展开态。修复前该用例稳定失败（`expanded=[5]`、`expandedAt` 为空），修复后通过。`NotificationRedeliveryTest` 覆盖重复投递仍会消失。`:desktop:jvmTest` 与 `:desktop:ktlintCheck` 通过。
## 统一计时（同批交付）

修复初期只把通知的状态搬出组合树，超级岛仍是 `remember` + `delay`，移除走 `pruneSuperIslands`，通知则自建循环——同一件事存在三套机制。现在收敛为一条：

**`OverlayClock` 是叠加层唯一的时钟。** 进程级 `object`，自持 `CoroutineScope(SupervisorJob() + Dispatchers.Default)`，按 100ms 粒度遍历注册的观察者。它不随任何组合树销毁，因此录制器重建不影响计时；也只有一条循环在采样墙钟，不会出现同一份时间被多处以不同周期读取。

**通知卡片** 以 `OverlayClock.register` 注册 `tick`，不再自建循环。条目到达时由 `addNotification` 调 `NotificationCardsHolder.onArrived` 立即登记展开起点——巡检有粒度，等到下一轮才登记会让起点晚于真实到达时刻，收起整体推迟。

**超级岛** 的展开态由 `SuperIslandExpansionState` 持有并按 `sourceKey` 登记在 `SuperIslandExpansionHolder`，同样由 `OverlayClock` 推进；内容标识变化时才重新展开并重置计时。它此前在 `onDispose` 里释放状态，而录制器重建同样会触发 `onDispose`，计时随之归零——释放改由 `DesktopOverlayStore.removeSuperIsland` 在条目真被移除时执行。

**移除与收起同源。** 超级岛条目的移除仍由 `pruneSuperIslands` 决定，但判据与时限统一取自 `ReplicaExpiry` / `ReplicaTiming`；通知的移除由同一个 `tick` 完成。两条业务不再各自维护计时语义。

三条替代方案：**让两类业务各留一个循环**，会重复采样墙钟且每加一类动态内容就要再来一个；**把超级岛也改成组合内 `LaunchedEffect`**，正是本次要修的病根；**给 `SuperIslandExpansionState` 在 `onDispose` 里加条件判断以区分「重建」与「真移除」**，组合侧无法可靠区分销毁原因，判定放在 Store 的移除入口才唯一。

`OverlayClock` 的唯一实例与注册表在进程生命周期内常驻；观察者按业务对象固定注册，不随窗口重建增长。单条超级岛的状态在条目移除时释放，不按来源累积。

`OverlayTimerRebuildTest` 覆盖两类：每 900ms 重建录制器、持续 6 秒（收起阈值两倍），断言通知卡片已退出展开态、超级岛已收起。修复前两者均失败。
