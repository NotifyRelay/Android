package notifyrelay.desktop.platform

import notifyrelay.data.Storage

/**
 * 桌面端叠加层设置。
 *
 * 仅桌面生效：控制叠加层是否显示、弹幕字号与速度、以及顶部媒体卡的自动收起时长。
 * 读写经公共 [Storage]，与其它桌面偏好同库。
 */
object DesktopOverlaySettings {
    private const val KEY_ENABLED = "desktop_overlay_enabled"
    private const val KEY_DANMAKU_FONT_SIZE = "desktop_overlay_danmaku_font_size"
    private const val KEY_DANMAKU_SPEED = "desktop_overlay_danmaku_speed"
    private const val KEY_DANMAKU_AREA_PERCENT = "desktop_overlay_danmaku_area_percent"
    private const val KEY_MEDIA_AUTO_COLLAPSE_MS = "desktop_overlay_media_auto_collapse_ms"

    /** 弹幕字号可选值（像素）：对应 FontSizePercent 25/50/75/100/150，字号 = 36 × 百分比。 */
    val FONT_SIZE_OPTIONS = listOf(9, 18, 27, 36, 54)

    /** 弹幕速度可选值（像素/秒）：对应 DanmakuSpeed 1/2/3/6/9，速度 = Speed × 60。 */
    val SPEED_OPTIONS = listOf(60, 120, 180, 360, 540)

    /** 弹幕显示区域可选值（占屏高百分比）。 */
    val AREA_PERCENT_OPTIONS = listOf(30, 45, 60, 80, 100)

    /** 媒体卡展开后自动收起时长可选值（毫秒）。 */
    val MEDIA_AUTO_COLLAPSE_OPTIONS = listOf(3_000, 5_000, 8_000, 15_000)

    /** 叠加层是否显示。 */
    var enabled: Boolean
        get() = Storage.getBoolean(KEY_ENABLED, true)
        set(value) = Storage.putBoolean(KEY_ENABLED, value)

    /** 弹幕字号（dp）。 */
    var danmakuFontSize: Float
        get() = Storage.getFloat(KEY_DANMAKU_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = Storage.putFloat(KEY_DANMAKU_FONT_SIZE, value.coerceIn(12f, 48f))

    /** 弹幕速度（像素/秒）。 */
    var danmakuSpeed: Float
        get() = Storage.getFloat(KEY_DANMAKU_SPEED, DEFAULT_SPEED)
        set(value) = Storage.putFloat(KEY_DANMAKU_SPEED, value.coerceIn(60f, 800f))

    /** 弹幕显示区域（占屏高百分比）。 */
    var danmakuAreaPercent: Float
        get() = Storage.getFloat(KEY_DANMAKU_AREA_PERCENT, DEFAULT_AREA_PERCENT)
        set(value) = Storage.putFloat(KEY_DANMAKU_AREA_PERCENT, value.coerceIn(10f, 100f))

    /** 媒体卡展开后自动收起时长（毫秒）。 */
    var mediaAutoCollapseMs: Long
        get() = Storage.getInt(KEY_MEDIA_AUTO_COLLAPSE_MS, DEFAULT_MEDIA_AUTO_COLLAPSE_MS.toInt()).toLong()
        set(value) = Storage.putInt(KEY_MEDIA_AUTO_COLLAPSE_MS, value.toInt())

    /**
     * 默认值：与 C# `GeneralSettingsService.Danmaku` 一致（不是 `DanmakuStyleSettings` 的类内默认值）。
     *
     * `DanmakuFontSizePercent = 50` → 字号 18px；`DanmakuSpeed = 3` → 180 px/s；
     * `DanmakuDisplayAreaPercent = 100`。
     */
    private const val DEFAULT_FONT_SIZE = 18f
    private const val DEFAULT_SPEED = 180f
    private const val DEFAULT_AREA_PERCENT = 100f
    private const val DEFAULT_MEDIA_AUTO_COLLAPSE_MS = 5_000L
}
