package com.xzyht.notifyrelay.feature.media.lyric

import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.Handler
import notifyrelay.base.util.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌词字段判定与复核调度。持有跨服务实例保留的 [lyricFieldProbes]（见 companion）与
 * 主线程 [handler]，并通过注入的回调与宿主解耦：
 * - [onMediaSessionUpdated]：状态推进时重新上报给 NotifyRelayNotificationListenerService
 * - [getPrimaryController]：复核时读取当前主控制器
 */
class LyricResolver(
    private val handler: Handler,
    private val onMediaSessionUpdated: (pkg: String, title: String, artist: String, duration: Long, bitmap: Any?) -> Unit,
    private val getPrimaryController: () -> MediaController?,
) {
    companion object {
        private const val TAG = "LyricResolver"

        // 歌词字段判定所需的最小连续观测次数
        private const val LYRIC_FIELD_CONFIRM_THRESHOLD = 2

        // 已知歌词字段的包名：切歌后快速监听（100ms 粒度，持续 1.5s），
        // 首次歌词滚动一出现即切 LYRIC，不再走慢速阶梯
        private const val FAST_WATCH_INTERVAL_MS = 100L
        private const val FAST_WATCH_WINDOW_MS = 1_500L

        // 歌词字段未知时的保守阶梯
        private val COLD_PKG_RECHECK_DELAYS_MS =
            longArrayOf(1_000L, 2_000L, T_INSTRUMENTAL_NORMAL_MS, T_INSTRUMENTAL_SLOW_MS)

        // 歌词字段观测状态：按包名保存，且需跨服务实例保留
        // （监听服务会被系统频繁解绑重建，实例字段会导致学习状态丢失）
        private val lyricFieldProbes = ConcurrentHashMap<String, LyricFieldProbe>()
    }

    /**
     * 判定歌词所在字段并完成字段映射（仅作用于获取层，下游仍按 title=歌词位 约定处理）。
     *
     * 字段判定（按包名常驻，跨服务重建与切歌保留）：
     * - 仅 title 变化 → 歌词在 title；仅 artist 变化 → 歌词在 artist
     * - 同向连续 [LYRIC_FIELD_CONFIRM_THRESHOLD] 次后确认；未确认前默认 TITLE（兼容既有行为）
     *
     * 歌曲状态（per-song，切歌即重置）：
     * - [LyricState.PENDING_LYRIC]：新歌，等待判断；主位暂显歌名/标题
     * - [LyricState.PENDING_INSTRUMENTAL]：超过 [T_SOFT_MS] 仍无歌词滚动，倾向纯音乐但不回退
     * - [LyricState.INSTRUMENTAL]：超过 [T_HARD_MS] 仍无歌词滚动，回退原始字段
     * - [LyricState.LYRIC]：本首歌已出现歌词滚动，粘滞（前奏/间奏不再回退）
     *
     * @return Pair(歌词位文本, 歌手位文本)
     */
    fun resolveLyricField(
        pkg: String,
        title: String,
        artist: String,
        duration: Long,
        mediaId: String,
        position: Long,
    ): Pair<String, String> {
        val probe = lyricFieldProbes.computeIfAbsent(pkg) { LyricFieldProbe() }
        return synchronized(probe) {
            probe.lastDuration = duration

            val prevTitle = probe.lastTitle
            val prevArtist = probe.lastArtist
            probe.lastTitle = title
            probe.lastArtist = artist
            val prevPosition = probe.lastPosition
            probe.lastPosition = position

            val now = System.currentTimeMillis()
            // 歌曲标识优先用稳定的队列项标识（mediaId）；其次时长（歌词位不参与，
            // 歌词在 title 的应用其 title 每句都变）；时长未知的流媒体再退回标题/歌手。
            // 优先 mediaId 可避免相同时长或歌词滚动造成的误判切歌，确保同曲元数据变化不重置 streak。
            val songId =
                when {
                    mediaId.isNotEmpty() -> mediaId
                    duration > 0L -> duration.toString()
                    title.isNotEmpty() -> title
                    else -> artist
                }

            if (prevTitle == null && prevArtist == null) {
                // 首次观测：建立本首歌基线
                probe.songId = songId
                probe.songStartAt = now
                probe.state = LyricState.PENDING_LYRIC
            } else {
                val titleChanged = prevTitle != title
                val artistChanged = prevArtist != artist
                // 本轮是否发生切歌：切歌时 prevPosition 仍为上首歌的残留值，
                // 需跳过本轮基于位置的超时推进，避免新歌首帧即被判定为 INSTRUMENTAL
                var songSwitched = false

                if (songId != probe.songId) {
                    // 切歌：结算上一首（带歌词/纯音乐），状态按歌曲重置，不沿用上一首结论
                    // 仅以结算时的终态计入历史：LYRIC 计歌词命中、INSTRUMENTAL 计纯音乐命中；
                    // 仍处于 PENDING 的歌曲尚未确认是否有歌词，不计入任一计数器，避免污染自适应阈值。
                    when (probe.state) {
                        LyricState.LYRIC -> probe.lyricHits += 1
                        LyricState.INSTRUMENTAL -> probe.instrHits += 1
                        else -> { /* PENDING_LYRIC / PENDING_INSTRUMENTAL 不计入 */ }
                    }
                    probe.songId = songId
                    probe.songStartAt = now
                    probe.state = LyricState.PENDING_LYRIC
                    probe.candidate = null
                    probe.streak = 0
                    // 切歌后重置播放位置基准，确保新歌的进度判定不与上首歌残留位置比较
                    probe.lastPosition = position
                    songSwitched = true
                    scheduleLyricProbeRechecks(probe)
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
                        Logger.i(TAG, "歌词字段切换: $pkg -> $current (title=$title, artist=$artist)")
                    }
                    // 仅在字段已确认（含本轮刚确认）或未确认但连续观测已达阈值时才切 LYRIC，
                    // 避免学习阶段首次歌词滚动即误判为带歌词。
                    val canLyric = probe.confirmed == current || probe.streak >= LYRIC_FIELD_CONFIRM_THRESHOLD
                    if (canLyric && probe.state != LyricState.LYRIC) {
                        probe.state = LyricState.LYRIC
                        Logger.i(TAG, "歌词状态: $pkg -> LYRIC (title=$title, artist=$artist)")
                    }
                }

                // 软/硬超时推进（帧驱动 + 定时复核双保险）
                // LYRIC 状态粘滞：同一 songId 内即使长时间无歌词变化（前奏/间奏）也不回退
                if (!songSwitched &&
                    (probe.state == LyricState.PENDING_LYRIC || probe.state == LyricState.PENDING_INSTRUMENTAL)
                ) {
                    val threshold = instrumentalThreshold(probe)
                    if (position >= 0) {
                        // 播放位置可用：仅用歌曲内进度判定（暂停不推进，暂停期间不会因墙钟误判为纯音乐）
                        val playedEnough = position > prevPosition && position >= threshold
                        if (playedEnough) {
                            probe.state = LyricState.INSTRUMENTAL
                            Logger.i(TAG, "歌词状态: $pkg -> INSTRUMENTAL (title=$title, artist=$artist)")
                        } else if (probe.state == LyricState.PENDING_LYRIC && position >= threshold / 2) {
                            probe.state = LyricState.PENDING_INSTRUMENTAL
                        }
                    } else {
                        // 播放位置不可用：回退墙钟时间判断
                        val elapsed = now - probe.songStartAt
                        if (elapsed >= threshold) {
                            probe.state = LyricState.INSTRUMENTAL
                            Logger.i(TAG, "歌词状态: $pkg -> INSTRUMENTAL (title=$title, artist=$artist)")
                        } else if (probe.state == LyricState.PENDING_LYRIC && elapsed >= threshold / 2) {
                            probe.state = LyricState.PENDING_INSTRUMENTAL
                        }
                    }
                }
            }

            if (probe.state == LyricState.LYRIC && probe.confirmed == LyricField.ARTIST) {
                // artist 为歌词位时互换，保证下游拿到的 title 始终是歌词
                artist to title
            } else {
                // 等待判定/纯音乐/歌词在 title：主位保留歌名或视频标题
                title to artist
            }
        }
    }

    /**
     * 新歌开始后的主动复核：
     * - 已知歌词字段（confirmed=ARTIST）的包名：100ms 粒度快速监听 1.5s，
     *   首次歌词滚动一出现即切 LYRIC，不再走慢速阶梯；窗口结束再按自适应阈值复核一次
     * - 字段未知的包名：走保守阶梯
     */
    private fun scheduleLyricProbeRechecks(probe: LyricFieldProbe) {
        handler.removeCallbacks(lyricProbeRecheckRunnable)
        val delays = mutableListOf<Long>()
        if (probe.confirmed == LyricField.ARTIST) {
            var d = FAST_WATCH_INTERVAL_MS
            while (d <= FAST_WATCH_WINDOW_MS) {
                delays.add(d)
                d += FAST_WATCH_INTERVAL_MS
            }
            delays.add(instrumentalThreshold(probe))
        } else {
            COLD_PKG_RECHECK_DELAYS_MS.forEach { delays.add(it) }
        }
        delays.forEach { delay -> handler.postDelayed(lyricProbeRecheckRunnable, delay) }
    }

    private val lyricProbeRecheckRunnable =
        Runnable {
            val primary = getPrimaryController() ?: return@Runnable
            val pkg = primary.packageName
            val probe = lyricFieldProbes[pkg] ?: return@Runnable
            // 读取当前元数据（而非 probe 缓存），使歌词滚动变化能触发 resolveLyricField 的切字段分支
            val primaryMetadata = primary.metadata
            val currentTitle = primaryMetadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return@Runnable
            val currentArtist = primaryMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val before = probe.state
            val position = primary.playbackState?.position ?: -1L
            // 复核所需封面按需从当前 MediaController.metadata 重新读取，避免长期持有 Bitmap。
            val artBitmap =
                primaryMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: primaryMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val mediaId = primaryMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
            val (mappedTitle, mappedArtist) =
                resolveLyricField(pkg, currentTitle, currentArtist, probe.lastDuration, mediaId, position)
            // 仅在状态确实推进时重新上报，避免重复推送
            if (probe.state != before) {
                onMediaSessionUpdated(pkg, mappedTitle, mappedArtist, probe.lastDuration, artBitmap)
            }
        }

    // 停止监控时取消已排队的歌词复核任务，避免残留任务读取新的 activeControllers
    fun cancelRechecks() {
        handler.removeCallbacks(lyricProbeRecheckRunnable)
    }
}
