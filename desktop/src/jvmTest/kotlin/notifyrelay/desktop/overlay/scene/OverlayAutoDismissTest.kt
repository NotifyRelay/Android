package notifyrelay.desktop.overlay.scene

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.overlay.NOTIFICATION_COLLAPSE_MS
import notifyrelay.desktop.overlay.NOTIFICATION_ICON_LIFETIME_MS
import notifyrelay.desktop.overlay.NotificationCardsState
import notifyrelay.desktop.overlay.NotificationEntry
import notifyrelay.desktop.overlay.SuperIslandEntry
import notifyrelay.domain.feature.notification.superisland.replica.ReplicaTiming
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证通知卡片的收起与移除时序。
 *
 * 两条时限都由 [NotificationCardsState.tick] 推进；它们同时失效时，
 * 画面会停在展开态而不会自动消失。
 */
class OverlayAutoDismissTest {
    @AfterTest
    fun cleanup() {
        DesktopOverlayStore.clearNotifications()
    }

    @Test
    fun cardCollapsesAfterThreshold() {
        val entry = addOne()
        val state = NotificationCardsState()
        val t0 = 1_000_000L

        state.tick(t0)
        assertTrue(state.expandedIds.contains(entry.id), "新到通知应处于展开态")

        // 未到收起阈值
        state.tick(t0 + NOTIFICATION_COLLAPSE_MS - 1)
        assertTrue(state.expandedIds.contains(entry.id), "未到阈值不应收起")

        // 到点收起
        state.tick(t0 + NOTIFICATION_COLLAPSE_MS)
        assertTrue(state.expandedIds.isEmpty(), "到达收起阈值后应退出展开态")
        assertTrue(state.collapsedAt.containsKey(entry.id), "收起时刻应被登记")
    }

    @Test
    fun iconRemovedAfterLifetime() {
        val entry = addOne()
        val state = NotificationCardsState()
        val t0 = 2_000_000L

        state.tick(t0)
        state.tick(t0 + NOTIFICATION_COLLAPSE_MS)
        assertEquals(1, DesktopOverlayStore.notifications.size, "收起后仍以图标态存在")

        // 图标尚未到期
        state.tick(t0 + NOTIFICATION_COLLAPSE_MS + NOTIFICATION_ICON_LIFETIME_MS - 1)
        assertEquals(1, DesktopOverlayStore.notifications.size, "未到存活上限不应移除")

        // 到期移除
        state.tick(t0 + NOTIFICATION_COLLAPSE_MS + NOTIFICATION_ICON_LIFETIME_MS)
        assertTrue(DesktopOverlayStore.notifications.isEmpty(), "到达存活上限后应移除")
    }

    @Test
    fun superIslandPrunedWhenStale() {
        DesktopOverlayStore.removeSuperIsland("k")
        DesktopOverlayStore.putSuperIsland(
            SuperIslandEntry(
                sourceKey = "k",
                deviceName = "手机",
                title = "标题",
                text = "正文",
                paramV2Raw = null,
                pics = null,
                updatedAt = 10_000L,
            ),
        )
        val now = 10_000L + ReplicaTiming.NON_MEDIA_VIEW_TTL_MS + 1
        DesktopOverlayStore.pruneSuperIslands(now = now)
        assertTrue(DesktopOverlayStore.superIslands.isEmpty(), "过期超级岛应被移除")

        // 同一 sourceKey 被反复推送：updatedAt 每次刷新，因此永不超时
        DesktopOverlayStore.putSuperIsland(
            SuperIslandEntry(
                sourceKey = "k",
                deviceName = "手机",
                title = "标题",
                text = "正文",
                paramV2Raw = null,
                pics = null,
                updatedAt = now,
            ),
        )
        DesktopOverlayStore.pruneSuperIslands(now = now + 1)
        assertTrue(DesktopOverlayStore.superIslands.isNotEmpty(), "刚刷新过的条目不应被移除")
        DesktopOverlayStore.removeSuperIsland("k")
    }

    @Test
    fun repeatedSamePayloadDoesNotExtendLifetime() {
        DesktopOverlayStore.removeSuperIsland("rep")
        val t0 = 5_000_000L

        fun payload(at: Long) =
            SuperIslandEntry(
                sourceKey = "rep",
                deviceName = "手机",
                title = "标题",
                text = "正文",
                paramV2Raw = null,
                pics = null,
                updatedAt = at,
            )

        DesktopOverlayStore.putSuperIsland(payload(t0))
        // 对端周期性重发同一份内容：长度与字段完全一致
        DesktopOverlayStore.putSuperIsland(payload(t0 + 5_000))
        DesktopOverlayStore.putSuperIsland(payload(t0 + 10_000))

        val stored = DesktopOverlayStore.superIslands["rep"]
        assertEquals(t0, stored?.updatedAt, "内容未变时不应刷新更新时间")

        // 超时判定仍以首次到达时刻为准
        DesktopOverlayStore.pruneSuperIslands(now = t0 + ReplicaTiming.NON_MEDIA_VIEW_TTL_MS + 1)
        assertTrue(
            DesktopOverlayStore.superIslands.isEmpty(),
            "重复重发不应让条目免于超时回收",
        )
    }

    @Test
    fun changedPayloadRefreshesLifetime() {
        DesktopOverlayStore.removeSuperIsland("chg")
        val t0 = 6_000_000L
        DesktopOverlayStore.putSuperIsland(
            SuperIslandEntry(
                sourceKey = "chg",
                deviceName = "手机",
                title = "标题",
                text = "正文A",
                paramV2Raw = null,
                pics = null,
                updatedAt = t0,
            ),
        )
        DesktopOverlayStore.putSuperIsland(
            SuperIslandEntry(
                sourceKey = "chg",
                deviceName = "手机",
                title = "标题",
                text = "正文B",
                paramV2Raw = null,
                pics = null,
                updatedAt = t0 + 8_000,
            ),
        )

        val stored = DesktopOverlayStore.superIslands["chg"]
        assertEquals(t0 + 8_000, stored?.updatedAt, "内容变化时应刷新更新时间")
        DesktopOverlayStore.removeSuperIsland("chg")
    }

    @Test
    fun duplicateNotificationReplacesInsteadOfAppending() {
        DesktopOverlayStore.clearNotifications()
        val entry =
            NotificationEntry(
                id = 7L,
                appName = "微信",
                title = "标题",
                text = "正文",
                deviceName = "手机",
            )
        DesktopOverlayStore.addNotification(entry)
        DesktopOverlayStore.addNotification(entry)
        assertEquals(1, DesktopOverlayStore.notifications.size, "同一 id 不应重复入队")
    }

    private fun addOne(): NotificationEntry {
        val entry =
            NotificationEntry(
                id = 42L,
                appName = "微信",
                title = "标题",
                text = "正文",
                deviceName = "手机",
            )
        DesktopOverlayStore.addNotification(entry)
        return entry
    }
}
