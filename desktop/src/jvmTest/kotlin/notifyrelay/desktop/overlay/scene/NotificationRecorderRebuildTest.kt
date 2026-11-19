package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.overlay.NotificationCardsState
import notifyrelay.desktop.overlay.NotificationEntry
import notifyrelay.desktop.overlay.rememberNotificationCardsState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 验证「录制器重建」会让卡片计时状态归零。
 *
 * 现场的高度会随内容变化（通知到达、收起、媒体卡展开），
 * publishFrame 在尺寸变化时重建 recorder，计时随之从头开始。
 */
class NotificationRecorderRebuildTest {
    @AfterTest
    fun cleanup() {
        DesktopOverlayStore.clearNotifications()
    }

    @Test
    fun rebuildResetsExpandedTiming() {
        DesktopOverlayStore.addNotification(
            NotificationEntry(
                id = 5L,
                appName = "QQ",
                title = "标题",
                text = "正文",
                deviceName = "手机",
            ),
        )

        var latest: NotificationCardsState? = null

        fun recorder() =
            OverlaySceneRecorder(
                widthDp = 420,
                heightDp = 300,
                coroutineContext = Dispatchers.Default,
            ) {
                Box(Modifier.size(420.dp, 300.dp)) {
                    latest = rememberNotificationCardsState()
                }
            }

        // 每 1 秒重建一次 recorder，总计 6 秒：超过 3 秒收起阈值 2 倍
        val start = System.currentTimeMillis()
        var r = recorder()
        var rebuilds = 0
        while (System.currentTimeMillis() - start < 6_000) {
            r.render(System.nanoTime()) {}
            Thread.sleep(16)
            if (System.currentTimeMillis() - start > (rebuilds + 1) * 1_000L) {
                r.close()
                r = recorder()
                rebuilds++
            }
        }
        val s = latest
        r.close()

        val at = s?.expandedAt?.get(5L)
        println(
            "REBUILD rebuilds=" + rebuilds +
                " expanded=" + s?.expandedIds +
                " expandedAt=" + at +
                " now=" + System.currentTimeMillis() +
                " elapsedSinceExpandMs=" + (at?.let { System.currentTimeMillis() - it } ?: -1),
        )
        assertTrue(
            s?.expandedIds?.isEmpty() == true,
            "若每次重建都归零，6 秒后仍停在展开态",
        )
    }
}
