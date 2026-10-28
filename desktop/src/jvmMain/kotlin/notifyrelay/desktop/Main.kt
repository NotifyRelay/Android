package notifyrelay.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.runBlocking
import notifyrelay.desktop.platform.DesktopNotificationActionPort
import notifyrelay.desktop.platform.ProvideDesktopUiPorts
import notifyrelay.desktop.platform.WindowIcon
import notifyrelay.shared.feature.appslist.SharedAppIcon
import notifyrelay.shared.ui.pages.history.LocalNotificationActionPort
import notifyrelay.shared.ui.pages.history.NotificationHistoryScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 桌面端入口。 */
fun main() {
    // Rust 核心自检：确认 JNA 能加载本机动态库
    println("[核心自检] " + CoreSelfCheck.run())

    // 数据层先于 UI 初始化：历史页依赖 Room 数据库
    DataLayerDesktop.initialize()

    // 窗口图标与 UI 共用 :shared 的同一份资源；窗口创建前需已就绪，故同步预加载
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
            // 用 AWT 多尺寸图标替换 Compose 的单张图标：Compose 会按屏幕密度缩放单张图，
            // 在 125%/150% 缩放下被放大而模糊；多尺寸由系统按 DPI 自选最合适的一档。
            LaunchedEffect(Unit) {
                window.setIconImages(iconImages)
            }

            val actionPort = remember { DesktopNotificationActionPort() }
            ProvideDesktopUiPorts {
                CompositionLocalProvider(LocalNotificationActionPort provides actionPort) {
                    MiuixTheme {
                        Column(modifier = Modifier.fillMaxSize()) {
                            NotificationHistoryScreen()
                        }
                    }
                }
            }
        }
    }
}
