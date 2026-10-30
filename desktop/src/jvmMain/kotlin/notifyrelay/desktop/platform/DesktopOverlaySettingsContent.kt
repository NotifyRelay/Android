package notifyrelay.desktop.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.shared.ui.common.MiuixSpinnerPreference
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 叠加层设置内容（桌面端专有）。
 *
 * 直接读写 [DesktopOverlaySettings]，改动立即对叠加层渲染生效
 * （弹幕字号/速度/区域在下一条弹幕分配轨道时读取）。
 */
@Composable
fun DesktopOverlaySettingsContent() {
    var enabled by remember { mutableStateOf(DesktopOverlaySettings.enabled) }
    var fontSize by remember { mutableFloatStateOf(DesktopOverlaySettings.danmakuFontSize) }
    var speed by remember { mutableFloatStateOf(DesktopOverlaySettings.danmakuSpeed) }
    var areaPercent by remember { mutableFloatStateOf(DesktopOverlaySettings.danmakuAreaPercent) }
    var collapseMs by remember { mutableLongStateOf(DesktopOverlaySettings.mediaAutoCollapseMs) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "显示叠加层",
            summary = "在屏幕顶部绘制媒体卡、超级岛与弹幕；关闭后不创建叠加层窗口",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.enabled = it
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        MiuixSpinnerPreference(
            title = "弹幕字号",
            summary = "当前 " + fontSize.toInt() + "px（基准 36px × 50%），字号越大轨道越少",
            items = DesktopOverlaySettings.FONT_SIZE_OPTIONS.map { it.toString() + "px" },
            selectedIndex =
                DesktopOverlaySettings.FONT_SIZE_OPTIONS
                    .indexOfFirst { it.toFloat() == fontSize }
                    .coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                val value = DesktopOverlaySettings.FONT_SIZE_OPTIONS.getOrNull(index)?.toFloat() ?: return@MiuixSpinnerPreference
                fontSize = value
                DesktopOverlaySettings.danmakuFontSize = value
            },
        )

        MiuixSpinnerPreference(
            title = "弹幕速度",
            summary = "当前 " + speed.toInt() + " px/s",
            items = DesktopOverlaySettings.SPEED_OPTIONS.map { it.toString() },
            selectedIndex =
                DesktopOverlaySettings.SPEED_OPTIONS
                    .indexOfFirst { it.toFloat() == speed }
                    .coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                val value = DesktopOverlaySettings.SPEED_OPTIONS.getOrNull(index)?.toFloat() ?: return@MiuixSpinnerPreference
                speed = value
                DesktopOverlaySettings.danmakuSpeed = value
            },
        )

        MiuixSpinnerPreference(
            title = "弹幕显示区域",
            summary = "占用屏幕高度的百分比，当前 " + areaPercent.toInt() + "%",
            items = DesktopOverlaySettings.AREA_PERCENT_OPTIONS.map { it.toString() + "%" },
            selectedIndex =
                DesktopOverlaySettings.AREA_PERCENT_OPTIONS
                    .indexOfFirst { it.toFloat() == areaPercent }
                    .coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                val value = DesktopOverlaySettings.AREA_PERCENT_OPTIONS.getOrNull(index)?.toFloat() ?: return@MiuixSpinnerPreference
                areaPercent = value
                DesktopOverlaySettings.danmakuAreaPercent = value
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        DesktopDanmakuTestEntry()

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        MiuixSpinnerPreference(
            title = "媒体卡自动收起",
            summary = "媒体卡展开后经过该时长收起为胶囊；换曲时重新展开",
            items = DesktopOverlaySettings.MEDIA_AUTO_COLLAPSE_OPTIONS.map { (it / 1000).toString() + " 秒" },
            selectedIndex =
                DesktopOverlaySettings.MEDIA_AUTO_COLLAPSE_OPTIONS
                    .indexOfFirst { it.toLong() == collapseMs }
                    .coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                val value = DesktopOverlaySettings.MEDIA_AUTO_COLLAPSE_OPTIONS.getOrNull(index)?.toLong() ?: return@MiuixSpinnerPreference
                collapseMs = value
                DesktopOverlaySettings.mediaAutoCollapseMs = value
            },
        )
    }
}
