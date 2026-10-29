package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import wtf.magma.audio.SMTCLoader

/**
 * 本机系统媒体会话（SMTC）读取。
 *
 * 数据来自 Rust 桥 `smtc_jni`（子模块 `desktop/smtc-rust`）：它直读 Windows 的
 * `GlobalSystemMediaTransportControlsSessionManager`，镜像当前持有媒体会话的应用
 * （Spotify、浏览器等）。本类只做取值与解析，不持有会话对象。
 *
 * 该桥只提供读取能力，没有播放控制导出；控制类指令由 [DesktopMediaKeySender] 以媒体键完成。
 */
internal object DesktopMediaSessionSource {
    private const val TAG = "DesktopMedia"

    /** 无会话时 Rust 侧返回的哨兵值。 */
    private const val NO_MEDIA = "No media"

    /** 字段分隔符（Rust 侧以 `|` 拼接）。 */
    private const val SEPARATOR = "|"

    /** 当前是否有可用媒体会话。 */
    fun hasSession(): Boolean {
        if (!SMTCLoader.available) return false
        val info = runCatching { SMTCLoader.getSmtcInfo() }.getOrNull() ?: return false
        return info.isNotBlank() && info != NO_MEDIA
    }

    /**
     * 读取一次会话快照。
     *
     * @return 快照；无会话或读取失败时为 null
     */
    fun read(): Snapshot? {
        if (!SMTCLoader.available) return null
        val info =
            runCatching { SMTCLoader.getSmtcInfo() }
                .onFailure { Logger.w(TAG, "读取媒体信息失败", it) }
                .getOrNull()
                ?: return null
        if (info.isBlank() || info == NO_MEDIA) return null

        // 格式：title|artist|pos_ms|dur_ms（标题/艺术家本身可能含分隔符，故从两端解析）
        val parts = info.split(SEPARATOR)
        if (parts.size < 4) return null
        val durationMs = parts.last().trim().toLongOrNull() ?: 0L
        val positionMs = parts[parts.size - 2].trim().toLongOrNull() ?: 0L
        val artist = parts[parts.size - 3]
        val title = parts.subList(0, parts.size - 3).joinToString(SEPARATOR)

        val status =
            runCatching { SMTCLoader.getPlaybackStatus() }
                .getOrDefault(STATUS_CLOSED)
        val sourceAppId =
            runCatching { SMTCLoader.getSourceAppId() }
                .getOrDefault("")

        return Snapshot(
            title = title.takeIf { it.isNotBlank() && it != "Unknown" },
            artist = artist.takeIf { it.isNotBlank() },
            positionMs = positionMs,
            durationMs = durationMs,
            playing = status == STATUS_PLAYING,
            sourceAppId = sourceAppId.takeIf { it.isNotBlank() },
        )
    }

    /** 一次媒体会话快照。 */
    data class Snapshot(
        val title: String?,
        val artist: String?,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val sourceAppId: String?,
    )

    /** Windows `MediaPlaybackStatus` 取值。 */
    private const val STATUS_CLOSED = 0
    private const val STATUS_PLAYING = 3
}
