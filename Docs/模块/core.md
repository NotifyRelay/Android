# `:core`

Rust 核心库的 KMP 封装。Kotlin 绑定由 `:core:uniffiBindgen`（`gobley-uniffi-bindgen` 0.3.7）从 `cargo-ndk` 与本机构建产出的动态库生成，产出包 `com.xzyht.notifyrelay.core.ffi`，生成物在 `nativecore/build/generated/uniffi-kmp`，不入版本库。

## 目标与源集

模块为 KMP 库，目标为 **android 与 jvm**（`uniffi.kmp.toml` 的 `kotlin_targets`）。`kotlin_multiplatform = true` 使其只产出双源集绑定：commonMain 为 `expect` 声明，androidMain 与 jvmMain 为 `actual` 实现（均为 JNA 调用动态库）。

| 源集 | 内容 |
|---|---|
| commonMain | UniFFI 绑定的 `expect` 声明（`Core`、`CoreCallbacks`、顶层函数）；`NativeCore` 门面；`CoreWatchdog`；`PlatformMediaProjection` 的 expect 声明 |
| androidMain | UniFFI 绑定的 `actual` 实现（JNA 加载 `.so`）；`PlatformMediaProjection` 的 actual typealias |
| jvmMain | UniFFI 绑定的 `actual` 实现（JNA 加载宿主动态库），供 `:desktop` 复用同一份 core |

Rust 产物分两条路径：`rustBuild` 经 `cargo-ndk` 交叉编译 arm64-v8a 与 x86_64 的 `.so` 随 AAR 分发；`hostRustBuild` 为本机目标产出动态库，供 jvmMain 使用。绑定统一从本机动态库导出，因此 `uniffiBindgen` 只依赖 `hostRustBuild`，JVM 构建不会被强制做一次交叉编译。

## `NativeCore` 门面

`NativeCore`（object）是 `:core` 的对外门面，按域分组：会话与加密、设备与配对、消息与过滤、剪贴板与应用列表同步、状态推送、音频与去重。方法清单以源码为本，本页只记无法从签名读出的契约：

- 上下文归属：句柄不归 `NativeCore`，由 UniFFI 生成的 `Core` 对象单一持有；门面只在 `coreRef` 里保存该实例。
- 就绪判定：`isReady()`（实例已创建）、`senderQueueReady`（`startCore` 已建立发送队列）。
- 回调：`setCallbacks(CoreCallbacks)` 一处登记配对、数据、设备事件、状态查询、音频与日志；音频帧与日志经门面转交给平台侧注册的通道。
- 失败语义：门面在实例缺失时返回与 core 一致的失败值（`-1`/`false`/`null`），调用方按既有分支处理，不会收到伪装成成功的默认值。

## `device/` 跨平台编排

commonMain 的 `device/` 子包承载跨平台编排，Android 与桌面共用：

| 位置 | 内容 |
|---|---|
| `device/SendGateway` | 已认证设备快照读取与通知类入队（加密、限流、重试、去重由 Rust 承担） |
| `device/ProtocolSender` | 加密报文的统一发送入口 |
| `device/DeviceTargetRegistry` | 目标设备集合注册 |
| `device/NativePairingCorePort` | 配对能力的 core 侧端口实现 |
| `device/appsync/RemoteAppsCacheCore` / `RemoteAppIconCache` | 远端应用列表与图标缓存 |
| `device/appsync/AppListSyncManagerCore` / `IconSyncManagerCore` | 应用列表与图标的请求/响应编排 |
| `device/appsync/IconCodecPort` | 图标编解码端口 |
| `device/appsync/ChatSendGateway` | 聊天测试报文构造与广播 |
| `device/appsync/InboundNotificationProcessor` | 入站通知解析、检定、落库与裁剪；判定唯一入口为 `isDuplicate()`，候选整体一次传入 core |
| `device/notification/LocalNotificationForwardGateway` | 两平台共用的本机通知出站网关，平台差异经参数表达；检定为重复时仍落库与广播，仅不上屏 |
| `device/filter/NativeFilterBridge` | 过滤能力端口，四方法直通 `NativeCore` |
| `device/service/**` | 配对、设备目录与快照、在线设备缓存、回调适配与安装 |

`device/service/callback/` 把单一 `CoreCallbacks` 拆成配对、数据、设备事件与心跳四组处理器；`RustCallbackInstaller` 负责装配，`CoreCallbacksAdapter` 负责音频与日志的转交。

本机通知的过滤规则位于 `:data` `filter/LocalFilterRepository.shouldForward`，纯判定部分抽为 `LocalForwardRules`。

## 去重存储

去重状态由 `DedupState` 持有，键为内容的 SHA-256 定长指纹（`[u8; 32]`），不保存原文。入站内容键把应用名、标题、正文各带长度前缀并入同一摘要，字段边界参与哈希，因此分隔符相同的不同字段组合不会得到同一键。

状态分出站与入站内容两域：出站域承载通知发送去重（3s 窗口）与平台经 `dedup` 接口登记的长窗口键（超级岛锁屏去重 300s）；入站内容域承载 10s 内容窗口，`clear_content` 只清本域。每个条目记录自身过期时刻（登记时刻 + 登记时给定的窗口），清理按该时刻回收，同域内不同窗口的键互不影响。

查重动作由 `apply_action`（出站）与 `apply_content_action`（入站内容）统一分发，C ABI 与 UniFFI 两个入口共用同一实现，返回语义只有一处定义。

相似度判定是独立的通道：`should_deduplicate` 按「应用名指向同一应用 + 标题与正文加权相似度 ≥ 0.8」判定，`find_similar_candidate` 接受一批候选并单次返回首个命中下标，使跨语言调用次数与候选数量解耦。

<a id="locking"></a>

## 锁与阻塞边界

核心状态由 `SafeContext`（`Mutex<CoreContext>`）保护，另有三处状态持有独立锁、不参与该互斥：音频流、去重状态与子模块内部的守护集合。需要它们的路径先在上文锁内取 `Arc` 句柄，再在锁外锁定，避免把查重等短操作串到网络与持久化之后。

锁内只取句柄与读写字段；网络收发、拨号、mDNS 重注册与线程 `join` 在锁外执行且自带超时（发送 5s / 握手 8s）。后台线程锁序恒为「上下文锁 → 组件锁」。发送队列在丢弃或替换队列项时无法就地取锁，改为登记待补清理的去重键，由未持锁的一侧统一释放，否则相同负载会被永久判为重复。

设备离线是正常状态：发送队列只记信息级日志并退避重试，已在线却失败才告警。

`CoreWatchdog` 统计核心调用在途数量，自首个未返回调用起超过 10s 即 `requestShutdown()`；该接口只置位信号、不取核心锁，因此核心卡死时仍可返回。恢复依赖 `createContext()` 重建实例，重建后调用 `reset()` 复位统计以获得完整超时窗口。

## 剪贴板同步

判定在 core。`clipboardOnChanged` 在收发两侧把内容归一到同一口径（`data:image/...;base64,` 去前缀、换行统一为 `LF`），再按归一化内容排除回灌：与最近一次接收一致且在 1000ms 窗口内即跳过，不要求类型一致；图片形态是平台重编码后的 PNG，内容不可比，按类型与时间窗兜底。其后依次是"内容未改变"（`force` 时跳过）、频率限制与 2MB 阈值。

平台对 core 的义务是口径一致：交给 `clipboardOnChanged` 的内容必须是回读时的那一个字符串（回包按 JSON 语法解码），写回前后用同一口径归一；否则内容相等前提不成立，回灌会被当成新的本机复制再次发出。写回形态见[桌面端](../桌面端.md#clipboard)与 [`:app`](app.md)。
