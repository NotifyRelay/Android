package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.currentTimeMillis
import notifyrelay.base.util.PlaybackProgress

// 媒体会话快照：字段均为平台无关数据，时间戳默认值取跨源集的当前时间。
data class MediaSessionData(
    val packageName: String,
    val appName: String?,
    val title: String,
    val text: String,
    val coverUrl: String?,
    val appIconUrl: String? = null,
    val deviceName: String,
    val timestamp: Long = currentTimeMillis(),
    /** 采样时的播放位置（毫秒）；null 表示无进度信息。 */
    val positionMs: Long? = null,
    /** 曲目总时长（毫秒）；null 表示未知或直播。 */
    val durationMs: Long? = null,
    /** 是否正在播放。 */
    val isPlaying: Boolean = false,
    /** 上面 [positionMs] 的采样时刻（epoch 毫秒）；0 表示未标注，按不推进处理。 */
    val sampledAtMs: Long = 0L,
) {
    /**
     * 按计时器口径推算 [nowMs] 时刻的播放位置。
     *
     * 播放中自采样时刻起按经过时间推进，暂停则停在采样位置；两者都截到 [durationMs]。
     *
     * @param nowMs 当前时刻（epoch 毫秒）
     * @returns 推算位置；无进度信息时为 null
     */
    fun positionAt(nowMs: Long): Long? {
        val base = positionMs ?: return null
        return PlaybackProgress.positionAt(base, durationMs, isPlaying, sampledAtMs, nowMs)
    }

    /**
     * 按计时器口径推算 [nowMs] 时刻的进度比例（0~1）。
     *
     * @param nowMs 当前时刻（epoch 毫秒）
     * @returns 进度比例；无时长或无位置时为 null
     */
    fun progressFractionAt(nowMs: Long): Float? {
        val base = positionMs ?: return null
        return PlaybackProgress.fractionAt(base, durationMs, isPlaying, sampledAtMs, nowMs)
    }

    /** 采样时刻的进度比例 0~1；不做时间推进，供不随时间刷新的调用方使用。 */
    val progressFraction: Float?
        get() = progressFractionAt(sampledAtMs)
}
