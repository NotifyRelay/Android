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
            coverDataUrl = readCoverDataUrl(),
        )
    }

    /**
     * 读取封面并转成 data URL。
     *
     * Rust 侧返回原始图片字节的 base64；这里按图片内容的魔数判断 MIME，
     * 未知类型统一按 PNG 标注（Compose 解码器按内容识别，不依赖 MIME）。
     */
    private fun readCoverDataUrl(): String? {
        val base64 = runCatching { SMTCLoader.getCoverBase64() }.getOrNull()
        if (base64.isNullOrBlank()) return null
        val mime =
            runCatching {
                val head =
                    java.util.Base64
                        .getDecoder()
                        .decode(base64.take(16))
                when {
                    head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "image/jpeg"
                    head.size >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> "image/png"
                    else -> "image/png"
                }
            }.getOrDefault("image/png")
        return "data:" + mime + ";base64," + base64
    }

    /** 一次媒体会话快照。 */
    data class Snapshot(
        val title: String?,
        val artist: String?,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val sourceAppId: String?,
        val coverDataUrl: String? = null,
    )

    /** Windows `MediaPlaybackStatus` 取值。 */
    private const val STATUS_CLOSED = 0
    private const val STATUS_PLAYING = 3
}
