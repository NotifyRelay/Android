package com.xzyht.notifyrelay.feature.notification.superisland.media

import android.content.Context
import github.xzynine.superislandui.common.TextSplitter
import notifyrelay.base.util.DeviceUtils
import notifyrelay.data.StorageManager

/**
 * 歌词拆分（D2 去重）。
 *
 * 原先 `MediaCapsulePresenter.show` 与 `MediaReplicaNotifier.build` 各有一份逐字相同的
 * 拆分逻辑（含 `lyrics_split_mode` 读取、平板默认判定、阈值 12 与 [TextSplitter] 调用），
 * 现统一到此处。
 */
object LyricsSplitter {
    /** 歌词分割阈值：远端和本地都保持 6 字符开始分割（超阈值才拆）。 */
    private const val THRESHOLD = 12

    /** `lyrics_split_mode` 存储 key：0=默认（平板不分割，手机分割）1=分割 2=不分割。 */
    private const val LYRICS_SPLIT_MODE_KEY = "lyrics_split_mode"

    /**
     * 拆分结果：`iconText` 为左岛（图文组件）文本，`capsuleText` 为右胶囊文本。
     *
     * 不分割时 `iconText` 为空、`capsuleText` 为完整歌词（不截断）。
     */
    data class SplitLyrics(
        val iconText: String,
        val capsuleText: String,
    )

    /**
     * 按用户设置与文本长度拆分歌词。
     *
     * @param title 原始歌词文本（null 视为空串，与原实现 `title.orEmpty()` 一致）。
     */
    fun split(
        context: Context,
        title: String?,
    ): SplitLyrics {
        val lyricText = title.orEmpty()

        // 检查歌词分割模式设置：1=分割 2=不分割，其余按设备类型（平板不分割，手机分割）
        val lyricsSplitMode = StorageManager.getInt(context, LYRICS_SPLIT_MODE_KEY, 0)
        val shouldSplit =
            when (lyricsSplitMode) {
                1 -> true
                2 -> false
                else -> !DeviceUtils.isTablet(context)
            }

        if (!shouldSplit) {
            // 不分割时，不进行任何截断和拆分，完整显示所有文本
            return SplitLyrics(iconText = "", capsuleText = lyricText)
        }

        val textLength = TextSplitter.calculateTextLength(lyricText)
        if (textLength <= THRESHOLD) {
            return SplitLyrics(iconText = "", capsuleText = lyricText)
        }

        val (splitIconText, splitCapsuleText) = TextSplitter.splitLyric(lyricText, THRESHOLD)
        return SplitLyrics(iconText = splitIconText, capsuleText = splitCapsuleText)
    }
}
