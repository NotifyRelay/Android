package notifyrelay.base.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 计时器基准换算：只用对端两个时间戳之差，偏差被消掉。 */
class TimerClockTest {
    @Test
    fun countUpRebasesToLocalStart() {
        // 对端时钟比本地快 30 秒：已计时 42 秒，本地起点应为当前时刻减 42 秒
        val base =
            TimerClock.rebase(
                timerType = 1,
                timerWhen = 1_000_000_000_000L,
                timerSystemCurrent = 1_000_000_000_000L + 42_000L,
                localNowMs = 1_700_000_300_000L,
            )
        assertEquals(1_700_000_300_000L - 42_000L, base?.whenMs)
        assertEquals(1_700_000_300_000L, base?.currentMs)
        assertEquals(42_000L, base!!.currentMs - base.whenMs, "已计时长不受时钟偏差影响")
    }

    @Test
    fun countDownRebasesToLocalEnd() {
        val base =
            TimerClock.rebase(
                timerType = -1,
                timerWhen = 1_000_000_300_000L,
                timerSystemCurrent = 1_000_000_000_000L,
                localNowMs = 1_700_000_000_000L,
            )
        assertEquals(1_700_000_300_000L, base?.whenMs, "终点应落在本地时钟上")
    }

    @Test
    fun pausedKeepsSampleOffset() {
        val base =
            TimerClock.rebase(
                timerType = 2,
                timerWhen = 1_000L,
                timerSystemCurrent = 61_000L,
                localNowMs = 900_000L,
            )
        assertEquals(60_000L, base!!.currentMs - base.whenMs, "暂停态显示采样时的已计时长")
    }

    @Test
    fun invalidTimestampsYieldNull() {
        assertNull(TimerClock.rebase(1, 0L, 1_000L, 5_000L))
        assertNull(TimerClock.rebase(1, 1_000L, 0L, 5_000L))
        assertNull(TimerClock.rebase(0, 1_000L, 2_000L, 5_000L), "非计时类型不换算")
    }
}
