package notifyrelay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import notifyrelay.desktop.device.DesktopDeviceManager
import notifyrelay.desktop.device.DesktopDevicePortsProvider
import notifyrelay.desktop.device.DesktopDeviceScreen
import notifyrelay.desktop.platform.DesktopDeviceListPort
import notifyrelay.desktop.platform.DesktopNotificationActionPort
import notifyrelay.desktop.platform.ProvideDesktopUiPorts
import notifyrelay.desktop.platform.WindowIcon
import notifyrelay.shared.feature.appslist.SharedAppIcon
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.navigation.rememberNavigator
import notifyrelay.shared.ui.pages.history.LocalNotificationActionPort
import notifyrelay.shared.ui.screen.devicelist.LocalDeviceListPort
import notifyrelay.shared.ui.viewmodel.LocalDevicePortsProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 桌面端入口。 */
fun main() {
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
        Window(
            onCloseRequest = ::exitApplication,
            title = "NotifyRelay",
        ) {
            LaunchedEffect(Unit) {
                window.setIconImages(iconImages)
            }

            val actionPort = remember { DesktopNotificationActionPort() }
            val deviceListPort = remember { DesktopDeviceListPort() }
            val devicePortsProvider = remember { DesktopDevicePortsProvider(deviceManager) }
            val navigator = rememberNavigator(Route.Main)

            ProvideDesktopUiPorts {
                CompositionLocalProvider(
                    LocalNotificationActionPort provides actionPort,
                    LocalDeviceListPort provides deviceListPort,
                    LocalDevicePortsProvider provides devicePortsProvider,
                    LocalNavigator provides navigator,
                ) {
                    MiuixTheme {
                        Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                            DesktopDeviceScreen(navigator = navigator)
                        }
                    }
                }
            }
        }
    }
}
