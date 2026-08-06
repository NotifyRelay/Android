# 修复计划 B：设备连接与状态管理

> 来源：[PR #45](https://github.com/NotifyRelay/Android/pull/45) CodeRabbit 评审
> 生成日期：2026-08-06

## 概述

- 评论数：7（3 Major / 1 Minor / 3 Nitpick）
- 涉及文件（4 个，与其他计划无重叠，可并行执行）：
  - `app/src/main/java/com/xzyht/notifyrelay/feature/device/service/DeviceConnectionManager.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/sync/HeartbeatProcessor.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/sync/ConnectionDiscoveryManager.kt`
  - `app/src/main/java/com/xzyht/notifyrelay/ui/screen/DeviceListScreen.kt`

## 执行前置

- 按 AGENTS.md 要求，修改前先迁出 `agent_{原分支}_{设备连接状态管理修复}` 分支。
- 验证：修改完成后运行 Android 模块构建（如 `./gradlew :app:assembleDebug`），确认编译通过。

---

## 修复项

### B1. [Major] DeviceConnectionManager.kt:1825-1829 — 广播接收器使用 RECEIVER_NOT_EXPORTED

- **问题**：`audioRelayNotificationReceiver` 注册未限定导出，可能被外部应用发送广播触发。
- **修复建议**：注册为 `Context.RECEIVER_NOT_EXPORTED`，并用 try/catch 包裹 `registerReceiver` 调用（与 `registerBatteryChangeReceiver` 一致）；同文件 616-622 行 `Intent.ACTION_BATTERY_CHANGED` 的注册标志同样改为 `Context.RECEIVER_NOT_EXPORTED`。

### B2. [Major] DeviceConnectionManager.kt:1254-1266 — audioStop 确认不能重入停止路径

- **问题**：`audioStop` 处理中，确认（ack）可能再次进入停止命令路径，导致重复响应。
- **修复建议**：在响应中使用独立的 `audioStopAck` action，或检测到含 result 的入站消息时不再发送另一条响应；保留本地播放停止与通知取消，确保确认终止交互。

### B3. [Major] DeviceConnectionManager.kt:704-713 — 认证设备元数据同步优化

- **问题**：未知 `deviceType`（"unknown"）会覆盖已保存的具体类型；IP/类型未实际变化时仍持续写库。
- **修复建议**：快照中 "unknown" 视为无效，保留 `auth.deviceType`；仅在有效 IP 或设备类型实际变化时更新 `authenticatedDevices` 并调用 `saveAuthedDevices()`；为混合 `&&`/`||` 的条件补充明确括号。

### B4. [Nitpick] DeviceConnectionManager.kt:1458-1477 — 媒体状态查询缓存封面编码

- **问题**：`handleMediaStateQuery` 在 Rust 心跳线程上同步执行 JPEG 压缩和 Base64 编码，每次查询（即使媒体未变化）都重复开销，阻塞心跳。
- **修复建议**：按位图标识（或等价稳定变更键）缓存编码结果，位图未变时复用 Base64 数据，仅在变化时重新压缩；仍保留 `fullJson` 构建用于 `compareAndPushState`。
- **关联**：与计划 D 的 D2（MessageSender 图像处理 suspend 化）逻辑相关，但两者文件不重叠，可并行。

### B5. [Nitpick] HeartbeatProcessor.kt:57-90 — 合并认证/未认证分支重复逻辑

- **问题**：两个分支的缓存回填、未知电量保留、`updateGlobalDeviceName`、`updateDeviceListInternal` 完全重复，易漂移。
- **修复建议**：`needSave` 判断保留在认证分支内；将 `deviceInfoCacheInternal` 更新、`updateGlobalDeviceName`、`updateDeviceListInternal` 移到分支外只执行一次（参考评论中的重构 diff）。

### B6. [Nitpick] ConnectionDiscoveryManager.kt:197-205 — 网络回调防抖

- **问题**：`onAvailable`/`onCapabilitiesChanged` 高频触发时反复启动协程重置 Rust 重连退避，产生重连风暴。
- **修复建议**：维护可取消的 `reconnectRefreshJob`，新回调到来时先取消旧任务再启动新任务，保留 1000ms 延迟后调用 `refreshAllReconnectTargetsInternal()`。

### B7. [Minor] DeviceListScreen.kt:300-305 — 未知电量 -1 不显示为 0%

- **问题**：上游用 `-1` 表示未知电量，`abs(...) <= 100` 恒真导致 `-1` 被 `coerceIn(0, 100)` 转成 0%，界面显示 0% 与低电量图标。
- **修复建议**：改为按有效范围过滤：`device.batteryLevel in 0..100` 时才更新 `batteryLevel.intValue`，直接赋值无需 coerce。

---

## 执行顺序建议

按 B1 → B2 → B3 → B4（DeviceConnectionManager 集中）→ B5（HeartbeatProcessor）→ B6（ConnectionDiscoveryManager）→ B7（DeviceListScreen）顺序修改。
