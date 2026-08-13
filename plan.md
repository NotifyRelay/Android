# PR #52 CodeRabbit 评审 → 修复计划

> 本文件替换了原 PR #48 修复计划（已合并完成），现归档 PR #52（统一 Rust Core 迁移）的完整评审修复计划。

**基线**：HEAD `b20147e` | **评审来源**：CodeRabbit review [4919249053](https://github.com/NotifyRelay/Android/pull/52#pullrequestreview-4919249053) | **共 35 条评论**：33 条有效（全部修复）+ 2 条部分有效（并入死代码清理）+ 1 条结论不成立（用户确认加固）

## 验证结论汇总

| # | 评论 | 验证结果 | 处置 |
|---|------|---------|------|
| I1 | buildscript 缺 repositories | 结论不成立（Gradle 隐式 gradlePluginPortal，KGP 2.4.0 在 portal 确认存在），防御性加固 | ✅ 加 mavenCentral() |
| M15 | NativeCore 音频事件回调 getString 无空指针检查 + Log.d | **有效**（对照 NotifyRelayCore.ptrToString 双重检查） | 修 |
| M17 | `_callbackInstance` 缺 @Volatile | **有效**（JNA 回调线程读取） | 修 |
| M18 | `_rustContext` 缺 @Volatile（相邻 senderQueuePtr 已标） | **有效** | 修 |
| M19 | on_data 日志泄漏完整 UUID、绕过 Logger 级别控制 | **有效** | 修 |
| M24 | audioRelayPlayer 等字段初始化晚于 init 块，startCore 后回调窗口 NPE | **有效** | 修 |
| M25 | setupAudioCallbacks check-then-act 非线程安全 | **有效** | 修 |
| M27 | stateQueryKeys HashMap 非并发安全、无界增长、无清理 | **有效** | 修 |
| M28 | 音频回调缺 Native.detach(false)（其余回调均有） | **有效** | 修 |
| M5 | notify-relay-core 子模块 SSH URL，CI 无对应密钥 → **CI 拉取会失败** | **有效**；`NotifyRelay/core` 为**公开仓库** | 改 HTTPS |
| M1 | RemoteMediaSessionManager cleanupRunnable 从未初始化 | **有效且更严重**（clearSession 路径直接崩溃） | 修 |
| M6 | 两个 specialUse 服务缺 PROPERTY_SPECIAL_USE_FGS_SUBTYPE | **有效**（manifest 无任何 meta-data） | 修 |
| M12 | onDestroy 无条件清空全局回调，重建时清掉新实例注册 | **有效** | 修 |
| M13 | startForeground 无异常保护 | **有效**；修正：AudioRelay 类型与 manifest 已一致，仅需 try/catch | 修 |
| M14 | onForegroundReady 回调可能执行两次 | **有效** | 修 |
| M16 | AudioRelayPlayer runBlocking 阻塞主线程（18s 级） | **有效**（BroadcastReceiver 调用链） | 修 |
| M20 | 重复 startSendCapture 泄漏 AudioRecord | **有效**（旧 finally 释放新实例） | 修 |
| O4 | 已在线设备被反复重握手（最长约 18s 阻塞） | **有效**（online 字段存在） | 修 |
| M22 | startDiscovery 无锁屏判定，独立调用破坏 UDP 广播约定 | **有效**（public 方法） | 修 |
| M21 | performDeviceConnectionWithRetry 无 IO 调度器 | **有效**（当前唯一调用方在 IO 上） | 修 |
| M23 | processHeartbeat 无条件全量刷新（3N 次/2s） | **有效** | 修 |
| M26 | 未认证心跳直写缓存、名称未清洗 | **有效且更严重**（globalDeviceNameCache 无清理路径） | 修 |
| M2 | 超级岛状态包多协程乱序（结束包可能先到） | **有效** | 修 |
| O2 | dedupKey 重复拼接无法清除原始去重记录 | **有效**（与 substringAfterLast 分支不一致） | 修 |
| M3 | 稳定 featureId 共享 dedupKey 丢弃状态更新（OS≤3.0.200） | **有效**（高版本有 shouldSkipDedup 削弱） | 修 |
| M29 | 裸路径图片被原样发送远端、异常无日志 | **有效** | 修 |
| M7 | 迁移把"集合不存在"当作禁用，与"默认启用"注释矛盾 | **有效**（升级后全部条目禁用） | 修 |
| M9 | customEnabled 空串 split 得 [false]，首个自定义组静默禁用 | **有效** | 修 |
| M8 | 过滤条目读改写整表覆盖竞态 | **有效**（无单条 DAO；saveLock 先例在） | 修 |
| M10 | shouldForwardBlocking 主线程 runBlocking 全表查库 | **有效**（每通知 1 次） | 修 |
| M11 | 负电量直传图标/颜色 | **部分有效**（上游已 abs，仅 -1 未知特例） | 修 |
| O1 | 验证码复制双路径可能双发 | **部分有效**（服务未启动不可复现，删死代码后消除） | 并入 H1 |
| O5 | ClipboardMonitorService 类/manifest/sendClipboardToDevices 残留 | **部分有效**（ServiceManager 已移除调用） | 删死代码 |
| M4 | gradle-wrapper 缺 distributionSha256Sum | **有效**（SHA 值已与官方校验一致） | 修 |
| O3 | GitHub Actions 固定完整 commit SHA | 用户确认：**不修不记录**（浮动标签亦为人工提交的更新，历史哈希不会移除） | 跳过 |

---

## 修复任务清单（按文件分组）

### A. 原生桥接 — `nativecore/src/main/java/com/xzyht/notifyrelay/nativecore/NativeCore.kt`（M15 M18 M25 M28）

**A1. 音频事件回调（约 L293-299）**
- `event?.getString(0, "UTF-8")` / `errorMsg?.getString(0, "UTF-8")` → `NotifyRelayCore.ptrToString()`（检查 `Pointer.nativeValue == 0L`）
- `Log.d(TAG, "音频事件: ...")` → `AppLogger.d`（受 Logger.CURRENT_LEVEL 控制）

**A2. `_rustContext`（L14）**：加 `@Volatile`（写入：createContext/setContext；读取：getContext 各回调）

**A3. `setupAudioCallbacks`（L284-285）**：`= synchronized(this)`，使 `audioDataCallbackRef != null` 守卫与注册原子化

**A4. 音频回调入口（L287-291、L294-298）**：`OnAudioDataCb.invoke` 与 `OnAudioEventCb.invoke` 方法入口（提前 return 之前）调用 `Native.detach(false)`，补 `com.sun.jna.Native` import

### B. 设备连接 — `app/.../feature/device/service/DeviceConnectionManager.kt`（M17 M19 M24 M27）

**B1. `_callbackInstance`（L116）**：companion object 中加 `@Volatile`

**B2. on_data 日志（L1216）**：`android.util.Log.d("CoreCb", ...)` → `Logger.d`，移除 `uuid=$uuid`

**B3. 回调访问字段初始化（L1831-1839）**：`audioRelayPlayer`、`currentAudioRelayUuid`、`pendingAudioRelaySend` 改为 `by lazy`（或前置声明），消除 startCore 到赋值完成间的 NPE 窗口

**B4. `stateQueryKeys`（L1507）**：`HashMap` → `ConcurrentHashMap`；设备移除（removeAuthenticatedDevice）与超级岛结束时删除对应键；增设容量上限/淘汰策略

### C. 音频与前台服务（M16 M20 M13 M14 M12 M6）

**C1. `app/.../feature/audio/AudioRelayPlayer.kt`（M16）**
- `stopSendCapture()`（L192-197）与 `stop()`（L199-204）：`runBlocking { cancelAndJoin() }` → `cancel()` 立即返回
- 新增 `suspend fun stopSendCaptureAndJoin()` 供协程调用方（`withContext(Dispatchers.IO)` 内 join）
- 调用方适配：DeviceConnectionManager L1875（BroadcastReceiver 主线程，用非阻塞版）、MusicControlPage L198

**C2. `AudioRelayPlayer.kt` `startSendCapture`（M20，L119-124）**
- `if (!isRunning) return` 后增加 `if (captureJob?.isActive == true || audioRecord != null) { Logger.w(...); return }`，再进入权限检查与资源创建

**C3. 前台服务 startForeground 保护（M13）**
- `MediaProjectionForegroundService.kt` L31：`ServiceCompat.startForeground(1002, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)` + try/catch（失败 `stopSelf()`）
- `AudioRelayForegroundService.kt` L53-58：`ServiceCompat.startForeground` 加 try/catch（类型与 manifest 一致，**不修改**）

**C4. `MediaProjectionForegroundService.kt` onForegroundReady 同步化（M14，L50-67）**
- 参照评审 diff：新增 `markReady()`，`onForegroundReady` setter 与 `markReady` 均走同一 `synchronized(lock)`，保证回调恰执行一次；`onStartCommand` 改调 `markReady()`

**C5. `app/.../ui/activity/MainActivity.kt` onDestroy（M12，L221-226)**
- onCreate 注册时分别保存 `screenCaptureReadyCallback` / `projectionRequestCallback` 实例引用
- onDestroy 仅当对应静态字段 `===` 本实例回调时才置 null；保留 `pendingScreenCapture = null`

**C6. `app/src/main/AndroidManifest.xml`（M6）**
- `NotifyRelayNotificationListenerService` 与 `AudioRelayForegroundService` 各加 `<meta-data android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="有意义的 subtype 描述"/>`

### D. 同步层（O4 M22 M21 M23 M26 M2 O2 M3 M29）

**D1. `sync/ConnectionDiscoveryManager.kt` `connectToAuthedDevice`（O4，L73-80）**
- 增加在线过滤：`val isOnline = deviceManager.devices.value[device.uuid]?.second == true`，`isAuthed && !isOnline` 才调用 `connectToDevice`

**D2. `ConnectionDiscoveryManager.kt` `startDiscovery`（M22，L253-263）**
- 停止直接调 `NativeCore.periodicBroadcast`，改为委托 `syncHeartbeatMode()` 统一决策（锁屏/WLAN 直连 → 停 UDP 广播）

**D3. `sync/ConnectionKeepAlive.kt`（M21，L36-55）**
- `performDeviceConnectionWithRetry` 内 `NativeCore.connectDevice` 包 `withContext(Dispatchers.IO)`，保留现有返回值与快速失败逻辑

**D4. `sync/HeartbeatProcessor.kt` 刷新节流（M23，L73-75）**
- `AtomicLong lastRefreshAt` + `REFRESH_MIN_INTERVAL_MS = 500`，`compareAndSet` 时间窗合并；UDP/mDNS/TCP 三条心跳链路共用

**D5. `HeartbeatProcessor.kt` 未认证写入（M26，L62-71）**
- 仅认证设备允许写入 `deviceInfoCacheInternal` 与 `updateGlobalDeviceName`；UDP/mDNS 名称复用 `decodeDisplayNameFromTransport` 清洗；`globalDeviceNameCache`/`deviceInfoCacheInternal` 增加清理或容量上限

**D6. `servers/NotifyRelayNotificationListenerService.kt` 串行发送（M2，L417-435）**
- 取消每通知独立 `CoroutineScope(Dispatchers.Default).launch`，改为服务持有的单一作用域 + 串行机制（Channel/Mutex）；服务 onDestroy 时 cancel 待执行任务

**D7. `sync/notification/SuperIslandProcessor.kt` dedupClear（O2，L121-130）**
- explicitFeatureKey 含 `|` 分支：`dedupClear(manager, dedupKey)` → `dedupClear(manager, explicitFeatureKey)`（直接使用完整 sourceId 清除原始去重记录）

**D8. `SuperIslandProcessor.kt` dedupKey 含内容维度（M3，L177-193）**
- `dedupKey` 追加状态版本或内容哈希（标题/进度/计时器更新产生不同键），仅丢弃完全相同的状态包；保留 isLocked 分支与 shouldSkipDedup

**D9. `sync/MessageSender.kt` 裸路径图片（M29，L250-269）**
- `v.startsWith("/")` 分支改为 `java.io.File(v)` 读取（`isFile` 判断，`data:image/png;base64,...`）；content/file:// 维持 `openInputStream`；catch 分支保留原值并加 `Logger.w`

### E. 媒体状态 — `app/.../feature/notification/superisland/RemoteMediaSessionManager.kt`（M1）

**E1. `cleanupRunnable` 懒初始化（L67/L75-94/L126/L138/L184）**
- `lateinit var` → `createCleanupRunnable()` 返回 Runnable；移除 `init(context)` 中 `initCleanupRunnable()` 调用
- 同时保护 `ensureCleanupLoop` / `stopCleanupLoop` / `clearSession` 路径（验证发现比评论更广：L184 关闭接收模式会在 handler 线程崩溃）

### F. 过滤与数据库（M7 M9 M8 M10 M4）

**F1. `data/.../database/AppDatabase.kt` 迁移默认值（M7）**
- `migrateRemoteBlackWhiteList`（L428-439）与 `migrateLocalFilterEntries`（L460-473）：enabledKey/集合不存在 → 全部写 `enabled=1`；仅 key 存在时按集合判断
- `BackendRemoteFilter.kt` `isActiveEntryEnabled`（L659-664）：统一注释与实现（迁移后默认启用）

**F2. `AppDatabase.kt` customEnabled 空串（M9，L496-501）**
- `.split(",").filter { it.isNotBlank() }.map { it == "1" }`，缺失/空值 → 各自定义组默认启用

**F3. `app/.../backend/BackendLocalFilter.kt` 写入串行化（M8，L97-125)**
- `setFilterEntryEnabled` / `addFilterEntry` / `removeFilterEntry` 共享 `Mutex`（照 `RemoteFilterConfig.saveLock` L472 模式），锁住读库与写库全程

**F4. `BackendLocalFilter.kt` 过滤条目缓存（M10，L252-259）**
- `@Volatile cachedRows` + `rowsCached()` 惰性加载；三个写入方法尾部 `invalidateCache()`

**F5. `gradle/wrapper/gradle-wrapper.properties`（M4）**
- 加 `distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`（与官方 sha256 校验一致）

### G. UI — `app/.../ui/screen/DeviceListScreen.kt`（M11）

**G1. 电量取绝对值（L299-303）**
- `val level = kotlin.math.abs(device.batteryLevel)`；`level <= 100` 且不等于当前值才更新 `batteryLevel.intValue`

### H. 剪贴板死代码清理 + 构建/CI（O5/O1 I1 M5）

**H1. 删除已停用的剪贴板监控（O5/O1）**
- 删除 `app/.../servers/clipboard/ClipboardMonitorService.kt` 类文件
- 删除 `app/src/main/AndroidManifest.xml` L126-131 服务声明
- 删除 `ClipboardSyncManager.kt` `sendClipboardToDevices`（L86-122，全仓库无调用点）
- 回归验证：通知点击复制同步、`FcitxClipboardReceiver` 输入法广播同步保持正常

**H2. `build.gradle.kts`（I1）**：buildscript 块加 `repositories { mavenCentral() }`（保持 dependencies 不变）

**H3. `.gitmodules`（M5）**：`nativecore/notify-relay-core` URL 由 SSH 改 HTTPS（公开仓库，CI 无需密钥；checkupdata/scrcpy 保持 SSH）

### I. PR 元数据

**I1. PR 标题**：`gh pr edit 52 --title "迁移 Rust Core 并更新 Android 构建与过滤配置"`

## 验证步骤

1. **构建验证**：`.\gradlew.bat assembleDebug`（首次构建禁止筛选输出）确认 Kotlin 编译与 buildscript 类路径解析
2. **剪贴板回归（H1）**：通知点击复制同步、输入法广播（FcitxClipboardReceiver）同步
3. **CI 验证（H3）**：推送后触发 PR 检查工作流，确认 notify-relay-core 子模块 HTTPS 拉取成功
4. **功能抽查**：音频中继启停与重复触发（C1/C2）、媒体浮岛显示与清理（E1）、超级岛状态更新（D7/D8）、升级后过滤条目默认启用（F1/F2）、设备列表电量显示（G1）

## 明确不做

- **O3（Actions 固定 commit SHA）**：用户确认不修不记录——浮动标签同样需人工提交更新，历史 commit SHA 不会被移除，威胁模型不适用本私有分发应用
- **Docstring 覆盖率**：项目为 Kotlin/Rust 非 Python，检查不适用
- 迁移逻辑之外的表结构改动