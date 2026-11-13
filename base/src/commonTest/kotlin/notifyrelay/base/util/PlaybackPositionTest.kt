package notifyrelay.base.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** 位置推算：快照只在状态变更时刷新，播放中需按经过时间推进。 */
class PlaybackPositionTest {
    @Test
    fun predictAdvancesWhilePlaying() {
        // 快照 5 秒、采样在 30 秒前、常速：此刻应为 35 秒
        assertEquals(35_000L, PlaybackPosition.predict(5_000L, 1_000L, true, 1f, 31_000L))
    }

    @Test
    fun predictKeepsSnapshotWhilePaused() {
        assertEquals(5_000L, PlaybackPosition.predict(5_000L, 1_000L, false, 1f, 31_000L))
    }

    @Test
    fun predictHonoursSpeed() {
        assertEquals(45_000L, PlaybackPosition.predict(5_000L, 1_000L, true, 2f, 21_000L))
    }

    @Test
    fun predictRejectsUnknownPosition() {
        assertEquals(-1L, PlaybackPosition.predict(-1L, 1_000L, true, 1f, 31_000L))
    }

    @Test
    fun trackerExtrapolatesBetweenPolls() {
        val tracker = PlaybackPositionTracker()
        assertEquals(10_000L, tracker.estimate(10_000L, true, 1_000L))
        // 读数未变：按经过时间推进，不回到旧值
        assertEquals(16_000L, tracker.estimate(10_000L, true, 7_000L))
    }

    @Test
    fun trackerRebasesWhenReadingChanges() {
        val tracker = PlaybackPositionTracker()
        tracker.estimate(10_000L, true, 1_000L)
        // 读数跳到 60 秒：视为跳转，从新读数重新计时
        assertEquals(60_000L, tracker.estimate(60_000L, true, 7_000L))
        assertEquals(63_000L, tracker.estimate(60_000L, true, 10_000L))
    }

    @Test
    fun trackerStopsWhilePaused() {
        val tracker = PlaybackPositionTracker()
        tracker.estimate(10_000L, true, 1_000L)
        // 暂停：钉在当次读数，不再随时钟推进
        assertEquals(20_000L, tracker.estimate(20_000L, false, 7_000L))
        assertEquals(20_000L, tracker.estimate(20_000L, false, 30_000L))
    }

    @Test
    fun trackerResetsOnUnknownReading() {
        val tracker = PlaybackPositionTracker()
        tracker.estimate(10_000L, true, 1_000L)
        assertEquals(-1L, tracker.estimate(-1L, true, 2_000L))
        // 重置后第一次读数重新建基准
        assertEquals(5_000L, tracker.estimate(5_000L, true, 9_000L))
    }
}
