package notifyrelay.desktop.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

/**
 * 叠加层设置主页（桌面端专有）：总开关与各子页入口。
 *
 * 数值项一律用滑块，不再用下拉。
 */
@Composable
fun DesktopOverlaySettingsContent() {
    val navigator = LocalNavigator.current
    var enabled by remember { mutableStateOf(DesktopOverlaySettings.enabled) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "显示叠加层",
            summary = "在屏幕顶部居中绘制媒体卡、超级岛、通知卡与状态元素；关闭后不创建叠加层窗口",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.enabled = it
            },
        )

        var interactiveEnabled by remember { mutableStateOf(DesktopOverlaySettings.interactiveEnabled) }
        SwitchPreference(
            title = "允许点击交互",
            summary = "鼠标悬停在媒体卡、超级岛或通知图标上时可点击；离开区域自动恢复点击穿透",
            checked = interactiveEnabled,
            onCheckedChange = {
                interactiveEnabled = it
                DesktopOverlaySettings.interactiveEnabled = it
                DesktopOverlaySettings.notifyChanged()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        ArrowPreference(
            title = "通知与媒体卡",
            summary = "测试通知卡片与媒体卡收起、操作按钮、频谱",
            onClick = { navigator.push(Route.SettingsOverlayNotifications) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "DeepSeek 余额",
            summary = "凭据、轮询间隔与外观",
            onClick = { navigator.push(Route.SettingsOverlayBalance) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "罗技电池",
            summary = "设备电量显示与离线处理",
            onClick = { navigator.push(Route.SettingsOverlayLogi) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "键盘监控",
            summary = "按键显示、快捷键映射与外观",
            onClick = { navigator.push(Route.SettingsOverlayKeyboard) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "时间元素",
            summary = "显示格式与外观",
            onClick = { navigator.push(Route.SettingsOverlayClock) },
        )
    }
}

/** 通知与媒体卡子页。 */
@Composable
fun DesktopOverlayNotificationContent() {
    var collapseSeconds by remember { mutableFloatStateOf(DesktopOverlaySettings.mediaAutoCollapseMs / 1000f) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DesktopNotificationTestEntry()

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        OverlaySlider(
            title = "媒体卡自动收起",
            summary = "媒体卡展开后经过该时长收起为胶囊；换曲时重新展开（不显示操作按钮）",
            value = collapseSeconds,
            range = 2f..15f,
            step = 1f,
            format = { it.roundToInt().toString() + " 秒" },
            onValueChangeFinished = {
                collapseSeconds = it
                DesktopOverlaySettings.mediaAutoCollapseMs = (it * 1000).toLong()
                DesktopOverlaySettings.notifyChanged()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        var mediaControlsEnabled by remember { mutableStateOf(DesktopOverlaySettings.mediaControlsEnabled) }
        SwitchPreference(
            title = "显示媒体操作按钮",
            summary = "媒体卡展开时显示播放/暂停、上一首、下一首按钮；换曲自动展开时不显示，需点击胶囊展开",
            checked = mediaControlsEnabled,
            onCheckedChange = {
                mediaControlsEnabled = it
                DesktopOverlaySettings.mediaControlsEnabled = it
                DesktopOverlaySettings.notifyChanged()
            },
        )

        var spectrumEnabled by remember { mutableStateOf(DesktopOverlaySettings.spectrumEnabled) }
        SwitchPreference(
            title = "显示频谱",
            summary = "媒体卡收起为胶囊后右端显示频谱条动画",
            checked = spectrumEnabled,
            onCheckedChange = {
                spectrumEnabled = it
                DesktopOverlaySettings.spectrumEnabled = it
                DesktopOverlaySettings.notifyChanged()
            },
        )
    }
}

/**
 * 数值型设置的公共滑块。
 *
 * 拖动期间用本地值跟手、不做吸附，松手时才吸附并提交：
 * 若在拖动回调里吸附，步进大于 1 时大多数位置吸附后仍是原值，受控滑块会回弹，表现为「拖不动」。
 *
 * 回调拆分：
 * - [onValueChange] 在拖动过程中每帧调用，用于实时更新 UI；默认空实现。
 * - [onValueChangeFinished] 在松手时调用吸附后的值，用于落盘持久化。
 *
 * [format] 依据当前显示值生成文本；[steps] 为除两端外的离散值个数，Miuix 要求非负。
 */
@Composable
internal fun OverlaySlider(
    title: String,
    summary: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: (Float) -> Unit,
) {
    val realStep = if (step > 0f) step else 1f
    var local by remember { mutableFloatStateOf(value) }

    // 外部值变化（如别的入口写入设置）时同步本地显示值
    LaunchedEffect(value) { local = value }

    SliderPreference(
        title = title,
        summary = summary,
        valueText = format(local),
        value = local,
        valueRange = range,
        steps = (((range.endInclusive - range.start) / realStep).roundToInt() - 1).coerceAtLeast(0),
        enabled = enabled,
        onValueChange = {
            local = it
            onValueChange(it)
        },
        onValueChangeFinished = {
            val snapped = snapToStep(local, range, realStep)
            local = snapped
            onValueChange(snapped)
            onValueChangeFinished(snapped)
        },
    )
}

/** 把滑块原始值吸附到步进网格并夹在范围内。 */
internal fun snapToStep(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val steps = ((clamped - range.start) / step).roundToInt()
    return (range.start + steps * step).coerceIn(range.start, range.endInclusive)
}
