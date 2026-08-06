# 修复计划 D：同步发送与数据通道

> 来源：[PR #45](https://github.com/NotifyRelay/Android/pull/45) CodeRabbit 评审
> 生成日期：2026-08-06

## 概述

- 评论数：12（6 Major / 2 Minor / 4 Nitpick）
- 涉及文件（5 个，与其他计划无重叠，可并行执行）：
  - `app/src/main/java/com/xzyht/notifyrelay/sync/MessageSender.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/sync/IconSyncManager.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/servers/clipboard/ClipboardSyncManager.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/ui/dialog/PairingCodeDialog.kt`
  - `core/src/main/java/notifyrelay/core/util/ServiceManager.kt`

## 执行前置

- 按 AGENTS.md 要求，修改前先迁出 `agent_{原分支}_{同步数据通道修复}` 分支。
- 验证：修改完成后运行 Android 模块构建（如 `./gradlew :app:assembleDebug`），确认编译通过。

---

## 修复项

### D1. [Major] MessageSender.kt:34-50 — lastPushedStateCache 改为 LRU 并保证并发安全

- **问题**：当前缓存实现无容量上限且并发访问不安全；`sendSuperIslandEnd` 时按结束时的 title/text 重新计算 key 移除，可能移除错误的记录。
- **修复建议**：改为最大容量的 `LinkedHashMap` LRU；保证 `cacheLastPushedState`、`getLastPushedState`、`removeLastPushedState` 并发安全；按会话记录实际推送时使用的 `featureId`，`sendSuperIslandEnd` 结束时用该记录调用 `removeLastPushedState`，不重新计算 key。

### D2. [Major] MessageSender.kt:267-304 — buildSuperIslandFullContent 去除 runBlocking

- **问题**：`runBlocking` 同步执行本地图片读取与 Base64 编码，阻塞 Rust 心跳线程。
- **修复建议**：改为 `suspend` 函数，异步执行图像处理；更新调用方（尤其 `DeviceConnectionManager.handleSuperIslandStateQuery`）以 suspend 流程调用，保留现有 `processedPics` 行为。
- **关联**：与计划 B 的 B4 逻辑相关，但文件不重叠，可并行。

### D3. [Nitpick] MessageSender.kt:143-150 — cacheLastPushedState 移出设备循环

- **问题**：`content` 与 `MEDIA_FEATURE_ID` 在循环中不变，当前对每台设备重复写入同一条缓存。
- **修复建议**：将 `cacheLastPushedState(MEDIA_FEATURE_ID, content)` 移出 `getAuthenticatedDevices(deviceManager).forEach` 循环，设备处理完成后调用一次；`pushMediaState` 与逐设备错误日志保留在循环内。

### D4. [Major] MessageSender.kt:338-362 — 复用 authenticatedDevices 快照

- **问题**：行 338 已取得 `authenticatedDevices`，行 357 再次调用 `getAuthenticatedDevices(deviceManager)`，反射开销高且两次可能返回不同快照，导致空判定与实际推送目标不一致。
- **修复建议**：推送循环直接使用已取得的 `authenticatedDevices` 变量，保留现有空集合提前返回逻辑。

### D5. [Major] IconSyncManager.kt:57-66 — 单图标请求传入缓存的包状态

- **问题**：单图标请求以 `listOf()` 无条件传给 `appSyncPrepareIconRequest`，Rust 无法按缓存状态过滤。
- **修复建议**：用与批量路径 `cachedPackages` 相同的缓存来源和表示，推导 `packageName` 是否已缓存，并将结果传给 `appSyncPrepareIconRequest`。

### D6. [Major] IconSyncManager.kt:157-163 — 检查 EnqueueResult 并抛异常

- **问题**：`requestIconsFromDevice` 中的 `ProtocolSender.sendEncrypted` 未检查入队结果，失败时仍保存设备关联并调用 `appSyncClearIconPending`。
- **修复建议**：结果为 `QUEUE_UNINITIALIZED`、`MISSING_CONTEXT`、`AUTH_FAILED`、`NATIVE_ERROR` 时立即抛异常，使 `checkAndSyncIcon`/`requestIconsBatch` 进入现有 catch 分支；成功入队时保留当前流程。

### D7. [Nitpick] IconSyncManager.kt:218-238 — 提取 10000L 超时常量

- **问题**：行 163、220、238 三处使用字面量 `10000L`。
- **修复建议**：定义私有常量（如 `ICON_REQUEST_TIMEOUT_MS`），替换三处 `ProtocolSender.sendEncrypted` 调用点（含批量与单图标响应路径），保留超时值。

### D8. [Nitpick] ClipboardSyncManager.kt:70-75 — 缓存 ClipboardManager 使用 applicationContext

- **问题**：`getClipboardManager` 缓存单例服务实例，若首次调用传入 Activity/Service context，会间接持有组件导致无法回收。
- **修复建议**：通过 `context.applicationContext.getSystemService(...)` 获取并缓存。

### D9. [Nitpick] ClipboardSyncManager.kt:77-79 — 移除无实际行为的 suppressClipboardMonitoring

- **问题**：该方法仅写日志，防循环逻辑已迁移到 Rust，行 140 仍调用并带误导性注释。
- **修复建议**：删除该方法及其调用点，同步清理误导性注释。

### D10. [Minor] ClipboardSyncManager.kt:389-395 — file_transfer 分支向用户提示同步失败

- **问题**：Rust 返回 `file_transfer` 时平台端只记日志，大内容剪贴板被静默丢弃，用户无感知。
- **修复建议**：复用现有用户可见的通知/错误上报机制提示剪贴板同步失败；保留现有 warning 日志与其他 action 行为。

### D11. [Major] PairingCodeDialog.kt:58-60 — 移除强制解包

- **问题**：`displayCode` 初始化对 `rustContextInternal` 和 `NativeCore.generatePairingCode` 强制解包，context 或配对码为 null 时崩溃。
- **修复建议**：安全处理 null；获取不到配对码时提示用户并关闭对话框，成功生成时保留现有展示流程。

### D12. [Minor] ServiceManager.kt:90-96 — 同步 startAllServices 的 KDoc 与残留方法

- **问题**：KDoc 仍写"包括通知监听服务和剪贴板监控服务"，实现只启动通知监听服务；`startClipboardMonitorService` 已无人调用。
- **修复建议**：更新 KDoc 仅描述实际行为并同步返回值说明；删除未被调用的 `startClipboardMonitorService` 及其无用依赖。

---

## 执行顺序建议

按 D1 → D2 → D3 → D4（MessageSender 集中）→ D5 → D6 → D7（IconSyncManager 集中）→ D8 → D9 → D10（ClipboardSyncManager 集中）→ D11（PairingCodeDialog）→ D12（ServiceManager）顺序修改。
