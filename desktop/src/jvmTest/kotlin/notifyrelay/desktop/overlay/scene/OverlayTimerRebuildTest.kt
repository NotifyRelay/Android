package notifyrelay.desktop.overlay.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.overlay.NotificationCardsHolder
import notifyrelay.desktop.overlay.NotificationEntry
import notifyrelay.desktop.overlay.SuperIslandCard
import notifyrelay.desktop.overlay.SuperIslandEntry
import notifyrelay.desktop.overlay.SuperIslandExpansionHolder
import notifyrelay.domain.feature.notification.superisland.replica.ReplicaTiming
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证录制器反复重建后，两类展开态计时仍按墙钟推进。
 *
 * 现场的高度随内容变化，publishFrame 因此频繁重建 OverlaySceneRecorder；
 * 计时若挂在组合树上，收起就永远不会发生。
 */
class OverlayTimerRebuildTest {
    @AfterTest
    fun cleanup() {
        DesktopOverlayStore.clearNotifications()
        DesktopOverlayStore.removeSuperIsland("k")
    }

    @Test
    fun notificationCollapsesDespiteRebuilds() {
        DesktopOverlayStore.addNotification(
            NotificationEntry(
                id = 11L,
                appName = "QQ",
                title = "标题",
                text = "正文",
                deviceName = "手机",
            ),
        )

        val rebuilds = runWithRebuilds { DesktopOverlayStore.notifications.size.toString() }
        val state = NotificationCardsHolder.state
        println("TIMER rebuilds=" + rebuilds + " notificationExpanded=" + state.expandedIds)
        assertTrue(state.expandedIds.isEmpty(), "重建不应阻止通知卡片收起")
    }

    @Test
    fun superIslandCollapsesDespiteRebuilds() {
        val entry =
            SuperIslandEntry(
                sourceKey = "k",
                deviceName = "手机",
                title = "标题",
                text = "正文",
                paramV2Raw = null,
                pics = null,
            )
        DesktopOverlayStore.putSuperIsland(entry)

        val rebuilds = runWithRebuilds { DesktopOverlayStore.superIslands["k"]?.let { SuperIslandCard(entry = it) } }

        // 用与渲染一致的内容标识查询：首次调用会登记计时
        val contentKey =
            entry.title.orEmpty() + "" + entry.text.orEmpty() + "" + entry.paramV2Raw.orEmpty()
        val state = SuperIslandExpansionHolder.stateFor("k")
        // 等过一个完整收起周期，确保计时确实推进
        Thread.sleep(ReplicaTiming.EXPANDED_DURATION_MS + 500)
        val stillExpanded = state.isExpanded(summaryOnly = false, contentKey = contentKey)
        println("TIMER rebuilds=" + rebuilds + " superIslandExpanded=" + stillExpanded)
        assertFalse(stillExpanded, "重建不应阻止超级岛收起")
    }

    /**
     * 按固定间隔反复重建录制器并推进渲染。
     *
     * 重建次数覆盖收起阈值的两倍时长，确保「计时被打断」会暴露出来。
     *
     * @return 实际重建次数
     */
    private fun runWithRebuilds(content: @androidx.compose.runtime.Composable () -> Unit): Int {
        fun recorder() =
            OverlaySceneRecorder(
                widthDp = 420,
                heightDp = 300,
                coroutineContext = Dispatchers.Default,
            ) {
                Box(Modifier.size(420.dp, 300.dp)) { content() }
            }

        val start = System.currentTimeMillis()
        var rebuilds = 0
        var r = recorder()
        while (System.currentTimeMillis() - start < 6_000) {
            r.render(System.nanoTime()) {}
            Thread.sleep(16)
            if (System.currentTimeMillis() - start > (rebuilds + 1) * 900L) {
                r.close()
                r = recorder()
                rebuilds++
            }
        }
        r.close()
        return rebuilds
    }
}
