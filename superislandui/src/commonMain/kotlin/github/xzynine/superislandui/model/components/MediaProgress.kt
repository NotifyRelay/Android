package github.xzynine.superislandui.model.components

/**
 * 媒体播放进度（计时器口径）。
 *
 * [positionMs] 是 [sampledAtMs] 时刻的播放位置；显示侧按本地时钟推算当前值，
 * 对端只需在换曲与固定周期推送，不必按进度高频发送。
 */
data class MediaProgress(
    /** 采样时的播放位置（毫秒）。 */
    val positionMs: Long,
    /** 曲目总时长（毫秒）；未知或直播为 0。 */
    val durationMs: Long,
    /** 是否正在播放。 */
    val isPlaying: Boolean,
    /** [positionMs] 的采样时刻（epoch 毫秒）；0 表示不推进。 */
    val sampledAtMs: Long,
) {
    /** 是否能据此显示进度：需要已知总时长。 */
    val isUsable: Boolean
        get() = durationMs > 0L && positionMs >= 0L
}
