package notifyrelay.base.util

/**
 * 计时器基准的本地换算，供超级岛计时器组件与媒体进度共用。
 *
 * 计时器由发送端给出「起点/终点时刻」与「状态变更时的系统时刻」。这两个时间戳都取发送端
 * 时钟，直接拿它们与本地当前时刻相减，会把两端时钟偏差算进显示值：时钟快的一方看到的
 * 计时偏大，暂停时又回落到真实值。
 *
 * 做法同历史实现 `ApplyTimerBase`：两个时间戳之差与时钟原点无关，只有它是可靠的；
 * 接收端以本地当前时刻重算起点（正计时）或终点（倒计时），此后所有计算都用本地时钟。
 */
object TimerClock {
    /**
     * 换算到本地时钟后的基准。
     *
     * @param whenMs 正计时的起点 / 倒计时的终点（本地时钟）
     * @param currentMs 状态采样时刻（本地时钟，即换算时的本地当前时刻）
     */
    data class Base(
        val whenMs: Long,
        val currentMs: Long,
    )

    /**
     * 把计时器基准换算到本地时钟。
     *
     * @param timerType 计时类型：正计时取 1/2，倒计时取 -1/-2（与超级岛计时器口径一致）；
     *   公式两个方向同构，该参数保留给调用方表达语义，不参与计算分支
     * @param timerWhen 对端的计时起点（正计时）或终点（倒计时）
     * @param timerSystemCurrent 对端的状态采样时刻
     * @param localNowMs 本地当前时刻
     * @return 换算后的基准；时间戳无效时为 null
     */
    fun rebase(
        timerType: Int,
        timerWhen: Long,
        timerSystemCurrent: Long,
        localNowMs: Long,
    ): Base? {
        // 0 表示无计时；其余非计时类型同样不换算
        if (timerType != 1 && timerType != 2 && timerType != -1 && timerType != -2) return null
        if (timerWhen <= 0L || timerSystemCurrent <= 0L) return null
        // 两个方向的公式是同一条：偏移取「采样时刻 - 归零时刻」。
        // 正计时时它是已计时长，起点 = 本地当前 - 偏移；
        // 倒计时时它是负的剩余时长，终点 = 本地当前 - 偏移 = 本地当前 + 剩余。
        val offset = timerSystemCurrent - timerWhen
        return Base(whenMs = localNowMs - offset, currentMs = localNowMs)
    }
}
