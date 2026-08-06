# 修复计划 C：超级岛状态同步

> 来源：[PR #45](https://github.com/NotifyRelay/Android/pull/45) CodeRabbit 评审
> 生成日期：2026-08-06

## 概述

- 评论数：5（2 Major / 2 Minor / 1 Nitpick）
- 涉及文件（4 个，与其他计划无重叠，可并行执行）：
  - `app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/RemoteMediaSessionManager.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/SuperIslandRemoteStore.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/sync/notification/SuperIslandProcessor.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/servers/NotifyRelayNotificationListenerService.kt`

## 执行前置

- 按 AGENTS.md 要求，修改前先迁出 `agent_{原分支}_{超级岛同步修复}` 分支。
- 验证：修改完成后运行 Android 模块构建（如 `./gradlew :app:assembleDebug`），确认编译通过。

---

## 修复项

### C1. [Major] RemoteMediaSessionManager.kt:230-231 — buildMediaState 使用最终封面 URL

- **问题**：`buildMediaState` 传入的是原始 `coverUrl` 而非已计算的 `finalCoverUrl`；封面字段按非空白判断时，缺失或空白封面会覆盖 `currentSession` 中已有封面。
- **修复建议**：改为传入 `finalCoverUrl`；封面写入逻辑按非空白字符串判断。

### C2. [Nitpick] RemoteMediaSessionManager.kt:220-226 — 删除未使用的 lastFeatureId

- **问题**：差异判断逻辑迁移到 Rust 后，`lastFeatureId` 不再被任何分支使用。
- **修复建议**：删除该局部变量及其读取，保留 `currentFeatureId` 计算与 `mediaFeatureIdCache`、`mediaLastUpdateTime` 更新。

### C3. [Minor] SuperIslandRemoteStore.kt:24-27 — 保留增量协议兼容路径

- **问题**：`applyIncoming` 对所有 payload 都用 `parseStateFromFull` 并覆盖 `store[sourceId]`；旧设备仍发送只含 `changes` 的 delta 报文时，缺失的 `title/text/pics` 会被解析为空，清空本地状态。
- **修复建议**：`parseStateFromFull` 与 `store[sourceId]` 覆盖仅用于 Rust 合并后的全量 payload；对 legacy changes-only delta 报文保留版本协商或显式兼容路径，delta 与现有状态合并而非清空；保留 raw/param 字段的降级回退行为。

### C4. [Minor] SuperIslandProcessor.kt:180 — 缺失 featureId 不注册共享去重键

- **问题**：`NativeCore.computeFeatureId` 返回 null 或抛异常时 `featureId` 为空，`dedupKey` 变为 `${remoteUuid}|${mappedPkg}|`，第一条锁屏消息会在 5 分钟内丢弃同设备同应用的所有后续消息。
- **修复建议**：仅当 `featureId` 非空时才执行锁屏去重（`dedupCheck`）；缺失时记录诊断日志并继续处理消息（参考评论中的 diff）。

### C5. [Major] NotifyRelayNotificationListenerService.kt:424-430 — computeFeatureId 的 instanceId 与 Rust 会话一致

- **问题**：传入的是 `sbnInstanceId`，与 Rust 会话使用的 instanceId 规则不一致，导致生成的 `featureIdOverride` 与 `MessageSender.sendSuperIslandData`、`sendSuperIslandEnd` 及 `DeviceConnectionManager.handleSuperIslandStateQuery` 不一致。
- **修复建议**：改为与 Rust 会话一致的空值格式，不再传 `sbnInstanceId`。
- **关联**：依赖计划 D（MessageSender）与计划 B（DeviceConnectionManager）的 instanceId 用法保持一致，但文件不重叠，可并行修改。

---

## 执行顺序建议

按 C1 → C2（RemoteMediaSessionManager 集中）→ C3（SuperIslandRemoteStore）→ C4（SuperIslandProcessor）→ C5（NotificationListenerService）顺序修改。
