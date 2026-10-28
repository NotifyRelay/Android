package notifyrelay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
import notifyrelay.desktop.platform.DesktopWindowController
import notifyrelay.desktop.platform.ProvideDesktopExtraPorts
import notifyrelay.desktop.platform.ProvideDesktopUiPorts
import notifyrelay.desktop.platform.WindowIcon
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.feature.appslist.SharedAppIcon
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.navigation.rememberNavigator
import notifyrelay.shared.ui.pages.MusicControlPage
import notifyrelay.shared.ui.pages.history.LocalNotificationActionPort
import notifyrelay.shared.ui.pages.history.NotificationHistoryScreen
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
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    // 窗口图标与 UI 共用 :shared 的同一份资源
    val iconImages =
        runCatching {
            val bytes = runBlocking { SharedAppIcon.preload() } ?: return@runCatching emptyList()
            WindowIcon.buildMultiSize(bytes)
        }.getOrDefault(emptyList())

    application {
        // 窗口与托盘：关闭按钮隐藏窗口，托盘菜单负责显隐与退出
        val windowController = remember { DesktopWindowController { exitApplication() } }

        LaunchedEffect(Unit) {
            val trayImage = iconImages.minByOrNull { abs(it.getWidth(null) - TRAY_ICON_SIZE) }
            windowController.installTray(trayImage, "NotifyRelay")
        }

        DisposableEffect(Unit) {
            onDispose { windowController.disposeTray() }
        }

        Window(
            onCloseRequest = { windowController.onCloseRequest() },
            title = "NotifyRelay",
        ) {
            LaunchedEffect(window) {
                windowController.bindWindow(window)
                window.setIconImages(iconImages)
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
                        CompositionLocalProvider(
                            LocalNotificationActionPort provides actionPort,
                            LocalDeviceListPort provides deviceListPort,
                            LocalDevicePortsProvider provides devicePortsProvider,
                            LocalNavigator provides navigator,
                        ) {
                            DesktopRoot(navigator)
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
private fun DesktopRoot(navigator: Navigator) {
    MiuixTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
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
                                superIslandHistory = {},
                                chatTest = {},
                            )
                        },
                        forwardContent = {
                            DeviceForwardScreen(
                                filterViewModel = remoteFilterViewModel(),
                                clipboardSync = {},
                                musicControl = { MusicControlPage() },
                                scrcpyScreen = { _, _ -> },
                            )
                        },
                        settingsContent = { SettingsScreen() },
                    )
                }
                entry<Route.SettingsRemoteFilter> { SettingsRemoteFilterScreen() }
                entry<Route.SettingsLocalFilter> { SettingsLocalFilterScreen() }
                entry<Route.SettingsSuperIsland> { SettingsSuperIslandScreen {} }
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
    top.yukonga.miuix.kmp.theme.MiuixTheme {
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
}
