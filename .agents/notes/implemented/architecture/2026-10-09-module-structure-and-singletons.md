# Agent Note: 模块结构收敛为直连仓储与三种单例

Status: implemented

## Problem

应用列表与设备通知两块曾各有一层中间抽象：`AppRepository` 与 `AppDatabaseHolder` 包住数据库，`NotificationMemoryStore` 夹在仓储与持久化之间。这些抽象在重构后只剩转发，却让读者要穿过两层才能看到真正持有状态的类型。同一时期单例写法在各处不同，没有记录说明哪些是有意保留的差异。

## Decision

应用列表的四个仓储直连，设备通知由 `NotificationRepository` 直接持有内存态，单例保留三种形态。当前结构见[模块结构](../../../../Docs/模块结构.md)。

## Alternatives considered

**给 `feature/appslist/` 补门面。** 四个仓储服务不同的数据来源与缓存策略，调用方需要按场景选择。门面只会把选择重新藏起来，并增加一层转发。

**保留 `NotificationMemoryStore` 中间层。** 它原本用于隔离内存态与持久化，但 `NotificationRepository` 已经成为内存态的唯一持有者，中间层只剩转发。删除后持久化仍经 `NotificationRecordStore` 单例，边界没有变弱。

**统一单例写法。** 三种形态对应三种生命周期：存储入口跟进程、组合根跟应用启动、`ScrcpyUiViewModel` 跟子模块。强行统一会让其中至少一个的归属失真。

## Consequences

新增或改动这四个仓储、两个存储单例时必须确认调用方是否依赖直连形态。新增 `:shared` 端口会同时牵动 Android 与桌面端两处 provides，漏一处只在运行时暴露。
