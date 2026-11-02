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
)
