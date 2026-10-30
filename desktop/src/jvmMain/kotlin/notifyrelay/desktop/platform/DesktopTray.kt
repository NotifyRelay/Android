package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import dev.nucleusframework.composenativetray.tray.api.Tray
import notifyrelay.shared.feature.appslist.SharedAppIcon
import javax.swing.SwingUtilities

/**
 * 托盘图标与右键菜单。
 *
 * 图标取自 :shared 的矢量资源，菜单由原生托盘后端绘制：
 * 左键单击显示主窗口，右键菜单提供「显示主窗口」与「退出」。
 *
 * 原生托盘回调来自系统消息循环线程，动作统一切回 Swing 事件线程执行。
 */
@Composable
internal fun DesktopTray(
    tooltip: String,
    onShowWindow: () -> Unit,
    onExit: () -> Unit,
) {
    Tray(
        icon = SharedAppIcon.drawable,
        tooltip = tooltip,
        primaryAction = { dispatch(onShowWindow) },
    ) {
        Item(label = "显示主窗口", onClick = { dispatch(onShowWindow) })
        Divider()
        Item(label = "退出", onClick = { dispatch(onExit) })
    }
}

/** 原生回调可能来自系统消息循环线程，统一投递到 Swing 事件线程。 */
private fun dispatch(action: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
}
