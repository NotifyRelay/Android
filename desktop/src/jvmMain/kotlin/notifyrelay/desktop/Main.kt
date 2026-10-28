package notifyrelay.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import notifyrelay.desktop.platform.DesktopNotificationActionPort
import notifyrelay.desktop.platform.ProvideDesktopUiPorts
import notifyrelay.shared.ui.pages.history.LocalNotificationActionPort
import notifyrelay.shared.ui.pages.history.NotificationHistoryScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 桌面端入口。 */
fun main() {
    // 数据层先于 UI 初始化：历史页依赖 Room 数据库
    DataLayerDesktop.initialize()

    application {
        Window(onCloseRequest = ::exitApplication, title = "NotifyRelay") {
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
