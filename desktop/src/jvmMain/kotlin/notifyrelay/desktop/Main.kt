package notifyrelay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode
import notifyrelay.desktop.device.DesktopDeviceManager
import notifyrelay.desktop.device.DesktopDevicePortsProvider
import notifyrelay.desktop.device.DesktopScrcpyScreen
import notifyrelay.desktop.platform.DesktopDeviceListPort
import notifyrelay.desktop.platform.DesktopNotificationActionPort
import notifyrelay.desktop.platform.DesktopThemePort
import notifyrelay.desktop.platform.DesktopToastHost
import notifyrelay.desktop.platform.DesktopWindowController
import notifyrelay.desktop.platform.DesktopWindowPlacement
import notifyrelay.desktop.platform.ProvideDesktopExtraPorts
import notifyrelay.desktop.platform.ProvideDesktopPairingDependencies
import notifyrelay.desktop.platform.ProvideDesktopUiPorts
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.ui.common.NotifyRelayTheme
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.navigation.rememberNavigator
import notifyrelay.shared.ui.pages.ClipboardSyncPage
import notifyrelay.shared.ui.pages.MusicControlPage
import notifyrelay.shared.ui.pages.ScrcpySettingsPage
import notifyrelay.shared.ui.pages.UISuperIslandSettings
import notifyrelay.shared.ui.pages.chat.UIChatTest
import notifyrelay.shared.ui.pages.history.LocalNotificationActionPort
import notifyrelay.shared.ui.pages.history.NotificationHistoryScreen
import notifyrelay.shared.ui.pages.history.SuperIslandHistoryScreen
import notifyrelay.shared.ui.port.ThemeBaseIndex
import notifyrelay.shared.ui.screen.DeveloperModeScreen
import notifyrelay.shared.ui.screen.DeviceForwardScreen
import notifyrelay.shared.ui.screen.HistoryScreen
import notifyrelay.shared.ui.screen.MainScreen
import notifyrelay.shared.ui.screen.SettingsAboutScreen
import notifyrelay.shared.ui.screen.SettingsAppearanceScreen
import notifyrelay.shared.ui.screen.SettingsDeveloperModeScreen
import notifyrelay.shared.ui.screen.SettingsLocalFilterScreen
import notifyrelay.shared.ui.screen.SettingsRemoteFilterScreen
import notifyrelay.shared.ui.screen.SettingsScreen
import notifyrelay.shared.ui.screen.SettingsSuperIslandScreen
import notifyrelay.shared.ui.screen.devicelist.DeviceListScreenContent
import notifyrelay.shared.ui.screen.devicelist.LocalDeviceListPort
import notifyrelay.shared.ui.viewmodel.LocalDevicePortsProvider
import notifyrelay.shared.ui.viewmodel.RemoteFilterViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/** 等待窗口尺寸就绪的上限。 */
private const val MAX_WAIT_WINDOW_SIZE_MS = 1200

/** 等待窗口尺寸就绪的轮询间隔。 */
private const val WINDOW_SIZE_POLL_MS = 16L

/** 首次启动时的默认窗口宽度。 */
private const val DEFAULT_WINDOW_WIDTH = 900

/** 首次启动时的默认窗口高度。 */
private const val DEFAULT_WINDOW_HEIGHT = 640

/**
 * 补齐可分发镜像的原生库搜索路径。
 *
 * 应用镜像把 Rust 动态库放在 resources 目录，`compose.application.resources.dir`
 * 在开发运行与镜像运行时都指向该目录；已显式配置库路径时不再覆盖。
 */
private fun applyDistributionLibraryPath() {
    val resourcesDir = System.getProperty("compose.application.resources.dir").orEmpty()
    if (resourcesDir.isEmpty()) return
    val separator = java.io.File.pathSeparator
    for (key in listOf("jna.library.path", "java.library.path")) {
        val current = System.getProperty(key).orEmpty()
        if (current.split(separator).any { it == resourcesDir }) continue
        val merged = if (current.isEmpty()) resourcesDir else current + separator + resourcesDir
        System.setProperty(key, merged)
    }
}

/** 桌面端入口。 */
fun main() {
    applyDistributionLibraryPath()

    // 应用标识：未用 Nucleus 插件打包时由系统属性提供，供系统媒体中心与自启解析 AUMID
    System.setProperty("nucleus.app.id", "notifyrelay.desktop")
    System.setProperty("nucleus.app.name", "NotifyRelay")
    System.setProperty("nucleus.app.aumid", "NotifyRelay.Desktop")

    // 单实例：已有实例在运行时请求其显示窗口并退出，避免多进程共用同一份数据与端口
    if (!notifyrelay.desktop.platform.DesktopSingleInstance
            .acquire()
    ) {
        notifyrelay.base.log.Logger
            .i("DesktopSingle", "已有实例在运行，已请求其显示窗口")
        return
    }

    // 开机自启后端在后台预加载（首次加载会 dlopen 原生库，避免阻塞界面）
    Thread({
        notifyrelay.desktop.platform.DesktopAutoLaunch
            .preload()
    }, "desktop-autolaunch")
        .apply {
            isDaemon = true
        }.start()

    // 数据层先初始化：偏好、历史等公共端 UI 均经 Storage 与端口读取
    DataLayerDesktop.initialize()

    // Rust 核心自检：确认 JNA 能加载本机动态库
    println("[核心自检] " + CoreSelfCheck.run())

    // 设备管理先于 UI 创建：列表页依赖 core 的设备快照
    val deviceManager = DesktopDeviceManager()
    deviceManager.start()

    // 通知历史、应用列表与图标的请求链路均取该管理器（与 Android 的 scanDeviceList 同口径）
    DataLayerDesktop.registerDeviceManager(deviceManager)

    // 窗口图标由 :shared 的矢量图标即时栅格化（与 Android 共用同一份资源）
    val iconImages =
        runCatching {
            notifyrelay.desktop.platform.AppIcon
                .images()
        }.getOrDefault(emptyList())

    application {
        // 主窗口状态：上次的非最大化矩形与最大化标记，启动时还原、退出时保存
        val savedBounds = remember { DesktopWindowPlacement.loadBounds() }
        val savedMaximized = remember { DesktopWindowPlacement.loadMaximized() }
        val mainWindow = remember { mutableStateOf<java.awt.Window?>(null) }
        val mainMaximized = remember { mutableStateOf(savedMaximized) }

        // 窗口与托盘：关闭按钮隐藏窗口，托盘菜单负责显隐与退出
        val windowController =
            remember {
                DesktopWindowController {
                    // 退出前落盘窗口状态：最大化时保留最大化标记与还原尺寸
                    mainWindow.value?.let { window ->
                        if (!mainMaximized.value) {
                            DesktopWindowPlacement.saveBounds(window.bounds)
                        }
                    }
                    DesktopWindowPlacement.saveMaximized(mainMaximized.value)
                    deviceManager.stopPowerMonitor()
                    DataLayerDesktop.clipboardSync?.stop()
                    DataLayerDesktop.mediaControl?.stop()
                    DataLayerDesktop.localNotificationRelay?.stop()
                    notifyrelay.desktop.platform.DesktopSystemMediaControl
                        .stop()
                    exitApplication()
                }
            }

        // 托盘：原生图标与原生右键菜单，左键单击显示主窗口
        notifyrelay.desktop.platform.DesktopTray(
            tooltip = "NotifyRelay",
            onShowWindow = { windowController.showWindow() },
            onExit = { windowController.exit() },
        )

        // 重复启动时的窗口唤起：报文来自监听线程，需投递到事件线程
        LaunchedEffect(windowController) {
            notifyrelay.desktop.platform.DesktopSingleInstance.onActivate = {
                javax.swing.SwingUtilities.invokeLater { windowController.showWindow() }
            }
        }

        // 叠加层：无边框透明置顶窗口，媒体卡/超级岛/弹幕均由本机 Compose 绘制
        notifyrelay.desktop.overlay.DesktopOverlayWindow()

        // 主题：索引经 ThemePort 持久化，跟随系统时读取系统深浅色
        val themePort = DesktopThemePort
        val themeIndex = remember { mutableIntStateOf(themePort.currentIndex()) }

        DisposableEffect(themePort) {
            val listener: (Int) -> Unit = { themeIndex.intValue = it }
            themePort.addListener(listener)
            onDispose { themePort.removeListener(listener) }
        }

        val darkTheme =
            when (themeIndex.intValue) {
                ThemeBaseIndex.LIGHT -> false
                ThemeBaseIndex.DARK -> true
                else -> isSystemInDarkMode()
            }

        // 还原上次的窗口矩形（裁剪到屏幕内）；没有记录时首次启动居中
        val restoredBounds = remember { savedBounds?.let { DesktopWindowPlacement.clampToScreen(it) } }
        val windowDensity = LocalDensity.current
        val windowState =
            rememberWindowState(
                placement = WindowPlacement.Floating,
                position =
                    restoredBounds?.let {
                        with(windowDensity) { WindowPosition.Absolute(it.x.toDp(), it.y.toDp()) }
                    } ?: WindowPosition(Alignment.Center),
                width = restoredBounds?.let { with(windowDensity) { it.width.toDp() } } ?: DEFAULT_WINDOW_WIDTH.dp,
                height = restoredBounds?.let { with(windowDensity) { it.height.toDp() } } ?: DEFAULT_WINDOW_HEIGHT.dp,
            )

        Window(
            onCloseRequest = { windowController.onCloseRequest() },
            state = windowState,
            title = "NotifyRelay",
            // 标题栏由应用自绘（DesktopTitleBar），窗口只保留内容区
            undecorated = true,
        ) {
            LaunchedEffect(window) {
                windowController.bindWindow(window)
                mainWindow.value = window
                window.setIconImages(iconImages)
                notifyrelay.base.log.Logger.i(
                    "DesktopWindow",
                    "窗口状态: 矩形=" + restoredBounds + " 最大化=" + savedMaximized,
                )
                // 先把还原矩形落到窗口上：Compose 的窗口状态要晚一拍才生效，
                // 若此时以 0×0 显示，系统会把窗口当作最小化处理
                if (restoredBounds != null) {
                    window.bounds = restoredBounds
                } else {
                    window.setSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT)
                    window.setLocationRelativeTo(null)
                }
                if (savedMaximized) {
                    window.bounds =
                        java.awt.GraphicsEnvironment
                            .getLocalGraphicsEnvironment()
                            .maximumWindowBounds
                }
                // 无边框窗口：由 DWM 绘制圆角；最大化时窗口为直角
                notifyrelay.desktop.platform.applyWindowCorners(window, rounded = !savedMaximized)
                // 等窗口拿到非零尺寸后再显示，避免零尺寸窗口被系统当作最小化
                var waitedMs = 0
                while (window.width <= 0 || window.height <= 0) {
                    if (waitedMs >= MAX_WAIT_WINDOW_SIZE_MS) break
                    kotlinx.coroutines.delay(WINDOW_SIZE_POLL_MS)
                    waitedMs += WINDOW_SIZE_POLL_MS.toInt()
                }
                // 清除可能的最小化标记并显示、前置
                window.extendedState = window.extendedState and
                    java.awt.Frame.ICONIFIED
                        .inv()
                window.isVisible = true
                window.toFront()
                window.requestFocus()
                notifyrelay.base.log.Logger.i(
                    "DesktopWindow",
                    "窗口已显示: " + window.bounds + " 状态=" + window.extendedState,
                )
                // 剪贴板同步：默认开启；窗口就绪后才能取到 HWND 注册系统剪贴板事件
                DataLayerDesktop.clipboardSync?.start(windowController.nativeHandle())
                // 媒体会话读取：SMTC 桥只提供读取接口，按固定间隔取快照
                DataLayerDesktop.mediaControl?.start()
                // 本机通知采集：WinRT 桥轮询，新增通知经公共网关转发
                DataLayerDesktop.localNotificationRelay?.start()
            }

            // 窗口底色跟随主题：无边框窗口移动时若有背景擦除，避免闪出默认白底
            LaunchedEffect(window, darkTheme) {
                window.background =
                    if (darkTheme) {
                        java.awt.Color(0x1C, 0x1C, 0x1E)
                    } else {
                        java.awt.Color.WHITE
                    }
            }

            val actionPort = remember { DesktopNotificationActionPort() }
            val deviceListPort = remember { DesktopDeviceListPort() }
            val devicePortsProvider = remember { DesktopDevicePortsProvider(deviceManager) }
            val navigator = rememberNavigator(Route.Main)
            // 桌面端无 Activity，需自行提供 ViewModelStoreOwner 供 viewModel() 使用
            val viewModelStoreOwner =
                remember {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }

            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                ProvideDesktopUiPorts {
                    ProvideDesktopExtraPorts(onFinish = { windowController.hideWindow() }) {
                        // 配对依赖：配对对话框与设备列表共用同一个握手等待器实例
                        ProvideDesktopPairingDependencies(deviceManager.handshakeWaiters) {
                            CompositionLocalProvider(
                                LocalNotificationActionPort provides actionPort,
                                LocalDeviceListPort provides deviceListPort,
                                LocalDevicePortsProvider provides devicePortsProvider,
                                LocalNavigator provides navigator,
                            ) {
                                DesktopRoot(
                                    navigator = navigator,
                                    darkTheme = darkTheme,
                                    devicePortsProvider = devicePortsProvider,
                                    window = window,
                                    titleIcon = iconImages.minByOrNull { abs(it.getWidth(null) - 32) },
                                    title = "NotifyRelay",
                                    initiallyMaximized = savedMaximized,
                                    initialRestoreBounds = savedBounds,
                                    onMaximizedChanged = { maximized, normalBounds ->
                                        mainMaximized.value = maximized
                                        DesktopWindowPlacement.saveMaximized(maximized)
                                        DesktopWindowPlacement.saveBounds(normalBounds)
                                    },
                                    onGeometrySettled = { bounds ->
                                        if (!mainMaximized.value) {
                                            DesktopWindowPlacement.saveBounds(bounds)
                                        }
                                    },
                                    onMinimize = {
                                        window.extendedState = window.extendedState or java.awt.Frame.ICONIFIED
                                    },
                                    onClose = { windowController.onCloseRequest() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 桌面端导航根：复用公共端 [MainScreen] 与各子页。
 *
 * 屏幕镜像（scrcpy）与超级岛在桌面端不接线，对应槽位给出空内容。
 */
@Composable
private fun DesktopRoot(
    navigator: Navigator,
    darkTheme: Boolean,
    devicePortsProvider: DesktopDevicePortsProvider,
    window: java.awt.Frame,
    titleIcon: java.awt.Image?,
    title: String,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    initiallyMaximized: Boolean,
    initialRestoreBounds: java.awt.Rectangle?,
    onMaximizedChanged: (Boolean, java.awt.Rectangle) -> Unit,
    onGeometrySettled: (java.awt.Rectangle) -> Unit,
) {
    NotifyRelayTheme(darkTheme = darkTheme) {
        // 全局提示承载：桌面端 Toast 经此 Snackbar 展示（任意线程入队）
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(snackbarHostState) {
            DesktopToastHost.messages.collect { message ->
                snackbarHostState.showSnackbar(
                    message = message.text,
                    duration =
                        when (message.duration) {
                            // Miuix 的 Short 约 4s、Long 约 10s，对桌面端偏长，改用自定义时长
                            DesktopToastHost.Duration.SHORT -> SnackbarDuration.Custom(DesktopToastHost.SHORT_MILLIS)
                            DesktopToastHost.Duration.LONG -> SnackbarDuration.Custom(DesktopToastHost.LONG_MILLIS)
                        },
                )
            }
        }

        notifyrelay.desktop.platform.DesktopWindowFrame(
            window = window,
            title = title,
            icon = titleIcon,
            onMinimize = onMinimize,
            onClose = onClose,
            initiallyMaximized = initiallyMaximized,
            initialRestoreBounds = initialRestoreBounds,
            onMaximizedChanged = onMaximizedChanged,
            onGeometrySettled = onGeometrySettled,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MiuixTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(MiuixTheme.colorScheme.background),
                ) {
                    NavDisplay(
                        backStack = navigator.backStack,
                        onBack = {
                            if (navigator.backStackSize() > 1) {
                                navigator.pop()
                            }
                        },
                    ) {
                        entry<Route.Main> {
                            MainScreen(
                                navigator = navigator,
                                deviceListContent = { args ->
                                    DeviceListScreenContent(
                                        isLandscape = args.isLandscape,
                                        discoveryEnabled = args.discoveryEnabled,
                                        onDiscoveryChange = args.onDiscoveryChange,
                                        allDevices = args.allDevices,
                                        authedDeviceUuids = args.authedDeviceUuids,
                                        deviceStates = args.deviceStates,
                                        unauthedDevices = args.unauthedDevices,
                                        selectedDevice = args.selectedDevice,
                                        onSelectDevice = args.onSelectDevice,
                                        onRequestDelete = args.onRequestDelete,
                                        onShowRejectedDialog = args.onShowRejectedDialog,
                                        localBatteryLevel = args.localBatteryLevel,
                                        colorScheme = MiuixTheme.colorScheme,
                                        textStyles = MiuixTheme.textStyles,
                                    )
                                },
                                historyContent = {
                                    HistoryScreen(
                                        navigator,
                                        notificationHistory = { NotificationHistoryScreen() },
                                        superIslandHistory = { SuperIslandHistoryScreen() },
                                        chatTest = {
                                            UIChatTest(
                                                deviceManager = devicePortsProviderChatPort(devicePortsProvider),
                                                onSendMessage = { input -> sendChatMessage(devicePortsProvider, input) },
                                            )
                                        },
                                    )
                                },
                                forwardContent = {
                                    DeviceForwardScreen(
                                        filterViewModel = remoteFilterViewModel(),
                                        clipboardSync = { ClipboardSyncPage() },
                                        musicControl = { MusicControlPage() },
                                        scrcpyScreen = { deviceInfo, _ ->
                                            DesktopScrcpyScreen(devicePortsProvider, deviceInfo)
                                        },
                                    )
                                },
                                settingsContent = { SettingsScreen() },
                            )
                        }
                        // 与 MainScreen 页签同源：单独 push 这两个路由时复用同一内容，避免未注册 entry 报错
                        entry<Route.History> {
                            HistoryScreen(
                                navigator,
                                notificationHistory = { NotificationHistoryScreen() },
                                superIslandHistory = { SuperIslandHistoryScreen() },
                                chatTest = {
                                    UIChatTest(
                                        deviceManager = devicePortsProviderChatPort(devicePortsProvider),
                                        onSendMessage = { input -> sendChatMessage(devicePortsProvider, input) },
                                    )
                                },
                            )
                        }
                        entry<Route.Settings> { SettingsScreen() }
                        entry<Route.SettingsRemoteFilter> { SettingsRemoteFilterScreen() }
                        entry<Route.SettingsLocalFilter> { SettingsLocalFilterScreen() }
                        entry<Route.SettingsSuperIsland> { SettingsSuperIslandScreen { UISuperIslandSettings() } }
                        entry<Route.SettingsDeveloperMode> { SettingsDeveloperModeScreen { DeveloperModeScreen() } }
                        entry<Route.SettingsAbout> { SettingsAboutScreen {} }
                        entry<Route.SettingsAppearance> { SettingsAppearanceScreen() }
                        entry<Route.SettingsScrcpy> { ScrcpySettingsPage() }
                        // 高级设置与基础设置同页：桌面端参数集较移动端少，不再分组
                        entry<Route.ScrcpyAdvanced> { ScrcpySettingsPage() }
                        // 虚拟按钮排序在桌面端不接线（无触摸注入语义）
                        entry<Route.ScrcpyVirtualButtonOrder> { DesktopUnavailableScreen("虚拟按钮排序") { navigator.pop() } }
                    }
                }
            }
        }
    }
}

/** 构造桌面端聊天端口。 */
private fun devicePortsProviderChatPort(provider: DesktopDevicePortsProvider): notifyrelay.domain.feature.device.service.contract.DeviceChatPort = provider.chat()

/** 发送聊天测试消息：报文构造与入队由公共网关承担。 */
private fun sendChatMessage(
    provider: DesktopDevicePortsProvider,
    message: String,
) {
    com.xzyht.notifyrelay.nativecore.device.appsync.ChatSendGateway.sendChatMessage(
        localPackageName = "com.xzyht.notifyrelay.desktop",
        message = message,
        deviceManager = provider.message(),
    )
}

/** 以公共端端口注册表构造远端过滤 ViewModel。 */
@Composable
private fun remoteFilterViewModel(): RemoteFilterViewModel =
    viewModel(
        factory =
            factoryOf {
                RemoteFilterViewModel(
                    RemoteFilterPortRegistry.current ?: error("RemoteFilterPort 未注册"),
                    LocalAppsPortRegistry.current ?: error("LocalAppsPort 未注册"),
                    AppIconPortRegistry.current ?: error("AppIconPort 未注册"),
                )
            },
    )

/** 由无参构造器函数构造 ViewModel 的工厂。 */
private inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: kotlin.reflect.KClass<T>,
            extras: CreationExtras,
        ): T = create() as T
    }

/** 桌面端未接线功能的占位页。 */
@Composable
private fun DesktopUnavailableScreen(
    title: String,
    onBack: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        top.yukonga.miuix.kmp.basic
            .Text(text = title, color = MiuixTheme.colorScheme.onBackground)
        top.yukonga.miuix.kmp.basic.Text(
            text = "桌面端暂未提供该功能",
            color = MiuixTheme.colorScheme.onBackground,
        )
        top.yukonga.miuix.kmp.basic
            .TextButton(text = "返回", onClick = onBack)
    }
}
