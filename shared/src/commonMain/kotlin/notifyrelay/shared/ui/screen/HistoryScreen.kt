package notifyrelay.shared.ui.screen

import androidx.compose.runtime.Composable
import notifyrelay.shared.ui.navigation.Navigator

/**
 * 历史页面屏幕。
 *
 * 三个子页签由调用方注入；容器样式见 [TabbedScreen]。
 *
 * @param notificationHistory 通知历史子页。
 * @param superIslandHistory 超级岛历史子页。
 * @param chatTest 聊天测试子页。
 */
@Composable
fun HistoryScreen(
    navigator: Navigator,
    notificationHistory: @Composable () -> Unit,
    superIslandHistory: @Composable () -> Unit,
    chatTest: @Composable () -> Unit,
) {
    val tabTitles = listOf("通知历史", "超级岛历史", "聊天测试")
    TabbedScreen(tabTitles) { page ->
        when (page) {
            0 -> notificationHistory()
            1 -> superIslandHistory()
            2 -> chatTest()
        }
    }
}
