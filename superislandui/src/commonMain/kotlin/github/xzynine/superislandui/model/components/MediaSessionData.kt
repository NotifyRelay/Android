package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.currentTimeMillis

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
    /** 当前播放位置（毫秒）；null 表示无进度信息。 */
    val positionMs: Long? = null,
    /** 曲目总时长（毫秒）；null 表示未知或直播。 */
    val durationMs: Long? = null,
    /** 是否正在播放。 */
    val isPlaying: Boolean = false,
) {
    /** 进度比例 0~1；无时长或无进度时为 null。 */
    val progressFraction: Float?
        get() {
            val total = durationMs?.takeIf { it > 0L } ?: return null
            val current = positionMs ?: return null
            return (current.toFloat() / total).coerceIn(0f, 1f)
        }
}
