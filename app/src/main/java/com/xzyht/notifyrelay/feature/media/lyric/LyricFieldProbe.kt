package com.xzyht.notifyrelay.feature.media.lyric

// 歌词所在字段
internal enum class LyricField { TITLE, ARTIST }

// 当前歌曲的歌词状态（per-song，切歌即重置）
internal enum class LyricState {
    // 新歌：等待判断是否带歌词
    PENDING_LYRIC,

    // T_SOFT 已到仍无歌词滚动：倾向纯音乐，但暂不回退
    PENDING_INSTRUMENTAL,

    // T_HARD 已到仍无歌词滚动：纯音乐/无歌词，回退原始字段
    INSTRUMENTAL,

    // 本首歌已出现歌词滚动：粘滞歌词（前奏/间奏不再回退）
    LYRIC,
}

// 按包名的歌词字段观测状态
internal class LyricFieldProbe {
    var lastTitle: String? = null
    var lastArtist: String? = null
    var candidate: LyricField? = null
    var streak: Int = 0
    var confirmed: LyricField? = null

    // 当前歌曲标识（切歌即变化，用标题区分：歌词不影响该值）
    var songId: String? = null

    // 当前歌曲起始时间（用于软/硬超时判定）
    var songStartAt: Long = 0L
    var state: LyricState = LyricState.PENDING_LYRIC

    // 包名历史：用于自适应纯音乐判定阈值
    var lyricHits: Int = 0
    var instrHits: Int = 0

    // 最近一次播放位置（用于"位置推进但无歌词"的直接判定）
    var lastPosition: Long = -1L

    // 复核上报所需的原始信息（封面按需从当前 MediaController.metadata 重新读取，避免长期持有 Bitmap）
    var lastDuration: Long = 0L
}

// 纯音乐判定阈值：按包名历史自适应。
// 常出现纯音乐的包名 2s 即可回退标题，出现过歌词的包名 4s，新包名保守 6s。
internal const val T_INSTRUMENTAL_FAST_MS = 2_000L
internal const val T_INSTRUMENTAL_NORMAL_MS = 4_000L
internal const val T_INSTRUMENTAL_SLOW_MS = 6_000L

internal fun instrumentalThreshold(probe: LyricFieldProbe): Long {
    val total = probe.lyricHits + probe.instrHits
    if (total == 0) return T_INSTRUMENTAL_SLOW_MS
    val instrRatio = probe.instrHits.toFloat() / total
    return when {
        instrRatio > 0.8f -> T_INSTRUMENTAL_FAST_MS
        probe.lyricHits > 0 -> T_INSTRUMENTAL_NORMAL_MS
        else -> T_INSTRUMENTAL_SLOW_MS
    }
}
