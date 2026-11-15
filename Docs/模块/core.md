# `:core`

Kotlin 绑定由 `:core:uniffiBindgen`（`gobley-uniffi-bindgen`）从 `cargo-ndk` 产出的 `.so` 生成，产出包为 `com.xzyht.notifyrelay.core.ffi`，生成物位于 `nativecore/build/generated/uniffi-kmp`，不入版本库。

模块为 KMP 库（android 单目标）。`uniffi.kmp.toml` 以 `kotlin_multiplatform = true` 产出双源集绑定： commonMain 为 `expect` 声明，androidMain 为 `actual` 实现（JNA 调用 `.so`）。两个目录分别注册到对应源集的 kotlin 目录。

| 源集 | 内容 |
|---|---|
| commonMain | UniFFI 绑定的 `expect` 声明（`Core`、`CoreCallbacks`、顶层函数）；`NativeCore` 门面；`PlatformMediaProjection` 的 expect 声明 |
| androidMain | UniFFI 绑定的 `actual` 实现（JNA）；`PlatformMediaProjection` 的 actual typealias |

`NativeCore`（object）是 `:core` 的对外门面，位于 commonMain，按域分组：会话与加密、设备与配对、消息与过滤、剪贴板与应用列表同步、状态推送。方法清单以源码为本，本页只记无法从签名读出的契约：

- 就绪判定：`isReady()`（上下文已创建）、`senderQueueReady`（`startCore` 已建立发送队列）
- 生命周期：`requestShutdown()` 置位关停信号（不取核心锁），`createContext()` 重建实例
- 回调：`setCallbacks(CoreCallbacks)` 一处登记配对、数据、设备事件、状态查询、音频与日志

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
| `device/appsync/InboundNotificationProcessor` | 入站通知解析、已入库检定、落库与裁剪；已入库检定的唯一判定处为 `isDuplicate()` |
| `device/notification/LocalNotificationForwardGateway` | 两平台共用的本机通知出站网关，平台差异经参数表达；检定为重复时仍落库与广播，仅不上屏 |
| `device/service/**` | 配对、设备目录与快照、在线设备缓存、回调适配与安装 |

本机通知的过滤规则位于 `:data` `filter/LocalFilterRepository.shouldForward`，纯判定部分抽为 `LocalForwardRules`（可脱离数据库单测）。

<a id="locking"></a>

## 锁与阻塞边界

全部核心状态由单一 `SafeContext`（`Mutex<CoreContext>`）保护。锁内只取句柄与读写字段；网络收发、拨号、mDNS 重注册与线程 `join` 在锁外执行且自带超时（发送 5s / 握手 8s）。后台线程锁序恒为「上下文锁 → 组件锁」。

设备离线是正常状态：发送队列只记信息级日志并退避重试，已在线却失败才告警。

`CoreWatchdog` 统计核心调用在途数量，超过 10 秒未返回即 `requestShutdown()`。该接口只置位 `lifecycle` 原子信号、不取核心锁；后台循环据信号退出，协调线程有界等待内释放 mDNS 与传输层。恢复依赖 `createContext()` 重建实例。

## 剪贴板同步

判定在 core。`clipboardOnChanged` 在收发两侧把内容归一到同一口径（`data:image/...;base64,` 去前缀、换行统一为 `LF`），再按归一化内容排除回灌：内容与最近一次接收一致且在 1000ms 窗口内即跳过，不要求类型一致；图片在剪贴板中的形态是平台重编码后的 PNG，内容不可比，按类型与时间窗兜底。其后依次是"内容未改变"（`force` 时跳过）、1000ms 频率限制与 2MB 阈值。

平台对 core 的义务是口径一致：交给 `clipboardOnChanged` 的内容必须是回读时的那一个字符串（回包按 JSON 语法解码），写回剪贴板前后用同一口径归一；口径不一致时内容相等前提不成立，回灌会被当成新的本机复制再次发出。写回形态见[桌面端](../桌面端.md#clipboard)与 [`:app`](app.md)。
