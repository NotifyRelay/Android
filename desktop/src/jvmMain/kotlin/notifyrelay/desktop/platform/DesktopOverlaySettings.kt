package notifyrelay.desktop.platform

import notifyrelay.data.Storage
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.OverlayAnchor

/**
 * 桌面端叠加层设置。
 *
 * 仅桌面生效：控制叠加层是否显示、弹幕样式、顶部元素开关与各类浮窗的外观。
 * 读写经公共 [Storage]，与其它桌面偏好同库。
 */
object DesktopOverlaySettings {
    /**
     * 设置版本号：每次由设置页写入后递增，供叠加层渲染侧观察变更。
     *
     * 渲染侧不逐帧读设置（读存储是阻塞调用），只在版本号变化时重取快照。
     */
    val revision = androidx.compose.runtime.mutableStateOf(0)

    /** 通知渲染侧设置已变更。 */
    fun notifyChanged() {
        revision.value = revision.value + 1
    }

    private const val KEY_ENABLED = "desktop_overlay_enabled"
    private const val KEY_DANMAKU_FONT_SIZE = "desktop_overlay_danmaku_font_size"
    private const val KEY_DANMAKU_SPEED = "desktop_overlay_danmaku_speed"
    private const val KEY_DANMAKU_AREA_PERCENT = "desktop_overlay_danmaku_area_percent"
    private const val KEY_MEDIA_AUTO_COLLAPSE_MS = "desktop_overlay_media_auto_collapse_ms"

    private const val KEY_BALANCE_ENABLED = "desktop_overlay_balance_enabled"
    private const val KEY_BALANCE_TOKEN = "desktop_overlay_balance_token"
    private const val KEY_BALANCE_INTERVAL_MS = "desktop_overlay_balance_interval_ms"

    private const val KEY_LOGI_ENABLED = "desktop_overlay_logi_enabled"
    private const val KEY_LOGI_HIDE_DISCONNECTED = "desktop_overlay_logi_hide_disconnected"
    private const val KEY_LOGI_DEVICE_NAMES = "desktop_overlay_logi_device_names"

    private const val KEY_KEYBOARD_ENABLED = "desktop_overlay_keyboard_enabled"
    private const val KEY_KEYBOARD_MAPPINGS = "desktop_overlay_keyboard_mappings"

    private const val KEY_CLOCK_ENABLED = "desktop_overlay_clock_enabled"
    private const val KEY_CLOCK_SHOW_SECONDS = "desktop_overlay_clock_show_seconds"
    private const val KEY_CLOCK_USE_24_HOUR = "desktop_overlay_clock_use_24_hour"

    /** 叠加层是否显示。 */
    var enabled: Boolean
        get() = Storage.getBoolean(KEY_ENABLED, true)
        set(value) = Storage.putBoolean(KEY_ENABLED, value)

    /** 弹幕字号（dp）：与设置页滑块同范围，避免写入时被夹导致滑块回弹。 */
    var danmakuFontSize: Float
        get() = Storage.getFloat(KEY_DANMAKU_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = Storage.putFloat(KEY_DANMAKU_FONT_SIZE, value.coerceIn(9f, 54f))

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

    // ======== DeepSeek 余额 ========

    /** 余额浮窗是否显示；默认关闭，填入凭据后由用户开启。 */
    var balanceEnabled: Boolean
        get() = Storage.getBoolean(KEY_BALANCE_ENABLED, DEFAULT_BALANCE_ENABLED)
        set(value) = Storage.putBoolean(KEY_BALANCE_ENABLED, value)

    /** DeepSeek API 凭据。 */
    var balanceApiToken: String
        get() = Storage.getString(KEY_BALANCE_TOKEN, "")
        set(value) = Storage.putString(KEY_BALANCE_TOKEN, value)

    /** 余额轮询间隔（毫秒）。 */
    var balanceIntervalMs: Long
        get() = Storage.getInt(KEY_BALANCE_INTERVAL_MS, DEFAULT_BALANCE_INTERVAL_MS.toInt()).toLong()
        set(value) = Storage.putInt(KEY_BALANCE_INTERVAL_MS, value.toInt())

    // ======== 罗技电池 ========

    /** 罗技电池浮窗是否显示。 */
    var logiEnabled: Boolean
        get() = Storage.getBoolean(KEY_LOGI_ENABLED, DEFAULT_LOGI_ENABLED)
        set(value) = Storage.putBoolean(KEY_LOGI_ENABLED, value)

    /** 离线设备是否隐藏。 */
    var logiHideWhenDisconnected: Boolean
        get() = Storage.getBoolean(KEY_LOGI_HIDE_DISCONNECTED, true)
        set(value) = Storage.putBoolean(KEY_LOGI_HIDE_DISCONNECTED, value)

    /** 罗技设备自定义名：键为 `VID:PID:槽位`，值为用户改名；重启后保留。 */
    var logiDeviceNameOverrides: Map<String, String>
        get() = LogiDeviceNameStore.decode(Storage.getString(KEY_LOGI_DEVICE_NAMES, ""))
        set(value) = Storage.putString(KEY_LOGI_DEVICE_NAMES, LogiDeviceNameStore.encode(value))

    // ======== 键盘监控 ========

    /** 键盘浮窗是否显示，同时决定全局键盘钩子的安装。 */
    var keyboardEnabled: Boolean
        get() = Storage.getBoolean(KEY_KEYBOARD_ENABLED, DEFAULT_KEYBOARD_ENABLED)
        set(value) = Storage.putBoolean(KEY_KEYBOARD_ENABLED, value)

    /** 快捷键映射规则（JSON 序列化）。 */
    var keyboardMappings: List<KeyMapping>
        get() = KeyboardMappingStore.decode(Storage.getString(KEY_KEYBOARD_MAPPINGS, ""))
        set(value) = Storage.putString(KEY_KEYBOARD_MAPPINGS, KeyboardMappingStore.encode(value))

    // ======== 时间浮窗 ========

    /** 时间浮窗是否显示。 */
    var clockEnabled: Boolean
        get() = Storage.getBoolean(KEY_CLOCK_ENABLED, DEFAULT_CLOCK_ENABLED)
        set(value) = Storage.putBoolean(KEY_CLOCK_ENABLED, value)

    /** 是否显示秒。 */
    var clockShowSeconds: Boolean
        get() = Storage.getBoolean(KEY_CLOCK_SHOW_SECONDS, true)
        set(value) = Storage.putBoolean(KEY_CLOCK_SHOW_SECONDS, value)

    /** 是否 24 小时制。 */
    var clockUse24Hour: Boolean
        get() = Storage.getBoolean(KEY_CLOCK_USE_24_HOUR, true)
        set(value) = Storage.putBoolean(KEY_CLOCK_USE_24_HOUR, value)

    /** 余额浮窗锚点。 */
    val balanceAnchor = Anchor("desktop_overlay_balance", defaultX = 1f, defaultY = 99f, defaultScale = 0.75f)

    /** 罗技电池浮窗锚点。 */
    val logiAnchor = Anchor("desktop_overlay_logi", defaultX = 90f, defaultY = 99f, defaultOutlineWidth = 4f)

    /** 键盘浮窗锚点。 */
    val keyboardAnchor = Anchor("desktop_overlay_keyboard")

    /** 时间浮窗锚点。 */
    val clockAnchor =
        Anchor("desktop_overlay_clock", defaultX = 49f, defaultY = 99f, defaultScale = 0.5f, defaultOutlineWidth = 3.25f)

    /**
     * 单个元素的位置与外观读写。
     *
     * 键为「前缀 + 字段名」，各元素的取值互不影响。
     * 默认值按元素分别给出：各浮窗的实际摆放位置不同，共用一套默认值会全部重叠。
     */
    class Anchor(
        private val prefix: String,
        private val defaultX: Float = DEFAULT_X_PERCENT,
        private val defaultY: Float = DEFAULT_Y_PERCENT,
        private val defaultScale: Float = 1f,
        private val defaultOutlineWidth: Float = DEFAULT_OUTLINE_WIDTH,
        private val defaultColor: String = DEFAULT_COLOR,
    ) {
        /** 水平位置（占屏宽百分比）。 */
        var xPercent: Float
            get() = Storage.getFloat(prefix + "_x_percent", defaultX)
            set(value) = Storage.putFloat(prefix + "_x_percent", value.coerceIn(0f, 100f))

        /** 垂直位置（占屏高百分比）。 */
        var yPercent: Float
            get() = Storage.getFloat(prefix + "_y_percent", defaultY)
            set(value) = Storage.putFloat(prefix + "_y_percent", value.coerceIn(0f, 100f))

        /** 缩放倍数。 */
        var scale: Float
            get() = Storage.getFloat(prefix + "_scale", defaultScale)
            set(value) = Storage.putFloat(prefix + "_scale", value.coerceIn(0.5f, 3f))

        /** 文本颜色，`#RRGGBB`。 */
        var colorHex: String
            get() = Storage.getString(prefix + "_color", defaultColor)
            set(value) = Storage.putString(prefix + "_color", value)

        /** 文本描边宽度（像素）。 */
        var outlineWidth: Float
            get() = Storage.getFloat(prefix + "_outline_width", defaultOutlineWidth)
            set(value) = Storage.putFloat(prefix + "_outline_width", value.coerceIn(0f, 8f))

        /** 当前锚点快照。 */
        fun snapshot(enabled: Boolean): OverlayAnchor =
            OverlayAnchor(
                enabled = enabled,
                xPercent = xPercent,
                yPercent = yPercent,
                scale = scale,
                colorHex = colorHex,
                outlineWidth = outlineWidth,
            )
    }

    /**
     * 默认值：与 C# `GeneralSettingsService.Danmaku` 一致（不是 `DanmakuStyleSettings` 的类内默认值）。
     *
     * `DanmakuSpeed = 3` → 180 px/s；`DanmakuDisplayAreaPercent = 100`。
     * 字号默认 27px（基准 36px × 75%）。
     */
    private const val DEFAULT_FONT_SIZE = 27f
    private const val DEFAULT_SPEED = 180f
    private const val DEFAULT_AREA_PERCENT = 100f
    private const val DEFAULT_MEDIA_AUTO_COLLAPSE_MS = 5_000L

    /** 余额默认关闭：需用户填入凭据后手动开启。 */
    private const val DEFAULT_BALANCE_ENABLED = false
    private const val DEFAULT_BALANCE_INTERVAL_MS = 300_000L

    private const val DEFAULT_LOGI_ENABLED = true
    private const val DEFAULT_KEYBOARD_ENABLED = true
    private const val DEFAULT_CLOCK_ENABLED = true

    /** 未单独给出默认值的元素（键盘）回落到这些值。 */
    private const val DEFAULT_X_PERCENT = 50f
    private const val DEFAULT_Y_PERCENT = 10f
    private const val DEFAULT_COLOR = "#FFFFFF"
    private const val DEFAULT_OUTLINE_WIDTH = 2f
}
