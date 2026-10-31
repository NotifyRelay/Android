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
            summary = "在屏幕顶部绘制媒体卡、超级岛、弹幕与各浮窗；关闭后不创建叠加层窗口",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.enabled = it
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        ArrowPreference(
            title = "弹幕与媒体卡",
            summary = "字号、速度、显示区域、测试弹幕与媒体卡收起",
            onClick = { navigator.push(Route.SettingsOverlayDanmaku) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "DeepSeek 余额",
            summary = "凭据、轮询间隔与浮窗位置",
            onClick = { navigator.push(Route.SettingsOverlayBalance) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "罗技电池",
            summary = "设备电量显示与浮窗位置",
            onClick = { navigator.push(Route.SettingsOverlayLogi) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "键盘监控",
            summary = "按键显示、快捷键映射与浮窗位置",
            onClick = { navigator.push(Route.SettingsOverlayKeyboard) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
        ArrowPreference(
            title = "时间浮窗",
            summary = "显示格式与浮窗位置",
            onClick = { navigator.push(Route.SettingsOverlayClock) },
        )
    }
}

/** 弹幕与媒体卡子页。 */
@Composable
fun DesktopOverlayDanmakuContent() {
    var fontSize by remember { mutableFloatStateOf(DesktopOverlaySettings.danmakuFontSize) }
    var speed by remember { mutableFloatStateOf(DesktopOverlaySettings.danmakuSpeed) }
    var areaPercent by remember { mutableFloatStateOf(DesktopOverlaySettings.danmakuAreaPercent) }
    var collapseSeconds by remember { mutableFloatStateOf(DesktopOverlaySettings.mediaAutoCollapseMs / 1000f) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OverlaySlider(
            title = "弹幕字号",
            summary = "基准 36px × 百分比；字号越大可用轨道越少",
            value = fontSize,
            range = 9f..54f,
            step = 1f,
            format = { it.roundToInt().toString() + "px" },
            onValueChange = {
                fontSize = it
                DesktopOverlaySettings.danmakuFontSize = it
            },
        )

        OverlaySlider(
            title = "弹幕速度",
            summary = "数值越大滚动越快",
            value = speed,
            range = 60f..540f,
            step = 10f,
            format = { it.roundToInt().toString() + " px/s" },
            onValueChange = {
                speed = it
                DesktopOverlaySettings.danmakuSpeed = it
            },
        )

        OverlaySlider(
            title = "弹幕显示区域",
            summary = "占用屏幕高度的百分比，剩余部分不分配轨道",
            value = areaPercent,
            range = 30f..100f,
            step = 1f,
            format = { it.roundToInt().toString() + "%" },
            onValueChange = {
                areaPercent = it
                DesktopOverlaySettings.danmakuAreaPercent = it
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        DesktopDanmakuTestEntry()

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        OverlaySlider(
            title = "媒体卡自动收起",
            summary = "媒体卡展开后经过该时长收起为胶囊；换曲时重新展开",
            value = collapseSeconds,
            range = 3f..20f,
            step = 1f,
            format = { it.roundToInt().toString() + " 秒" },
            onValueChange = {
                collapseSeconds = it
                DesktopOverlaySettings.mediaAutoCollapseMs = (it * 1000).toLong()
            },
        )
    }
}

/**
 * 数值型设置的公共滑块。
 *
 * 拖动期间用本地值跟手、不做吸附，松手时才吸附并提交：
 * 若在拖动回调里吸附，步进大于 1 时大多数位置吸附后仍是原值，受控滑块会回弹，表现为「拖不动」。
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
    onValueChange: (Float) -> Unit,
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
        onValueChange = { local = it },
        onValueChangeFinished = {
            val snapped = snapToStep(local, range, realStep)
            local = snapped
            if (snapped != value) onValueChange(snapped)
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
