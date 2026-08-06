# 修复计划 A：音频中继与媒体投影

> 来源：[PR #45](https://github.com/NotifyRelay/Android/pull/45) CodeRabbit 评审
> 生成日期：2026-08-06

## 概述

- 评论数：12（7 Major / 5 Minor）
- 涉及文件（6 个，与其他计划无重叠，可并行执行）：
  - `app/src/main/java/com/xzyht/notifyrelay/feature/audio/AudioRelayPlayer.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/servers/AudioRelayForegroundService.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/servers/MediaProjectionForegroundService.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/xzyht/notifyrelay/ui/pages/MusicControlPage.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/ui/activity/MainActivity.kt`

## 执行前置

- 按 AGENTS.md 要求，修改前先迁出 `agent_{原分支}_{音频中继修复}` 分支。
- 验证：修改完成后运行 Android 模块构建（如 `./gradlew :app:assembleDebug`），确认编译通过。

---

## 修复项

### A1. [Major] AudioRelayPlayer.kt:160-161 — 帧构建避免逐字节装箱

- **问题**：播放路径用 `buf.take(read)` 构建帧，每个字节都会被装箱，性能差。
- **修复建议**：改用 byte-array 拷贝（如 `copyOf`）复制读取区间；`read` 非正数时保留 `silenceChunk` 逻辑；将结果传给 `NativeCore.audioWriteFrame`。

### A2. [Major] AudioRelayPlayer.kt:164-171 — AudioRecord 清理职责集中

- **问题**：`AudioRecord` 的 stop/release 分散在多处，存在并发释放风险。
- **修复建议**：将清理集中到 `startSendCapture` 协程的 `finally` 中，协程退出时统一 stop/release 并置空引用；`stop()` 仅取消 `captureJob` 并等待其结束（`cancelAndJoin`）后再继续，移除调用线程中重复的清理。

### A3. [Minor] AudioRelayPlayer.kt:43-54 — 未知 direction 增加 else 分支

- **问题**：`when (direction)` 只处理 `"recv"`/`"send"`，未知值会把 `isRunning` 置 `true` 并返回 `true`，调用方误判会话已建立。
- **修复建议**：增加 `else` 分支：记录警告日志、`isRunning = false`、返回 `false`。`92-104` 行同样处理。

### A4. [Major] AudioRelayForegroundService.kt:34-40 — stopIntent 限制应用自身接收

- **问题**：`stopIntent` 未设置目标包名，其他应用可能触发停止广播。
- **修复建议**：使用当前 context 的 `packageName` 或显式指定对应广播接收器组件，确保 `STOP_ACTION` 仅本应用可接收，保留现有 `EXTRA_DEVICE_NAME` 和 `PendingIntent` 配置。

### A5. [Major] AudioRelayForegroundService.kt:51 — 返回 START_NOT_STICKY

- **问题**：`onStartCommand` 返回 `START_STICKY`，服务被系统杀死后会在没有 DeviceConnectionManager 驱动音频会话时被重启。
- **修复建议**：改为返回 `START_NOT_STICKY`。

### A6. [Minor] AudioRelayForegroundService.kt:27-32 — 发送方向不使用 mediaPlayback 前台服务类型

- **问题**：该服务在 `send` 方向用于捕获/发送音频，本机并未播放媒体，Android 14 的 `mediaPlayback` 类型与用途不符。
- **修复建议**：发送/中继场景改用匹配用途的前台服务类型（如 `FOREGROUND_SERVICE_SPECIAL_USE`），保留现有接收行为与通知流程。

### A7. [Major] MediaProjectionForegroundService.kt:34-39 — onForegroundReady 竞态丢失

- **问题**：`MainActivity.processPendingScreenCapture` 中回调赋值的时机与服务就绪时序存在竞态，可能导致 `handleScreenCaptureReady` 丢失。
- **修复建议**：先在 `processPendingScreenCapture` 中设置 `onForegroundReady` 再调用 `startForegroundService`；服务增加"已就绪"状态，`onStartCommand` 就绪时立即触发已有回调，之后赋值的回调也立即执行。

### A8. [Minor] AndroidManifest.xml:33-36 — 声明并运行时申请 RECORD_AUDIO

- **问题**：缺少 `android.permission.RECORD_AUDIO`，Android 13+ 播放捕获输入会失败。
- **修复建议**：在权限声明区域新增 `RECORD_AUDIO`；在 `AudioRelayPlayer.startSendCapture()` 启动 `AudioRecord.Builder()` 前做运行时权限检查与申请，未授权时安全终止或返回。

### A9. [Minor] MusicControlPage.kt:207-220 — 音频方式选择器说明不完整

- **问题**：该设置同时控制本端发送（131 行）和对端接收（164 行），现有文案只描述接收。
- **修复建议**：标题改为"音频转发方式"，摘要说明同时控制发送与接收，并区分 scrcpy 与中继模式的前置条件。

### A10. [Major] MainActivity.kt:126-130 — 投影替换时清理旧音频捕获会话

- **问题**：替换投影时未停止旧的音频捕获（`AudioRelayPlayer.startSendCapture()` 创建的 `captureJob` 与 `audioRecord`），旧任务可能重新创建 AudioRecord。
- **修复建议**：先取消 `captureJob` 并释放 `audioRecord`，再调用旧投影 `stop()`、赋值新投影并启动新的音频转发；补充 `MediaProjection.Callback`，在投影被用户或系统停止时执行同样的清理。

### A11. [Minor] MainActivity.kt:183-186 — checkPermissionsAndStartServices 协程包装失效

- **问题**：`Dispatchers.Default` 包装未生效，函数内部再次 `lifecycleScope.launch(Dispatchers.Main)` 导致全部工作在主线程执行，注释"避免阻塞 UI 线程"与实际不符。
- **修复建议**：改为 `suspend` 函数，用 `withContext(Dispatchers.Main)` 包裹状态更新（`showAutoStartBanner`、`bannerMessage`）；`PermissionHelper.checkAllPermissions` 与 `ServiceManager.startAllServices` 保留在后台上下文；与同文件 `startServicesAndUpdateBanner` 写法一致。

### A12. [Major] MainActivity.kt:296-299 — onDestroy 清除 MediaProjection 回调

- **问题**：`onDestroy` 未清除 `DeviceConnectionManager` 单例的 `onRequestMediaProjection` 回调，单例继续持有 MainActivity 或 `screenCaptureLauncher` 的 lambda，导致泄漏。
- **修复建议**：清除该回调，与现有的 `onForegroundReady`、`pendingScreenCapture` 清理保持一致。

---

## 执行顺序建议

组内按 A1 → A2 → A3（AudioRelayPlayer 内部）→ A4 → A5 → A6（ForegroundService）→ A7（MediaProjection）→ A8（Manifest）→ A9（UI）→ A10 → A11 → A12（MainActivity）顺序修改，避免同一文件多次往返。
