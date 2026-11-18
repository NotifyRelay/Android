package notifyrelay.domain.feature.notification.superisland.replica

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证两端共用的存活判定契约。
 *
 * 判据只看内容变化时刻：来源会周期性重发同一份内容，
 * 若把重发也算作更新，条目永远不会过期。
 */
class ReplicaExpiryTest {
    @Test
    fun nonMediaExpiresAtThreshold() {
        val t0 = 1_000_000L
        val ttl = ReplicaTiming.NON_MEDIA_VIEW_TTL_MS

        assertFalse(ReplicaExpiry.isExpired(t0, isMedia = false, nowMs = t0 + ttl - 1))
        assertFalse(ReplicaExpiry.isExpired(t0, isMedia = false, nowMs = t0 + ttl))
        assertTrue(ReplicaExpiry.isExpired(t0, isMedia = false, nowMs = t0 + ttl + 1))
    }

    @Test
    fun mediaOutlivesRefreshInterval() {
        // 媒体时限必须长于来源刷新周期，否则每轮刷新前先撤回一次
        assertTrue(
            ReplicaTiming.MEDIA_VIEW_TTL_MS > ReplicaTiming.SOURCE_REFRESH_INTERVAL_MS,
            "媒体时限须长于刷新周期",
        )
        assertTrue(
            ReplicaTiming.CLOSED_SOURCE_TTL_MS >= ReplicaTiming.SOURCE_REFRESH_INTERVAL_MS,
            "关闭屏蔽窗口须覆盖刷新周期",
        )
    }

    @Test
    fun viewTtlPicksByBusiness() {
        assertEquals(ReplicaTiming.MEDIA_VIEW_TTL_MS, ReplicaTiming.viewTtlMs(isMedia = true))
        assertEquals(ReplicaTiming.NON_MEDIA_VIEW_TTL_MS, ReplicaTiming.viewTtlMs(isMedia = false))
    }

    @Test
    fun remainingClampsToZero() {
        val t0 = 2_000_000L
        val ttl = ReplicaTiming.NON_MEDIA_VIEW_TTL_MS

        assertEquals(ttl, ReplicaExpiry.remainingMs(t0, isMedia = false, nowMs = t0))
        assertEquals(1L, ReplicaExpiry.remainingMs(t0, isMedia = false, nowMs = t0 + ttl - 1))
        assertEquals(0L, ReplicaExpiry.remainingMs(t0, isMedia = false, nowMs = t0 + ttl + 500))
    }
}
