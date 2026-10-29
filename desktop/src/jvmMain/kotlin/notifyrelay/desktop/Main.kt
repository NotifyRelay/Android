package notifyrelay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
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
import kotlinx.coroutines.runBlocking
import notifyrelay.desktop.device.DesktopDeviceManager
import notifyrelay.desktop.device.DesktopDevicePortsProvider
import notifyrelay.desktop.platform.DesktopDeviceListPort
import notifyrelay.desktop.platform.DesktopNotificationActionPort
import notifyrelay.desktop.platform.DesktopThemePort
import notifyrelay.desktop.platform.DesktopToastHost
import notifyrelay.desktop.platform.DesktopWindowController
import notifyrelay.desktop.platform.ProvideDesktopExtraPorts
import notifyrelay.desktop.platform.ProvideDesktopPairingDependencies
import notifyrelay.desktop.platform.ProvideDesktopUiPorts
import notifyrelay.desktop.platform.WindowIcon
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.feature.appslist.SharedAppIcon
import notifyrelay.shared.ui.common.NotifyRelayTheme
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.navigation.rememberNavigator
import notifyrelay.shared.ui.pages.ClipboardSyncPage
import notifyrelay.shared.ui.pages.MusicControlPage
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
import notifyrelay.shared.ui.screen.SettingsLocalFilterScreen
import notifyrelay.shared.ui.screen.SettingsRemoteFilterScreen
import notifyrelay.shared.ui.screen.SettingsScreen
import notifyrelay.shared.ui.screen.SettingsSuperIslandScreen
import notifyrelay.shared.ui.screen.devicelist.DeviceListScreenContent
import notifyrelay.shared.ui.screen.devicelist.LocalDeviceListPort
import notifyrelay.shared.ui.viewmodel.LocalDevicePortsProvider
import notifyrelay.shared.ui.viewmodel.RemoteFilterViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.awt.AWTEvent
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.math.abs

private const val TRAY_ICON_SIZE = 32

/** 桌面端入口。 */
fun main() {
    // 数据层先初始化：偏好、历史等公共端 UI 均经 Storage 与端口读取
    DataLayerDesktop.initialize()

    // Rust 核心自检：确认 JNA 能加载本机动态库
    println("[核心自检] " + CoreSelfCheck.run())

    // 设备管理先于 UI 创建：列表页依赖 core 的设备快照
    val deviceManager = DesktopDeviceManager()
    deviceManager.start()

    // 通知历史、应用列表与图标的请求链路均取该管理器（与 Android 的 scanDeviceList 同口径）
    DataLayerDesktop.registerDeviceManager(deviceManager)

    // 窗口图标与 UI 共用 :shared 的同一份资源
    val iconImages =
        runCatching {
            val bytes = runBlocking { SharedAppIcon.preload() } ?: return@runCatching emptyList()
            WindowIcon.buildMultiSize(bytes)
        }.getOrDefault(emptyList())

    application {
        // 窗口与托盘：关闭按钮隐藏窗口，托盘菜单负责显隐与退出
        val windowController =
            remember {
                DesktopWindowController {
                    deviceManager.stopPowerMonitor()
                    DataLayerDesktop.clipboardSync?.stop()
                    exitApplication()
                }
            }

        LaunchedEffect(Unit) {
            val trayImage = iconImages.minByOrNull { abs(it.getWidth(null) - TRAY_ICON_SIZE) }
            windowController.installTray(trayImage, "NotifyRelay")
        }

        DisposableEffect(Unit) {
            onDispose { windowController.disposeTray() }
        }

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
                else -> isSystemInDarkTheme()
            }

        // 托盘右键菜单：Compose 弹层，位置取右键时的屏幕坐标
        val trayMenuPoint = remember { mutableStateOf<Point?>(null) }

        LaunchedEffect(windowController) {
            windowController.onMenuRequested = { point -> trayMenuPoint.value = point }
        }

        Window(
            onCloseRequest = { windowController.onCloseRequest() },
            title = "NotifyRelay",
        ) {
            LaunchedEffect(window) {
                windowController.bindWindow(window)
                window.setIconImages(iconImages)
                // 剪贴板同步：默认开启；窗口就绪后才能取到 HWND 注册系统剪贴板事件
                DataLayerDesktop.clipboardSync?.start(windowController.nativeHandle())
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
                                DesktopRoot(navigator, darkTheme, devicePortsProvider)
                            }
                        }
                    }
                }
            }
        }

        trayMenuPoint.value?.let { point ->
            TrayMenu(
                point = point,
                darkTheme = darkTheme,
                onDismiss = { trayMenuPoint.value = null },
                onToggleWindow = {
                    trayMenuPoint.value = null
                    windowController.toggleWindow()
                },
                onExit = {
                    trayMenuPoint.value = null
                    windowController.exit()
                },
            )
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
                                    scrcpyScreen = { _, _ -> },
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
                    entry<Route.SettingsDeveloperMode> { DeveloperModeScreen() }
                    entry<Route.SettingsAbout> { SettingsAboutScreen {} }
                    entry<Route.SettingsAppearance> { SettingsAppearanceScreen() }
                    // 屏幕镜像相关路由在桌面端不接线，给出占位页避免导航失败
                    entry<Route.SettingsScrcpy> { DesktopUnavailableScreen("屏幕镜像") { navigator.pop() } }
                    entry<Route.ScrcpyAdvanced> { DesktopUnavailableScreen("屏幕镜像高级设置") { navigator.pop() } }
                    entry<Route.ScrcpyVirtualButtonOrder> { DesktopUnavailableScreen("虚拟按钮排序") { navigator.pop() } }
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

/** 托盘右键菜单：Miuix 样式的 Compose 弹层。 */
@Composable
private fun TrayMenu(
    point: Point,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
    onToggleWindow: () -> Unit,
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val menuWidth = 168.dp
    val menuHeight = 84.dp
    val position =
        remember(point, density) {
            val widthPx = with(density) { menuWidth.roundToPx() }
            val heightPx = with(density) { menuHeight.roundToPx() }
            val screen = Toolkit.getDefaultToolkit().screenSize
            val x = point.x.coerceIn(0, (screen.width - widthPx).coerceAtLeast(0))
            val y = point.y.coerceIn(0, (screen.height - heightPx).coerceAtLeast(0))
            with(density) { WindowPosition.Absolute(x.toDp(), y.toDp()) }
        }
    val windowState =
        rememberWindowState(
            position = position,
            size = DpSize(menuWidth, menuHeight),
        )
    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = "",
        resizable = false,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        // 失焦或点击菜单外部时关闭
        DisposableEffect(window) {
            var hadFocus = false
            val focusListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(event: WindowEvent) {
                        hadFocus = true
                    }

                    override fun windowLostFocus(event: WindowEvent) {
                        if (hadFocus) onDismiss()
                    }
                }
            window.addWindowFocusListener(focusListener)
            val globalClick =
                AWTEventListener { event ->
                    val mouse = event as? MouseEvent ?: return@AWTEventListener
                    if (mouse.id == MouseEvent.MOUSE_PRESSED && !window.containsComponent(mouse.component)) {
                        onDismiss()
                    }
                }
            Toolkit.getDefaultToolkit().addAWTEventListener(globalClick, AWTEvent.MOUSE_EVENT_MASK)
            runCatching { window.type = java.awt.Window.Type.UTILITY }
            window.requestFocus()
            onDispose {
                window.removeWindowFocusListener(focusListener)
                Toolkit.getDefaultToolkit().removeAWTEventListener(globalClick)
            }
        }
        NotifyRelayTheme(darkTheme = darkTheme) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                cornerRadius = 10.dp,
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
            ) {
                Column {
                    TrayMenuItem(text = "显示或隐藏窗口", onClick = onToggleWindow)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    TrayMenuItem(text = "退出", onClick = onExit)
                }
            }
        }
    }
}

/** 托盘菜单项。 */
@Composable
private fun TrayMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, color = MiuixTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

/** 判断组件是否属于该窗口。 */
private fun java.awt.Window.containsComponent(component: java.awt.Component): Boolean {
    var current: java.awt.Component? = component
    while (current != null) {
        if (current === this) return true
        current = current.parent
    }
    return false
}
