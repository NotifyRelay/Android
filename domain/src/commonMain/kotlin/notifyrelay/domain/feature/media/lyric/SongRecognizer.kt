package notifyrelay.domain.feature.media.lyric

/**
 * 一次媒体元数据的识别结果。
 *
 * [songKey] 是跨端共享的歌曲身份：歌词滚动只改展示文本，不改它；
 * [songSwitched] 为真才表示换了歌。
 */
data class SongRecognition(
    /** 歌曲身份；同一首歌在歌词滚动期间保持不变。 */
    val songKey: String,
    /** 是否识别到换歌（首次观测也算新歌）。 */
    val songSwitched: Boolean,
    /** 是否为该源的首次观测。 */
    val firstObservation: Boolean,
    /** 本首歌的歌词状态。 */
    val state: LyricState,
    /** 已确认的歌词所在字段；未确认时为 [LyricField.TITLE]。 */
    val lyricField: LyricField,
    /** 展示主位文本：带歌词时为当前歌词，否则为歌名或视频标题。 */
    val displayTitle: String,
    /** 展示副位文本。 */
    val displayArtist: String,
    /** 当前歌词；非歌词态为 null。 */
    val lyricText: String?,
    /** 本次观测的曲目时长（毫秒），供调用方回传时复用。 */
    val durationMs: Long,
)

/**
 * 单曲识别：判定歌曲身份、歌词所在字段与歌词状态。
 *
 * 歌词位会滚动变化，因此不能拿「标题变了」当换歌依据。身份按以下优先级取稳定字段：
 * 媒体队列项标识 → 时长 → 标题 → 歌手。歌词在标题的应用其标题每句都变，
 * 故标识优先取 `mediaId`，其次时长；只有流媒体等时长未知的场景才退回文本字段。
 *
 * 字段判定按调用方的源键（通常是包名）常驻，跨切歌保留；
 * 歌词状态按歌曲重置。调用方须串行调用同一实例（各平台均由单一线程驱动）。
 */
class SongRecognizer {
    /** 歌词字段判定确认所需的最小连续观测次数。 */
    private companion object {
        const val LYRIC_FIELD_CONFIRM_THRESHOLD = 2
    }

    private val probes = mutableMapOf<String, LyricFieldProbe>()

    /**
     * 观测一次元数据并返回识别结果。
     *
     * @param sourceKey 观测源键（通常是包名）；同一源的字段学习跨切歌保留
     * @param title 元数据原始标题
     * @param artist 元数据原始歌手
     * @param durationMs 曲目时长；未知为 0
     * @param mediaId 媒体队列项标识；不可用时为空串
     * @param positionMs 当前播放位置；不可用为负数
     * @param nowMs 当前时刻（epoch 毫秒）
     */
    fun observe(
        sourceKey: String,
        title: String,
        artist: String,
        durationMs: Long,
        mediaId: String,
        positionMs: Long,
        nowMs: Long,
    ): SongRecognition {
        val probe = probes.getOrPut(sourceKey) { LyricFieldProbe() }
        probe.lastDuration = durationMs

        val prevTitle = probe.lastTitle
        val prevArtist = probe.lastArtist
        probe.lastTitle = title
        probe.lastArtist = artist
        val prevPosition = probe.lastPosition
        probe.lastPosition = positionMs

        val songId = songKeyOf(title, artist, durationMs, mediaId)
        val firstObservation = prevTitle == null && prevArtist == null
        var songSwitched = false

        if (firstObservation) {
            // 首次观测：建立本首歌基线
            probe.songId = songId
            probe.songStartAt = nowMs
            probe.state = LyricState.PENDING_LYRIC
        } else {
            val titleChanged = prevTitle != title
            val artistChanged = prevArtist != artist

            if (songId != probe.songId) {
                // 换歌：结算上一首（仅以终态计数，未确认的歌曲不计入自适应阈值），状态按歌曲重置
                when (probe.state) {
                    LyricState.LYRIC -> probe.lyricHits += 1
                    LyricState.INSTRUMENTAL -> probe.instrHits += 1
                    else -> { /* PENDING 状态不计入 */ }
                }
                probe.songId = songId
                probe.songStartAt = nowMs
                probe.state = LyricState.PENDING_LYRIC
                probe.candidate = null
                probe.streak = 0
                // 换歌后重置位置基准：上首歌的残留位置会把新歌首帧判成纯音乐
                probe.lastPosition = positionMs
                songSwitched = true
            } else if (titleChanged != artistChanged) {
                // 单一字段变化：歌词滚动
                val current = if (titleChanged) LyricField.TITLE else LyricField.ARTIST
                if (current == probe.candidate) {
                    probe.streak += 1
                } else {
                    probe.candidate = current
                    probe.streak = 1
                }
                if (probe.streak >= LYRIC_FIELD_CONFIRM_THRESHOLD && probe.confirmed != current) {
                    probe.confirmed = current
                }
                // 已确认该字段，或连续观测已达阈值，才切歌词态；避免学习期首次滚动即误判
                val canLyric = probe.confirmed == current || probe.streak >= LYRIC_FIELD_CONFIRM_THRESHOLD
                if (canLyric && probe.state != LyricState.LYRIC) {
                    probe.state = LyricState.LYRIC
                }
            }

            // 软/硬超时推进（帧驱动 + 定时复核双保险）
            // 歌词态粘滞：同一首歌内即使长时间无滚动（前奏/间奏）也不回退
            if (!songSwitched &&
                (probe.state == LyricState.PENDING_LYRIC || probe.state == LyricState.PENDING_INSTRUMENTAL)
            ) {
                val threshold = instrumentalThreshold(probe)
                if (positionMs >= 0) {
                    // 位置可用：只用歌曲内进度判定，暂停不推进，暂停期间不会因墙钟误判
                    if (positionMs > prevPosition && positionMs >= threshold) {
                        probe.state = LyricState.INSTRUMENTAL
                    } else if (probe.state == LyricState.PENDING_LYRIC && positionMs >= threshold / 2) {
                        probe.state = LyricState.PENDING_INSTRUMENTAL
                    }
                } else {
                    val elapsed = nowMs - probe.songStartAt
                    if (elapsed >= threshold) {
                        probe.state = LyricState.INSTRUMENTAL
                    } else if (probe.state == LyricState.PENDING_LYRIC && elapsed >= threshold / 2) {
                        probe.state = LyricState.PENDING_INSTRUMENTAL
                    }
                }
            }
        }

        val confirmedField = probe.confirmed ?: LyricField.TITLE
        val lyricState = probe.state
        val lyricField =
            when {
                lyricState != LyricState.LYRIC -> LyricField.TITLE
                else -> confirmedField
            }
        val isLyric = lyricState == LyricState.LYRIC
        // 歌词在歌手位时互换，保证下游主位拿到的是歌词
        val swap = isLyric && confirmedField == LyricField.ARTIST
        val lyricText =
            when {
                !isLyric -> null
                swap -> artist
                else -> title
            }

        return SongRecognition(
            songKey = songId,
            songSwitched = songSwitched,
            firstObservation = firstObservation,
            state = lyricState,
            lyricField = lyricField,
            displayTitle = if (swap) artist else title,
            displayArtist = if (swap) title else artist,
            lyricText = lyricText,
            durationMs = durationMs,
        )
    }

    /** 该源当前识别到的歌曲身份；未观测过时为 null。 */
    fun songKeyOf(sourceKey: String): String? = probes[sourceKey]?.songId

    /** 该源当前歌词状态；未观测过时为 null。 */
    fun stateOf(sourceKey: String): LyricState? = probes[sourceKey]?.state

    /** 该源已确认的歌词字段；未确认时为 null。 */
    fun confirmedFieldOf(sourceKey: String): LyricField? = probes[sourceKey]?.confirmed

    /** 该源自适应的纯音乐判定阈值；未观测过时为保守值。 */
    fun instrumentalThresholdOf(sourceKey: String): Long {
        val probe = probes[sourceKey] ?: return T_INSTRUMENTAL_SLOW_MS
        return instrumentalThreshold(probe)
    }

    /** 丢弃某个源的观测状态（源被移除时调用）。 */
    fun forget(sourceKey: String) {
        probes.remove(sourceKey)
    }

    /**
     * 歌曲身份：歌词位会滚动变化，故按稳定性依次取媒体标识、时长、标题、歌手。
     */
    private fun songKeyOf(
        title: String,
        artist: String,
        durationMs: Long,
        mediaId: String,
    ): String =
        when {
            mediaId.isNotEmpty() -> mediaId
            durationMs > 0L -> durationMs.toString()
            title.isNotEmpty() -> title
            else -> artist
        }
}
