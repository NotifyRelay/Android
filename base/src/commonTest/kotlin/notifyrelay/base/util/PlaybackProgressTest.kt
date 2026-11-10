package notifyrelay.base.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 进度推算：按计时器口径，播放中推进、暂停停住、到总时长即止。 */
class PlaybackProgressTest {
    @Test
    fun playingAdvancesFromSample() {
        assertEquals(30_000L, PlaybackProgress.positionAt(20_000L, 180_000L, true, 1_000L, 11_000L))
    }

    @Test
    fun pausedStaysAtSample() {
        assertEquals(20_000L, PlaybackProgress.positionAt(20_000L, 180_000L, false, 1_000L, 91_000L))
    }

    @Test
    fun playingWithoutSampleTimeDoesNotAdvance() {
        assertEquals(20_000L, PlaybackProgress.positionAt(20_000L, 180_000L, true, 0L, 91_000L))
    }

    @Test
    fun positionClampsToTotal() {
        assertEquals(180_000L, PlaybackProgress.positionAt(170_000L, 180_000L, true, 1_000L, 91_000L))
    }

    @Test
    fun unknownTotalHasNoUpperBound() {
        assertEquals(20_000L, PlaybackProgress.positionAt(20_000L, null, false, 1_000L, 91_000L))
        assertNull(PlaybackProgress.fractionAt(20_000L, null, true, 1_000L, 91_000L))
    }

    @Test
    fun fractionFollowsAdvancedPosition() {
        assertEquals(0.5f, PlaybackProgress.fractionAt(0L, 200_000L, true, 1_000L, 101_000L))
        assertEquals(0f, PlaybackProgress.fractionAt(0L, 200_000L, false, 1_000L, 101_000L))
    }
}
