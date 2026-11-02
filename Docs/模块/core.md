# `:core`

Kotlin 绑定由 `:core:uniffiBindgen`（`gobley-uniffi-bindgen`）从 `cargo-ndk` 产出的 `.so` 生成，产出包为 `com.xzyht.notifyrelay.core.ffi`，生成物位于 `nativecore/build/generated/uniffi-kmp`，不入版本库。

模块为 KMP 库（android 单目标）。`uniffi.kmp.toml` 以 `kotlin_multiplatform = true` 产出双源集绑定： commonMain 为 `expect` 声明，androidMain 为 `actual` 实现（JNA 调用 `.so`）。两个目录分别注册到对应源集的 kotlin 目录。

| 源集 | 内容 |
|---|---|
| commonMain | UniFFI 绑定的 `expect` 声明（`Core`、`CoreCallbacks`、顶层函数）；`NativeCore` 门面；`PlatformMediaProjection` 的 expect 声明 |
| androidMain | UniFFI 绑定的 `actual` 实现（JNA）；`PlatformMediaProjection` 的 actual typealias |

`NativeCore`（object）是 `:core` 的对外门面，位于 commonMain，覆盖：

- 会话：`createContext()`, `isReady()`, `exportState()`, `importState()`, `encryptLocalState()`, `decryptLocalState()`, `deserializeLocalState()`
- 加密：`generateKeypair()`, `getPublicKey()`, `hasKeypair()`, `deriveSharedSecret()`, `migrateSharedSecret()`, `deriveFtpCredentials()`, `derivePasswordHash()`, `generateRandomPassword()`
- 设备：`getLocalUuid()`, `renameDevice()`, `getDeviceList()`, `removeDevice()`, `removeDeviceSession()`
- 连接/配对：`periodicBroadcast()`, `sendHandshake()`, `connectDevice()`, `sendPairingInit()`, `sendPairingResp()`, `sendAccept()`, `sendReject()`, `generatePairingCode()`, `clearPairingCode()`
- 消息：`enqueueMessage()`, `computeFeatureId()`, `dedup()`, `contentDedup()`, `shouldDeduplicate()`
- 过滤：`setFilterConfig()`, `mapLocalPackage()`, `checkFilterMode()`
- 同步：`clipboardOnChanged()`, `clipboardOnReceived()`, `appSyncPrepareIconRequest()`, `appSyncRequestIcons()`, `appSyncClearIconPending()`, `appSyncParseIconResponse()`, `appSyncBuildApplistRequest()`, `appSyncParseApplistResponse()`
- 状态推送：`pushSuperislandState()`, `pushMediaState()`, `onNetworkChanged()`, `getLocalIp()`, `updateHeartbeatSchedulerParams()`
- 回调：`setCallbacks(CoreCallbacks)`（配对/数据/设备事件/状态查询/音频/日志合一）
- 就绪判定：`isReady()`（上下文已创建）、`senderQueueReady`（`startCore` 已建立发送队列）

`NativeCore` 不持有上下文句柄；上下文由 UniFFI 生成的 `Core` 对象单一持有。

commonMain 的 `device/` 子包承载跨平台编排，两平台共用：

| 位置 | 内容 |
|---|---|
| `device/SendGateway` | 已认证设备快照读取与通知类入队（加密、限流、重试、去重由 Rust 承担） |
| `device/ProtocolSender` | 加密报文的统一发送入口 |
| `device/appsync/RemoteAppsCacheCore` | 远端应用列表缓存读写（按设备维度） |
| `device/appsync/RemoteAppIconCache` | 远端应用图标字节缓存 |
| `device/appsync/AppListSyncManagerCore` / `IconSyncManagerCore` | 应用列表与图标的请求/响应编排 |
| `device/appsync/ChatSendGateway` | 聊天测试报文构造与广播 |
| `device/appsync/InboundNotificationProcessor` | 入站通知解析、已入库检定、落库与裁剪；同时提供本机设备标识与记录键。**已入库检定的唯一判定处**为 `isDuplicate()`：10 秒内容窗口 → 24 小时内同包名候选的应用名 + 标题 + 正文相似度 → 更早记录完全一致；`markContentSeen()` 仅在确认展示后写窗口 |
| `device/notification/LocalNotificationForwardGateway` | **两平台共用**的本机通知出站网关：过滤（`LocalFilterRepository.shouldForward`）→ 可选按应用名反查远端包名 → 已入库检定 → 构造 `DATA_NOTIFICATION` → 可选落库 → 广播全部已认证设备。平台差异经参数表达：Android 传 `resolvePackage=false`、不重复落库并传 `existingKey` 排除自身，Windows 传 `true` 并由网关落库。**检定为重复时仍落库与广播，仅不上屏**（返回值 `ForwardResult.showOnScreen`）。**只发送新增通知**：本应用非镜像通知，不承担撤回语义 |
| `device/service/**` | 配对、设备目录与快照、在线设备缓存、回调适配与安装 |

本机通知的过滤规则位于 `:data` `filter/LocalFilterRepository.shouldForward`，纯判定部分抽为 `LocalForwardRules`（可脱离数据库单测）。
