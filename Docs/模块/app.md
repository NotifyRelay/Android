# `:app` — 关键类速查

| 包 | 类 | 职责 |
|---|---|---|
| `util` | `ApkArchMatcher` | 依据设备 ABI 匹配更新包 |
| `sync` | `ConnectionDiscoveryManager` | 局域网设备发现 |
| `sync` | `ConnectionKeepAlive` | 保活与重连 |
| `sync` | `FtpServerManager` / `FtpServerInfo` | 内置 FTP 服务（图标/文件分发） |
| `sync` | `HeartbeatProcessor` / `ProtocolSender` / `MessageSender` | 心跳、协议封装、消息发送（`MessageSender` 为门面，`SendGateway` 与 `builder/*` 为具体实现） |
| `sync/notification` | `NotificationProcessor` / `StatusProcessor` / `SuperIslandProcessor` | 三类协议的处理 |
| `feature/appslist` | `InstalledAppsRepository` / `AppIconRepository` / `PinnedAppsRepository` / `RemoteAppsCache` / `AppListSyncManager` / `IconSyncManager` / `AppLaunchManager` | 应用列表采集与枚举、图标缓存、跨端同步与远程拉起（枚举与标签获取工具在 `:base` 的 `AppListHelper`） |
| `feature/clipboard` | `ClipboardSyncManager` / `ClipboardProcessor` / `ClipboardSyncReceiver` / `FcitxClipboardManager` / `FcitxClipboardReceiver` | 剪贴板同步（含 Fcitx 输入法适配）：系统剪贴板监听即 Fcitx5 的剪贴板广播（仅条目文本非空时广播），另一条取剪贴板路径是通知点按经透明 Activity；写回文本按原样、图片写成内容 URI。判定在 [`:core`](core.md) |
| `feature/audio` | `AudioRelayPlayer` / `AudioRelayForegroundService` / `AudioRelayController` | 音频转发播放 |
| `feature/device` | `DeviceConnectionManager`(+`Singleton`) | 设备连接总控；单例获取唯一入口为 `DeviceConnectionManagerSingleton.getDeviceManager(context)` |
| `feature/device/service/core` | `RustCoreSession` | 封装 `NativeCore` 会话 |
| `feature/device/service/callback` | `RustCallbackInstaller` / `DataCallbackHandler` / `DeviceEventCallbackHandler` / `PairingCallbackHandler` / `DeviceCallbackHost` / `HandshakeRequestHandler` | Rust 回调分发 |
| `feature/device/service/pairing` | `PairingCoordinator` / `HandshakeWaiterRegistry` | 配对流程编排 |
| `feature/device/service/state` | `DeviceDirectory` / `DeviceQuery` / `DeviceSnapshotStore` / `DeviceTargetRegistry` / `OnlineDevicesCache` / `PairedDeviceRemover` | 设备状态存储与查询；`DeviceSnapshotStore` 为「在线且已配对」判定的单一投影来源（`onlinePairedUuids` / `onlinePairedAddressableSnapshots`），`DeviceDirectory` 与 `OnlineDevicesCache` 均消费该投影 |
| `feature/device/service/system` | `SystemStateMonitor` / `LegacyDeviceMigrator` | 系统状态监听与旧数据迁移 |
| `feature/device/model` | `NotificationRecordStore`（Room 持久化单例）/ `NotificationRepository`（历史流/增删/同步/缓存，含内存态与设备列表派生）/ `NotificationTextReader`（文本/verify_code 读取工具）/ `NotificationCacheCleaner`（缓存清理/历史老化清理）/ `NotificationPersistence` / `DeviceModels`（`DeviceInfo` / `AuthInfo` / `PendingPairing`）/ `DeviceNameCache` / `DeviceSnapshot` / `HandshakeRequest` | 通知记录的持久化与读取；`NotificationRepository` 为历史流与增删同步的对外门面，其 `deviceList` 由 core 快照的已配对 uuid 集合派生（本机在首位） |
| `feature/media` | `MediaControlUtil` / `RemoteMediaSessionManager` / `MediaSessionMonitorService` / `MediaProjectionForegroundService` / `MediaMessageReceiveMode` / `MediaSessionTimeoutCleaner` / `MediaSessionResender` / `MediaStateApplier` | 媒体会话监听与远程控制；接收模式枚举、超时会话清理与复传调度、媒体状态构建与应用。进度按 superislandui 计时器口径在接收端本地推算，`RemoteMediaSessionManager.sessions` 是分设备会话流的唯一拥有者。`MediaSessionTimeoutCleaner.host` 在 `onMediaMessageReceived` 首次调用时惰性执行 `RemoteMediaSessionManager.init()` 完成 `bind()`。 |
| `feature/media/lyric` | `LyricFieldProbe` / `LyricResolver` | 歌词字段探测与歌词状态解析 |
| `feature/media/service` | `MediaControllerRegistry` / `MediaSessionHealthMonitor` | 媒体控制器注册表、会话健康检查与重试 |
| `feature/notification` | `NotifyRelayNotificationListenerService` / `ListenerForegroundController` / `MediaNotificationHandler` / `NotificationProcessedCache` / `HighPriorityNotifier` | 系统通知监听入口、前台通知与 WakeLock、媒体通知处理、已处理通知去重缓存、本地高优先级悬浮通知（5s 自动取消） |
| `feature/notification/filter` | `BackendLocalFilter` / `BackendRemoteFilter`（门面）/ `RemoteFilterConfig` / `RemoteFilterPlaceholderQueue` / `RemoteFilterPendingMonitor` | 本地/远端通知过滤规则、延迟复刻占位队列与待撤回监控（标题前缀归一由 `:base` 的 `TextUtils.normalizeTitle` 提供）。**已入库检定不在本包**，见 `:core` 的 `InboundNotificationProcessor.isDuplicate` |
| `feature/notification/superisland` | `SuperIslandConfigUtils`（纯配置读写与通道推导）/ `SuperIslandStructuredDataHelper` / `SuperIslandDataFormatter` / `SuperIslandHistoryStore` / `SuperIslandImageStore` / `LocalSuperIslandTracker` / `SuperIslandActions`（广播 action 契约，`contract/`） | 超级岛数据格式化、历史与图片存储；`applyLocalNotificationSpecInjection` 按「规范信息注入方式」为本机前台通知（音频中继 1003 / 屏幕捕获 1002）注入规范信息 |
| `feature/notification/superisland/cache` | `SuperIslandBitmapCaches` | 超级岛位图与图标缓存的统一回收入口：`clearAll()` 依次清 LiveUpdates 图标缓存、`BitmapUtils` 分配池、`ReplicaIconCache`、`SuperIslandImageCache`，各步独立捕获 |
| `feature/notification/superisland/floating` | `FloatingWindowManager` / `FloatingWindowContainer` / `FloatingComposeContainer` / `FloatingWindowLifecycleOwner` / `ComposeFloatingView` / `ParamIslandCompose` | 悬浮窗管理与 Compose 渲染（`FloatingWindowLifecycleOwner` 为两套 LifecycleOwner 的唯一实现） |
| `feature/notification/superisland/intent` | `NotificationIntentFactory` | 通知意图与渠道工厂；`needClickIntent` 的唯一判定处 |
| `feature/notification/superisland/list` | `SuperIslandListManager` / `FloatingReplicaListModeManager` | 列表模式状态与通道薄壳 |
| `feature/notification/superisland/media` | `MediaCapsulePresenter` / `LyricsSplitter` | 媒体胶囊展示与歌词拆分（拆分逻辑单一来源） |
| `feature/notification/superisland/notification` | `NotificationGenerator`（编排门面）/ `MediaReplicaNotifier` / `GeneralReplicaNotifier` / `ReplicaIconCache` / `ReplicaScrollUpdater` / `ReplicaSmallIconInjector` / `LiveUpdatesNotificationManager` / `LiveUpdatesProgressStyleBuilder` / `LiveUpdatesIconLoader` | 实况通知生成；`LiveUpdatesNotificationManager.dismiss(sourceId, ctx?)` 为 Live Updates 撤回唯一入口 |
| `feature/notification/superisland/pipeline` | `SuperIslandDisplayPipeline` + `DisplayRequest` / `Channel` | 三通道（浮窗/列表/通知）共享的「展示一跳」：版本与竞态双守卫、标题回退、注入模式迁移、指纹与跳过刷新、LU/复刻二路发送 |
| `feature/notification/superisland/replica` | `FloatingReplicaManager`（展示+关闭统一门面）/ `FloatingReplicaWindowManager`（纯 overlay 宿主）/ `FloatingReplicaNotificationManager`（通知通道薄壳）/ `ReplicaTtlRegistry`（两组 TTL）/ `ReplicaNotificationCloser`（取消通知副作用）/ `ReplicaDisplayCache`（通道切换时的展示内容重建来源）/ `ReplicaErrorHandling`（包内异常吞没 helper） | 悬浮岛镜像副本（多窗口/列表模式）；状态与副作用分离 |
| `feature/notification/superisland/store` | `SuperIslandSourceStore` | 超级岛按 sourceId 聚合的状态存储（单一来源）：远端载荷（sourceId → `DiffSystem.State`）与本地生命周期（sourceId → entryKey 集合 / 通知 id / 版本号 / 内容指纹 / 注入模式 / 超时任务 / 隐藏条目）同处一个对象；`clear()` 只清远端载荷，`clearAllMappings()` 清本地生命周期 |
| `ui/activity` | `MainActivity`(+`MainActivityContent` / `MainActivityPermissions` / `ScreenCaptureCoordinator` / `SuperIslandTestLauncher`) / `GuideActivity` / `DeveloperModeActivity` / `ClipboardSyncActivity` | 四大 Activity 与主界面组件 |
| `ui/navigation` | `Navigator` / `Route` | Navigation3 导航 |
| `ui/screen` | `DeviceListScreen` / `DeviceForwardScreen` / `HistoryScreen` / `SettingsScreen` / `MainScreen` | 主界面四个 Tab 与容器 |
| `ui/screen/devicelist` | `DeviceListScreenState` / `GlobalSelectedDeviceHolder` / `DeviceListButtons`（含 `DeviceListScreenContent`） / `DeviceListDialogs` | 设备列表状态持有、按钮组件与对话框 |
| `ui/pages` | `NotificationHistory` / `SuperIslandHistory` / `RemoteAppsPage` / `MusicControlPage` / `ClipboardSyncPage` / `SuperIslandSettings` / `UILocalFilter` / `UIRemoteFilter` / `FilterListSection` / `UIAbout` / `UIAppearance` / `ChatTest` / `DeleteButton` / `DeleteSwipeWidth`（滑动删除宽度 80dp 及其像素值，历史页共用）/ `TimeFormatters` | 二级页面 |
| `ui/pages/history` | `NotificationListBlock` / `NotificationCard` / `NotificationHistoryScaffold` | 通知历史页的列表块、卡片与脚手架（`NotificationCard` 含可选 `onLongClick` / `enableClick` / `showPackageName`，供字段转储页复用展示） |
| `ui/devtools` | `NotificationFieldDumpPage` / `NotificationFieldDumpViewModel` / `NotificationFieldDump` / `NotificationDumpCache` | 通知字段转储页（开发者选项入口）：抓取当前活跃通知的全部字段，卡片外显包名、长按按下拉框选择复制或分享 JSON；字段数据仅内存留存，导出文件集中在 `externalCacheDir/notification_dump/` 并可一键清理 |
| `ui/pages/remoteapps` | `DisplayInfo` / `RemoteAppsPageState` / `RemoteAppsMenuHost` / `LocalAppsContent` / `RemoteAppsContent` / `DisplayNavigationBar` / `AppGridItems` | `RemoteAppsPage` 的显示器模型、页面状态（搜索词由 `AppsState` 持有）、菜单宿主、本地/远程应用网格、多显示器底栏与网格单元 |
| `ui/pages/superisland` | `SuperIslandHistoryListBlock` / `SuperIslandHistoryGroupCard` / `SuperIslandHistorySummaryRow` / `SuperIslandHistoryStoreEntryCard` / `SuperIslandHistoryImage` / `rememberAppIconBitmap` / `SuperIslandAppIcon` / `SuperIslandImageCache` / `formatTimestamp` / `buildEntryCopyText` / `copyEntryToClipboard` / `triggerFloatingReplica` / `sanitizeImageContent` | 超级岛历史页的卡片、图片图标、格式化工具与图片缓存 |
| `ui/guide` | `GuideScreen` + `GuideWelcomePage` / `GuideAgreementPage` / `GuideRequiredPermissionPage` / `GuideOptionalPermissionPage` / `GuideBasicSettingsPage` / `GuideCompletePage` / `GuideComponents` / `GuidePermissionUiState` | 首次启动引导 |
| `ui/viewmodel` | `*ViewModel` / `*UiState` / `*PagingSource` / `AppIconPreloader` / `AppsState`+`AppsStateMachine` | UI 状态与 Paging3 数据源、历史列表图标批量预加载；本地/远程应用列表共用同一套状态与加载/搜索流程 |
| `ui/common` | `ScrollableTopAppBarPage` / `Theme` / `DoubleClickConfirm` / `NavigationEventProvider` / `MiuixSpinnerPreference` | 通用 UI 组件与工具（`SystemBarUtils` 位于 `:base`；`ScrollableTopAppBarPage` 为统一页面根容器，独立页面优先复用） |
