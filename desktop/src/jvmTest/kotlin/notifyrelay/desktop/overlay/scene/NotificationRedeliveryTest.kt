package notifyrelay.desktop.overlay.scene

import notifyrelay.desktop.overlay.DesktopOverlayStore
import notifyrelay.desktop.overlay.NotificationCardsState
import notifyrelay.desktop.overlay.NotificationEntry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 复现现场：同一条通知被反复投递时，卡片是否仍会收起并消失。
 */
class NotificationRedeliveryTest {
    @AfterTest
    fun cleanup() {
        DesktopOverlayStore.clearNotifications()
    }

    private fun entry() =
        NotificationEntry(
            id = 99L,
            appName = "QQ",
            title = "标题",
            text = "正文",
            deviceName = "手机",
        )

    @Test
    fun repeatedDeliveryStillExpires() {
        val state = NotificationCardsState()
        var now = 1_000_000L
        var maxAgeMs = 0L
        val firstSeen = now
        var removed = false

        // 模拟对端每 2 秒重投一次同一通知，持续 40 秒
        var lastDelivery = -10_000L
        while (now - firstSeen < 40_000) {
            if (now - lastDelivery >= 2_000) {
                DesktopOverlayStore.addNotification(entry())
                lastDelivery = now
            }
            state.tick(now)
            if (DesktopOverlayStore.notifications.isEmpty()) {
                removed = true
                maxAgeMs = now - firstSeen
                break
            }
            now += 200
        }

        println("REDELIVERY removed=" + removed + " atMs=" + maxAgeMs)
        assertTrue(removed, "重复投递不应让通知永不消失")
    }
}
