package notifyrelay.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import notifyrelay.shared.ui.common.DebugFlags
import notifyrelay.shared.ui.port.LocalPreferencePort
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 桌面端专属的开发者选项。
 *
 * 由共享开发者选项页经槽位注入，Android 端不显示这些项。
 */
@Composable
fun DesktopDeveloperContent() {
    val preferencePort = LocalPreferencePort.current
    var regionDebug by remember { mutableStateOf(DebugFlags.OVERLAY_REGION_DEBUG.value) }

    SwitchPreference(
        title = "显示可点击区域",
        summary = "以红色矩形绘出叠加层的可交互区域，并以外框标出叠加层窗口范围；不改变点击穿透",
        checked = regionDebug,
        onCheckedChange = { enabled ->
            regionDebug = enabled
            DebugFlags.setOverlayRegionDebug(enabled) { key, value -> preferencePort?.putBoolean(key, value) }
        },
    )
}
