package notifyrelay.base.util

/**
 * 播放进度的本地推算。
 *
 * 媒体进度按超级岛计时器口径传递：发送端给出「采样位置 + 采样时刻 + 播放态 + 总时长」，
 * 接收端按本地时钟推进，因此发送端不必按进度高频推送。
 *
 * 推算规则与计时器组件一致：播放中取「采样位置 + 经过时间」，暂停则停在采样位置，
 * 两者都以总时长为上限。
 */
object PlaybackProgress {
    /**
     * 推算 [nowMs] 时刻的播放位置。
     *
     * @param positionMs 采样位置（毫秒）
     * @param durationMs 总时长（毫秒）；非正表示未知或直播，此时不设上限
     * @param isPlaying 采样时刻是否在播放
     * @param sampledAtMs 采样时刻（epoch 毫秒）；非正表示未标注，不推进
     * @param nowMs 当前时刻（epoch 毫秒）
     * @return 推算位置；总时长未知时返回采样位置的截断值
     */
    fun positionAt(
        positionMs: Long,
        durationMs: Long?,
        isPlaying: Boolean,
        sampledAtMs: Long,
        nowMs: Long,
    ): Long {
        val base = positionMs.coerceAtLeast(0L)
        val total = durationMs?.takeIf { it > 0L } ?: return base
        val advanced =
            if (isPlaying && sampledAtMs > 0L) {
                base + (nowMs - sampledAtMs).coerceAtLeast(0L)
            } else {
                base
            }
        return advanced.coerceIn(0L, total)
    }

    /**
     * 推算 [nowMs] 时刻的进度比例。
     *
     * @return 0~1；总时长未知时为 null
     */
    fun fractionAt(
        positionMs: Long,
        durationMs: Long?,
        isPlaying: Boolean,
        sampledAtMs: Long,
        nowMs: Long,
    ): Float? {
        val total = durationMs?.takeIf { it > 0L } ?: return null
        val current = positionAt(positionMs, total, isPlaying, sampledAtMs, nowMs)
        return (current.toFloat() / total).coerceIn(0f, 1f)
    }
}
