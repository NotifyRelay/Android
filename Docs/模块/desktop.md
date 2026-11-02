# `:desktop`

KMP 桌面端入口，仅 `jvmMain`（jvm 单目标 + Compose Desktop）。包根 `notifyrelay.desktop.*`。

| 文件 | 职责 |
|---|---|
| `Main.kt` | 入口：初始化数据层 → core 自检 → 启动设备管理 → 声明原生托盘与菜单 → 按 `ThemePort` 应用 `NotifyRelayTheme` 深浅色 → 提供各端口与 `ViewModelStoreOwner` → `NavDisplay` 渲染 `:shared` 的 `MainScreen` 与子页 |
| `DataLayerDesktop.kt` | 数据层初始化：以 `AppDatabaseJvmFactory` 打开用户目录下的数据库，`Storage.initialize`，并把全部端口注册到各 Registry |
| `CoreSelfCheck.kt` | Rust 核心自检（JNA 加载本机动态库），结果写入用户目录 |
| `device/DesktopDeviceManager.kt` | 设备列表与配对：状态来自 core 快照，配对经 `PairingCoordinator`；发送经 `ProtocolSender`，入站通知回调集合与 `DeviceAuthTablePort` 实现，在线设备缓存落 `Storage`；启动、周期广播与配对应答带本机带符号电量，电源变化时更新心跳调度器参数 |
| `device/DesktopDevicePortsProvider.kt` | 设备端口提供者（查询/管理/消息/聊天），消息与聊天端口转发到同一管理器 |
| `device/DesktopDataMessagePort.kt` | 入站数据报文端口：通知报文广播到管理器回调集合；应用列表与图标回包交由 `AppListSyncManagerCore`/`IconSyncManagerCore`（本机应用不列出时回空列表、图标回 missing）；其余报文空实现 |
| `device/DesktopDeviceScreen.kt` | 设备列表页 |
| `device/MachineName.kt` | 本机设备名解析 |
| `platform/DesktopUiPorts.kt` | 提示/剪贴板/跳转/主题（索引持久化到 `Storage`）/系统栏/位图端口实现 |
| `platform/Win32Clipboard.kt` | Win32 剪贴板纯文本写入（JNA 调 user32/kernel32，写 `CF_UNICODETEXT`），绕开 AWT 写路径把 `\n` 换成 `\r\n`；提供 `normalizeNewlines`（`CRLF` → `LF`）与 `isAvailable`，非 Windows 时回落 AWT |
| `platform/DesktopExtraUiPorts.kt` | 偏好/图片解码（data URL 与 http(s) 经 Skia 解码，LRU 32，最长边 320）/显示器/应用启动/主界面/设置端口实现 |
| `platform/DesktopExtraPorts.kt` | 端口实现：超级岛配置委托 `SuperIslandConfigPolicy`；音频中继与媒体控制为中性实现 |
| `platform/DesktopFilterPorts.kt` | 与 Android 共用 `RemoteFilterRepository` / `LocalFilterRepository` / `PinnedAppsRepository` 的端口实现 |
| `platform/DesktopPlatformPorts.kt` | 设备形态（非平板、无系统版本）、已安装包（恒空集）与 Rust 过滤能力端口 |
| `platform/DesktopIconCodecPort.kt` | 图标编解码端口：PNG 字节 ↔ Skia 位图；本机图标来源恒 null |
| `platform/DesktopSuperIslandHistoryPort.kt` | 超级岛历史端口：分页源与历史存储复用 `:data` 实现，应用名回落包名、图标为 null |
| `platform/DesktopAppPorts.kt` | 本机应用、远端应用列表与应用图标端口实现 |
| `platform/DesktopHistoryPort.kt` | 通知历史端口（直接读 Room） |
| `platform/DesktopDeviceListPort.kt` | 设备列表电量与充电状态（取 `PowerStatusMonitor.snapshot`）、横屏端口实现；图标字体取 `:shared` 的 MDL2 字体 |
| `platform/DesktopNotificationActionPort.kt` | 通知历史交互端口实现 |
| `platform/AppIcon.kt` | 由 `:shared` 的矢量图标（`app_icon.svg`）按尺寸栅格化，供窗口、托盘、标题栏图标使用；构建期由 `tool/IconTool.kt` 生成打包用 `.ico` |
| `platform/DesktopWindowChrome.kt` | 自绘标题栏、1dp 圆角描边、四边四角调整大小感应区与拖动移动；最大化状态在此持有并同步 DWM 圆角与描边形状 |
| `platform/DesktopWindowPlacement.kt` | 主窗口位置、尺寸与最大化标记的持久化与还原 |
| `platform/DesktopWindowController.kt` | 窗口控制：关闭窗口隐藏到托盘、显示/隐藏/互切、退出；托盘图标与菜单由 `platform/DesktopTray.kt` 提供 |
| `platform/DesktopSingleInstance.kt` | 单实例：经 `nucleus.core-runtime` 的 `SingleInstanceManager` 持锁；二次启动写恢复请求，主实例显示窗口 |
| `platform/DesktopAutoLaunch.kt` | 开机自启：经 `nucleus.autolaunch` 按打包格式解析后端；托盘「开机自启」勾选项读写该状态 |
| `platform/DesktopSystemMediaControl.kt` | 系统媒体中心：音频中继播放期间发布标题/艺人与播放状态，系统媒体按键转发为对源设备的 `DATA_MEDIA_CONTROL` 报文 |
| `platform/DesktopTray.kt` | 原生托盘：图标取 `SharedAppIcon.drawable`，左键显示主窗口，右键菜单为「显示主窗口/退出」；原生回调统一切回 Swing 事件线程 |
| `platform/DesktopSingleInstance.kt` | 单实例守卫：用户目录锁文件 + 回环激活报文；重复启动时唤起已有实例的窗口并退出 |
| `platform/BatteryLevel.kt` | 电源状态读取：Windows 经 kernel32 `GetSystemPowerStatus` 取电量与充电状态；提供无符号 `percent()` 与带符号 `signedPercent()`（正=充电、负=放电）；非 Windows 或读取失败回落 100／未充电 |
| `platform/PowerStatusMonitor.kt` | 电源状态变化监听：经 powrprof `PowerSettingRegisterNotification` 注册电池剩余电量与交流电来源两项系统通知，变化时刷新可观察快照并回调（供心跳调度器实时更新电量） |
| `platform/DesktopPairingDependencies.kt` | 配对依赖注入：核对走 `NativePairingCorePort`，握手等待器取设备管理器的同一实例，电量取 `BatteryLevel.signedPercent()`，平台标识 `pc` |
| `platform/DesktopOverlaySettings.kt` | 叠加层设置：叠加层总开关与弹幕样式经 `Storage` 持久化；扩展点四类设置（余额开关与凭据与轮询间隔、罗技开关与隐藏离线与设备名覆盖、键盘开关与映射规则、时间开关与格式）与各元素的位置/缩放/颜色/描边经内部 `Anchor` 读写，**每个元素的默认位置/缩放/描边各自独立**。`Anchor` 各字段以 Compose mutableState 为背景态：拖动滑块时直接写入触发重组（浮窗位置实时跟手），松手时经 `persist()` 落盘。另暴露 `revision` 与 `notifyChanged()` 供非锚点设置（开关、显示格式等）观察变更 |
| `platform/DesktopOverlaySettingsContent.kt` | 叠加层设置主页（总开关 + 五个子页入口）与「弹幕与媒体卡」子页（字号/速度/区域/测试弹幕/媒体卡收起）。数值项一律用滑块，公共辅助为 `OverlaySlider` 与 `snapToStep`。`OverlaySlider` 回调拆分为 `onValueChange`（拖动期实时，更新背景态）与 `onValueChangeFinished`（松手落盘） |
| `platform/DesktopOverlayFeaturesContent.kt` | 四个扩展点子页：余额（凭据录入与立即查询、轮询间隔）、罗技电池（隐藏离线、设备列表与立即刷新、设备名修改）、键盘（开关与映射的启用/删除/按键录入）、时间（显示秒与 24 小时制），以及各元素位置/缩放/描边滑块 |
| `platform/KeyboardMappingStore.kt` | 快捷键映射的 JSON 编解码，落到 `Storage`；解码失败按空列表处理并记告警 |
| `platform/LogiDeviceNameStore.kt` | 罗技设备自定义名的 JSON 编解码，落到 `Storage`；键为设备标识，解码失败按空表处理并记告警 |
| `overlay/OverlayElements.kt` | 扩展点浮窗渲染：余额、罗技电池、键盘状态、时间；定位按「屏幕百分比锚点 + 内容居中」，文本为黑色描边层与彩色填充层叠放。罗技浮窗为单列卡片（黑色半透明圆角底 + 白色细边框），卡片内为 Segoe MDL2 电量图标（复用 `BatteryIconConverter` 的字形与分段着色）与设备名，设备名超长以省略号截断。键盘浮窗为按键盒（蓝底白边圆角，每行最多 10 个，映射提示黑底显示在按键行下方并在最后 30% 时长淡出）：按下键全部映射为键名显示，Caps Lock 开启显示「大写」，Num Lock 常态开启、仅关闭时显示「NumLk关」，Scroll Lock 开启时显示。时间浮窗只读 `DesktopOverlayStore.clockText`，不自行计时 |
| `overlay/feature/DesktopOverlayFeatures.kt` | 扩展点装配：持有四项 `OverlayFeature` 实现并统一启停；具体生命周期与数据订阅由各 Feature 自管，本类只做编排与供设置页访问的委托。`current` 供设置页即时同步 |
| `overlay/feature/DesktopBalanceFeature.kt` | 余额扩展点：实现 `OverlayFeature`，封装 `DesktopBalanceSource` 的生命周期与数据订阅，注册到 `OverlayFeatureRegistry` |
| `overlay/feature/DesktopLogiBatteryFeature.kt` | 罗技电池扩展点：实现 `OverlayFeature`，封装 `DesktopLogiBatterySource` |
| `overlay/feature/DesktopKeyboardFeature.kt` | 键盘扩展点：实现 `OverlayFeature`，封装 `DesktopKeyboardSource` |
| `overlay/feature/DesktopClockFeature.kt` | 时间扩展点：实现 `OverlayFeature`，封装 `DesktopClockSource`（纯 JVM） |
| `overlay/feature/DesktopClockSource.kt` | 时间数据源（纯 JVM，无 JNA）：`java.time.LocalTime` 按配置格式化，秒级或分钟级刷新 |
| `overlay/feature/DesktopBalanceSource.kt` | DeepSeek 余额数据源：JDK `HttpClient` 轮询 `/user/balance`，优先取 CNY 余额；余额未变只刷新末条时间，变化时追加并算变化量；历史超 500 条时合并最旧的同向连续项 |
| `overlay/feature/DesktopBalanceHistoryStore.kt` | 余额历史持久化（JSON 落 `Storage`） |
| `overlay/feature/DesktopLogiBatterySource.kt` | 罗技电池数据源：后台协程按 30 秒轮询枚举（阻塞 HID IO），结果与上次一致时不广播 |
| `overlay/feature/LogiBatteryNative.kt` | `logi_battery.dll` 的 JNA 声明与枚举门面：结构体与 Rust `LbDeviceInfo` 逐字段对应，**对齐固定为 1**（Rust 侧最大对齐字段为 `u16`，结构体 266 字节，按平台默认对齐会算成 272 导致数组步长错位）；内部完成分配→读取→释放 |
| `overlay/feature/DesktopKeyboardSource.kt` | 键盘监控数据源：自建专用线程安装全局低级键盘钩子并泵消息循环（`WH_KEYBOARD_LL` 回调只投递到安装线程）；回调内只更新状态并合并推送，切换键在按下时翻转锁定状态（初值在安装时经 `GetKeyState` 读取）；修饰键以 `GetAsyncKeyState` 全局真值为准并每 50ms 轮询自愈（钩子漏掉抬起边沿时下次轮询清除）；推送按快照等值跳过，映射命中时投递提示 |
| `overlay/feature/KeyboardHookBridge.kt` | 钩子原生封装：`SetWindowsHookExW` / `UnhookWindowsHookEx` / `GetMessageW` 消息泵 / `PostThreadMessageW` 退出 / `GetKeyState` 读取 / `GetAsyncKeyState` 全局按下状态 |

屏幕镜像设置页 `SettingsScrcpy`、`ScrcpyAdvanced` 接公共页面，`ScrcpyVirtualButtonOrder` 为占位页。历史页「超级岛历史」「聊天测试」、设置页「超级岛」与「开发者选项」均已接公共页面；桌面端无平台专属槽位（复刻、测试分支、转储页与引导页分支均不显示）。

通知与超级岛的显示层在本模块的 `overlay` 包内：无边框、透明、置顶、点击穿透且不抢焦点的窗口，用 Compose 直接绘制媒体卡、超级岛卡片与弹幕；媒体卡展开态复用 `:superislandui` 的媒体大岛，超时后收起为摘要胶囊（短标题贴右对齐、频谱开关开启时频谱条贴右端常显），换曲时重新展开且不带操作按钮（仅用户点开的展开显示）；超级岛卡片与安卓浮窗共用 `:superislandui` 的 `SuperIslandTemplateCompose`（按 paramV2 分派媒体大岛 / 摘要区 / 模板组件）与 `SuperIslandExpandableCard`（展开/收起尺寸形变），3 秒后收起为摘要胶囊、12 秒无更新自动移除。非锚点设置（交互、频谱、操作按钮、收起时长）经 `revision` 观察，切换即时生效。窗口的 TOPMOST 层级由 `DesktopOverlayTopmostMonitor` 持续校准（丢失即恢复、每秒重断言、跟随显示器尺寸），帧心跳超时则清除置顶。超级岛卡片的 `paramV2Raw` 布局解析待接入。

叠加层扩展点（余额 / 罗技电池 / 键盘 / 时间）复用同一窗口渲染，不引入宿主编排器：`DesktopOverlayFeatures` 在窗口创建后装配。开关口径为「叠加层总开关 + 各元素开关」同时满足才生效，总开关关闭时不创建窗口也不启动扩展点；键盘项的全局低级键盘钩子随该口径安装与卸载，不做常驻。四类默认值：余额默认关（填入凭据后由用户开启），罗技电池、键盘、时间默认开，罗技仅在枚举到设备时显示。余额与罗技的枚举/轮询在后台，键盘钩子在专用消息泵线程，均不占用渲染线程。

可分发包由 `gradlew :desktop:createDistributable` 产出（`desktop/build/compose/binaries/main/app/NotifyRelay/NotifyRelay.exe`），直接运行该可执行文件即可；`gradlew :desktop:runDistributable` 运行同一产物，IDE 运行配置 `desktop [jvm]` 即指向该任务。三个 Rust 动态库（`notify_relay_core.dll`、`smtc_jni.dll`、`logi_battery.dll`）由 `prepareDistributableNatives` 复制进应用镜像的 `app/resources`，运行时由 `compose.application.resources.dir` 补齐 JNA 与 `System.loadLibrary` 的搜索路径；应用镜像保留控制台输出，stdout/stderr 均为 UTF-8。

主窗口为无边框窗口，标题栏、边框与调整大小由 `platform/DesktopWindowChrome.kt` 自绘：标题栏含应用图标、标题与最小化/最大化/关闭按钮，拖动移动按指针绝对屏幕坐标与按下时的窗口内偏移反推窗口位置（Win32 `SetWindowPos`，拖动期间不重绘），四边四角有拖拽调整大小的感应区，窗口内容外有 1dp 圆角描边；DWM 圆角随窗口状态切换（最大化时为直角）。窗口位置、尺寸与最大化标记由 `platform/DesktopWindowPlacement.kt` 持久化：拖动/缩放结束后延迟落盘，最大化切换、首次显示与退出时也会写入；启动时把还原矩形（按密度换算为 dp）直接作为窗口状态传入，首次启动居中显示，并清除最小化标记后前置。最大化状态下 DWM 圆角与 1dp 描边同时切为直角。

应用图标以 `:shared` 的矢量源 `shared/src/commonMain/composeResources/drawable/app_icon.svg` 为唯一来源：运行期由 `platform/AppIcon.kt` 按目标尺寸栅格化（窗口、托盘、标题栏共用），底色为 `#0067B8` 圆角方块；打包期由 `generateIconIco` 任务调用 `tool/IconTool.kt` 生成 `desktop/build/icon.ico` 作为启动器图标；托盘图标经 `SharedAppIcon.drawable` 交由原生托盘后端按平台尺寸栅格化。
