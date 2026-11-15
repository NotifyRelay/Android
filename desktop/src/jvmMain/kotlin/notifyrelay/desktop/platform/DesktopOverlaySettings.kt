package notifyrelay.desktop.platform

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import notifyrelay.data.Storage
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.OverlayStyle

/**
 * 桌面端叠加层设置。
 *
 * 仅桌面生效：控制叠加层是否显示、通知卡片与媒体卡行为、各类状态元素的开关与外观。
 * 状态元素由叠加层按固定列排布，这里不保存位置。读写经公共 [Storage]，与其它桌面偏好同库。
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

    private const val KEY_HIDE_FULLSCREEN = "desktop_overlay_hide_fullscreen"

    private const val KEY_INTERACTIVE_ENABLED = "desktop_overlay_interactive_enabled"

    private const val KEY_SPECTRUM_ENABLED = "desktop_overlay_spectrum_enabled"

    private const val KEY_MEDIA_CONTROLS_ENABLED = "desktop_overlay_media_controls_enabled"

    /** 叠加层是否显示。 */
    var enabled: Boolean
        get() = Storage.getBoolean(KEY_ENABLED, true)
        set(value) = Storage.putBoolean(KEY_ENABLED, value)

    /** 前台全屏窗口时隐藏叠加层；默认关闭，保持现有行为。 */
    var hideFullscreen: Boolean
        get() = Storage.getBoolean(KEY_HIDE_FULLSCREEN, false)
        set(value) = Storage.putBoolean(KEY_HIDE_FULLSCREEN, value)

    /** 媒体卡与通知图标是否可点击交互；默认开启。 */
    var interactiveEnabled: Boolean
        get() = Storage.getBoolean(KEY_INTERACTIVE_ENABLED, true)
        set(value) = Storage.putBoolean(KEY_INTERACTIVE_ENABLED, value)

    /** 媒体卡右侧显示频谱条；默认关闭。 */
    var spectrumEnabled: Boolean
        get() = Storage.getBoolean(KEY_SPECTRUM_ENABLED, false)
        set(value) = Storage.putBoolean(KEY_SPECTRUM_ENABLED, value)

    /** 媒体卡展开时显示操作按钮（播放/暂停、上一首、下一首）；仅用户点开的展开生效，换曲自动展开不显示。默认开启。 */
    var mediaControlsEnabled: Boolean
        get() = Storage.getBoolean(KEY_MEDIA_CONTROLS_ENABLED, true)
        set(value) = Storage.putBoolean(KEY_MEDIA_CONTROLS_ENABLED, value)

    /** 媒体卡展开后自动收起时长（毫秒）。 */
    var mediaAutoCollapseMs: Long
        get() = Storage.getInt(KEY_MEDIA_AUTO_COLLAPSE_MS, DEFAULT_MEDIA_AUTO_COLLAPSE_MS.toInt()).toLong()
        set(value) = Storage.putInt(KEY_MEDIA_AUTO_COLLAPSE_MS, value.toInt())

    // ======== DeepSeek 余额 ========

    /** 余额元素是否显示；默认关闭，填入凭据后由用户开启。 */
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

    /** 罗技电池岛条目是否显示。 */
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

    /** 键盘元素是否显示，同时决定全局键盘钩子的安装。 */
    var keyboardEnabled: Boolean
        get() = Storage.getBoolean(KEY_KEYBOARD_ENABLED, DEFAULT_KEYBOARD_ENABLED)
        set(value) = Storage.putBoolean(KEY_KEYBOARD_ENABLED, value)

    /** 快捷键映射规则（JSON 序列化）。 */
    var keyboardMappings: List<KeyMapping>
        get() = KeyboardMappingStore.decode(Storage.getString(KEY_KEYBOARD_MAPPINGS, ""))
        set(value) = Storage.putString(KEY_KEYBOARD_MAPPINGS, KeyboardMappingStore.encode(value))

    // ======== 时间元素 ========

    /** 时间元素是否显示。 */
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

    /** 余额元素外观。 */
    val balanceStyle = Style("desktop_overlay_balance", defaultScale = 0.75f)

    /** 键盘元素外观。 */
    val keyboardStyle = Style("desktop_overlay_keyboard")

    /** 时间元素外观。 */
    val clockStyle = Style("desktop_overlay_clock", defaultScale = 0.5f, defaultOutlineWidth = 3.25f)

    /**
     * 单个状态元素的外观读写。
     *
     * 各字段以 Compose mutableState 为背景态：拖动滑块时直接写入背景态，
     * 渲染侧读取即触发重组；松手时经 [persist] 落盘。
     * 键为「前缀 + 字段名」，各元素的取值互不影响。
     */
    class Style(
        private val prefix: String,
        private val defaultScale: Float = 1f,
        private val defaultOutlineWidth: Float = DEFAULT_OUTLINE_WIDTH,
        private val defaultColor: String = DEFAULT_COLOR,
    ) {
        private val _scale = mutableFloatStateOf(Storage.getFloat(prefix + "_scale", defaultScale))
        private val _colorHex = mutableStateOf(Storage.getString(prefix + "_color", defaultColor))
        private val _outlineWidth = mutableFloatStateOf(Storage.getFloat(prefix + "_outline_width", defaultOutlineWidth))

        /** 缩放倍数。 */
        var scale: Float
            get() = _scale.value
            set(value) {
                _scale.value = value.coerceIn(0.5f, 4f)
            }

        /** 文本颜色，`#RRGGBB`。 */
        var colorHex: String
            get() = _colorHex.value
            set(value) {
                _colorHex.value = value
            }

        /** 文本描边宽度（像素）。 */
        var outlineWidth: Float
            get() = _outlineWidth.value
            set(value) {
                _outlineWidth.value = value.coerceIn(0f, 8f)
            }

        /** 把当前背景态落盘；拖动结束后调用。 */
        fun persist() {
            Storage.putFloat(prefix + "_scale", _scale.value)
            Storage.putString(prefix + "_color", _colorHex.value)
            Storage.putFloat(prefix + "_outline_width", _outlineWidth.value)
        }

        /** 当前外观快照。 */
        fun snapshot(enabled: Boolean): OverlayStyle =
            OverlayStyle(
                enabled = enabled,
                scale = scale,
                colorHex = colorHex,
                outlineWidth = outlineWidth,
            )
    }

    private const val DEFAULT_MEDIA_AUTO_COLLAPSE_MS = 3_000L

    /** 余额默认关闭：需用户填入凭据后手动开启。 */
    private const val DEFAULT_BALANCE_ENABLED = false
    private const val DEFAULT_BALANCE_INTERVAL_MS = 300_000L

    private const val DEFAULT_LOGI_ENABLED = true
    private const val DEFAULT_KEYBOARD_ENABLED = true
    private const val DEFAULT_CLOCK_ENABLED = true

    private const val DEFAULT_COLOR = "#FFFFFF"
    private const val DEFAULT_OUTLINE_WIDTH = 2f
}
