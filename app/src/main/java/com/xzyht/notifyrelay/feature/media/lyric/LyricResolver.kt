package com.xzyht.notifyrelay.feature.media.lyric

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.media.lyric.LyricField
import notifyrelay.domain.feature.media.lyric.LyricState
import notifyrelay.domain.feature.media.lyric.SongRecognition
import notifyrelay.domain.feature.media.lyric.SongRecognizer
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌词与单曲识别的安卓接线。
 *
 * 识别本身在公共端的 [SongRecognizer]：歌曲身份、歌词所在字段与歌词状态都由它判定，
 * 桌面端对远端报文跑同一套识别。本类只负责取 [MediaController] 的元数据、按稳态复核调度，
 * 并把结果回传给宿主。
 *
 * 观测状态按包名常驻且跨服务实例保留：监听服务会被系统频繁解绑重建，
 * 实例字段会导致学习状态丢失。
 */
internal class LyricResolver(
    private val handler: Handler,
    /** 识别结果回传：宿主据此上报本机媒体状态。 */
    private val onRecognized: (pkg: String, recognition: SongRecognition, artBitmap: Any?, position: Long, isPlaying: Boolean) -> Unit,
    private val getPrimaryController: () -> MediaController?,
) {
    companion object {
        private const val TAG = "LyricResolver"

        // 已知歌词字段的包名切歌后快速监听：100ms 粒度、持续 1.5s，
        // 首次歌词滚动一出现即切歌词态，不再走慢速阶梯
        private const val FAST_WATCH_INTERVAL_MS = 100L
        private const val FAST_WATCH_WINDOW_MS = 1_500L

        // 歌词字段未知时的保守阶梯
        private val COLD_PKG_RECHECK_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L, 6_000L)

        /** 跨服务实例保留的识别器。 */
        private val recognizer = SongRecognizer()
    }

    /** 各包名上次回传时的歌词状态：复核只在状态推进时回传，避免重复推送。 */
    private val reportedStates = ConcurrentHashMap<String, LyricState>()

    /**
     * 观测一次元数据并完成字段映射。
     *
     * @return 识别结果；[SongRecognition.displayTitle] 为展示主位（带歌词时为当前歌词）
     */
    fun resolve(
        pkg: String,
        title: String,
        artist: String,
        duration: Long,
        mediaId: String,
        position: Long,
    ): SongRecognition {
        val result = observe(pkg, title, artist, duration, mediaId, position)
        reportedStates[pkg] = result.state
        if (result.songSwitched && !result.firstObservation) {
            Logger.i(TAG, "换歌: $pkg songKey=" + result.songKey)
            scheduleLyricProbeRechecks(pkg, result.state)
        }
        return result
    }

    private fun observe(
        pkg: String,
        title: String,
        artist: String,
        duration: Long,
        mediaId: String,
        position: Long,
    ): SongRecognition =
        synchronized(recognizer) {
            recognizer.observe(
                sourceKey = pkg,
                title = title,
                artist = artist,
                durationMs = duration,
                mediaId = mediaId,
                positionMs = position,
                nowMs = System.currentTimeMillis(),
            )
        }

    /**
     * 新歌开始后的主动复核：
     * - 已确认歌词在歌手位的包名：100ms 粒度快速监听 1.5s，窗口结束再按自适应阈值复核一次
     * - 其余包名：走保守阶梯
     */
    private fun scheduleLyricProbeRechecks(
        pkg: String,
        state: LyricState,
    ) {
        handler.removeCallbacks(recheckRunnable)
        val delays = mutableListOf<Long>()
        val confirmedArtist =
            synchronized(recognizer) {
                recognizer.confirmedFieldOf(pkg) == LyricField.ARTIST
            }
        if (confirmedArtist) {
            var d = FAST_WATCH_INTERVAL_MS
            while (d <= FAST_WATCH_WINDOW_MS) {
                delays.add(d)
                d += FAST_WATCH_INTERVAL_MS
            }
            val threshold = synchronized(recognizer) { recognizer.instrumentalThresholdOf(pkg) }
            delays.add(threshold)
        } else {
            COLD_PKG_RECHECK_DELAYS_MS.forEach { delays.add(it) }
        }
        delays.forEach { delay -> handler.postDelayed(recheckRunnable, delay) }
    }

    private val recheckRunnable =
        Runnable {
            val primary = getPrimaryController() ?: return@Runnable
            val pkg = primary.packageName
            // 读取当前元数据（而非缓存），使歌词滚动变化能走到识别的字段变化分支
            val metadata = primary.metadata ?: return@Runnable
            val currentTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return@Runnable
            val currentArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
            val playbackState = primary.playbackState
            val position = playbackState?.position ?: -1L
            val before = reportedStates[pkg]
            val result = observe(pkg, currentTitle, currentArtist, duration, mediaId, position)
            // 仅在歌词状态确实推进时回传，避免重复推送
            if (result.state == before) return@Runnable
            reportedStates[pkg] = result.state
            // 复核所需封面按需从当前元数据读取，避免长期持有 Bitmap
            val artBitmap =
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            onRecognized(pkg, result, artBitmap, position, playbackState?.state == PlaybackState.STATE_PLAYING)
            Logger.i(TAG, "歌词状态推进: $pkg -> " + result.state)
        }

    // 停止监控时取消已排队的歌词复核任务，避免残留任务读取新的 activeControllers
    fun cancelRechecks() {
        handler.removeCallbacks(recheckRunnable)
    }
}
