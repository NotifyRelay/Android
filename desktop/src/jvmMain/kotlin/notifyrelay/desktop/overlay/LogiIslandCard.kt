package notifyrelay.desktop.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.floating.common.SuperIslandExpandableCard
import github.xzynine.superislandui.floating.common.superIslandCardColor
import github.xzynine.superislandui.floating.smallisland.compose.collapsedCapsuleColor
import kotlinx.coroutines.delay
import notifyrelay.base.util.BatteryIconConverter
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.shared.ui.common.deviceIconFontFamily
import top.yukonga.miuix.kmp.basic.Text

/** 罗技汇总岛展开后的自动收起时长。 */
private const val LOGI_EXPAND_MS = 3_000L

/** 展开态的卡片最大宽度，与超级岛卡同列宽。 */
private val LOGI_CARD_MAX_WIDTH = 420.dp

/** 折叠胶囊的最大宽度。 */
private val LOGI_CAPSULE_MAX_WIDTH = 320.dp

/** 设备条目之间的横向间距。 */
private val LOGI_ITEM_GAP = 10.dp

/** 展开态内边距。 */
private val LOGI_EXPANDED_PADDING_H = 12.dp
private val LOGI_EXPANDED_PADDING_V = 8.dp

/** 折叠态内边距。 */
private val LOGI_CAPSULE_PADDING_H = 12.dp
private val LOGI_CAPSULE_PADDING_V = 6.dp

/**
 * 罗技电池汇总岛。
 *
 * 所有罗技设备合并为一条与超级岛同形的卡片：折叠态为胶囊，电量变动时展开 [LOGI_EXPAND_MS]，
 * 展开态横向排列各设备的电量图标与百分比。无设备时不绘制。
 *
 * 设备集合由 [DesktopOverlaySettings.logiEnabled] 与隐藏离线开关决定。
 *
 * @param interactiveEnabled 开启时点击胶囊可手动展开
 */
@Composable
internal fun LogiIslandCard(interactiveEnabled: Boolean = false) {
    val revision = DesktopOverlaySettings.revision.value
    val enabled = remember(revision) { DesktopOverlaySettings.logiEnabled }
    val hideDisconnected = remember(revision) { DesktopOverlaySettings.logiHideWhenDisconnected }
    if (!enabled) return

    val devices =
        DesktopOverlayStore.logiDevices.value.filter { device ->
            !hideDisconnected || device.online
        }
    if (devices.isEmpty()) return

    // 电量、充电状态或在线状态变化均视为一次更新：重新展开并重置计时
    val batteryKey =
        devices.joinToString("|") { it.deviceId + ":" + it.batteryPercent + ":" + it.statusRaw + ":" + it.online }
    var expanded by remember { mutableStateOf(false) }
    var expandGeneration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(batteryKey, expandGeneration) {
        expanded = true
        delay(LOGI_EXPAND_MS)
        expanded = false
    }

    SuperIslandExpandableCard(
        expanded = expanded,
        expandedBackgroundColor = superIslandCardColor(isOverlapping = false),
        collapsedBackgroundColor = collapsedCapsuleColor(isOverlapping = false),
        expandedContent = {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = LOGI_CARD_MAX_WIDTH)
                        .padding(horizontal = LOGI_EXPANDED_PADDING_H, vertical = LOGI_EXPANDED_PADDING_V),
                horizontalArrangement = Arrangement.spacedBy(LOGI_ITEM_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                devices.forEach { device -> LogiDeviceItem(device) }
            }
        },
        collapsedContent = {
            Box(
                Modifier.then(
                    if (interactiveEnabled) {
                        Modifier.clickable { expandGeneration++ }
                    } else {
                        Modifier
                    },
                ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .widthIn(max = LOGI_CAPSULE_MAX_WIDTH)
                            .padding(horizontal = LOGI_CAPSULE_PADDING_H, vertical = LOGI_CAPSULE_PADDING_V),
                    horizontalArrangement = Arrangement.spacedBy(LOGI_ITEM_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    devices.forEach { device -> LogiDeviceItem(device) }
                }
            }
        },
    )
}

/** 单台设备的电量图标与百分比；无电量数据时只显示设备名。 */
@Composable
private fun LogiDeviceItem(device: LogiBatteryDevice) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = logiSummaryGlyph(device),
            color = logiSummaryColor(device),
            fontSize = 16.sp,
            fontFamily = deviceIconFontFamily(),
        )
        Text(
            text = if (device.batteryPercent >= 0) device.batteryPercent.toString() + "%" else device.deviceName,
            color = Color(0xE6FFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 罗技设备电量图标；无电量数据时用满格未充电字形。 */
internal fun logiSummaryGlyph(device: LogiBatteryDevice): String =
    if (device.hasBattery && device.batteryPercent >= 0) {
        BatteryIconConverter.getBatteryIcon(device.batteryPercent, if (device.isCharging) '1' else '0')
    } else {
        BatteryIconConverter.getBatteryIcon(100, '0')
    }

/** 罗技设备电量图标颜色：无电量固定灰色，其余按电量分段着色。 */
internal fun logiSummaryColor(device: LogiBatteryDevice): Color =
    if (device.hasBattery && device.batteryPercent >= 0) {
        BatteryIconConverter.getBatteryColor(device.batteryPercent)
    } else {
        Color(0xFF808080)
    }
