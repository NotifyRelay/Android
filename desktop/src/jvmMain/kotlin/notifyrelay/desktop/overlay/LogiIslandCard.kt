package notifyrelay.desktop.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.AutoScrollText
import github.xzynine.superislandui.floating.common.SuperIslandExpandableCard
import github.xzynine.superislandui.floating.common.superIslandCardColor
import github.xzynine.superislandui.floating.smallisland.compose.collapsedCapsuleColor
import kotlinx.coroutines.delay
import notifyrelay.base.util.BatteryIconConverter
import notifyrelay.desktop.platform.DesktopOverlaySettings
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.shared.ui.common.deviceIconFontFamily
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.roundToInt

/** 罗技汇总岛展开后的自动收起时长。 */
private const val LOGI_EXPAND_MS = 3_000L

/** 展开态的卡片最大宽度，与超级岛卡同列宽。 */
private val LOGI_CARD_MAX_WIDTH = 420.dp

/** 折叠胶囊的最大宽度。 */
private val LOGI_CAPSULE_MAX_WIDTH = 320.dp

/** 设备条目之间的横向间距。 */
private val LOGI_ITEM_GAP = 14.dp

/** 展开态内边距。 */
private val LOGI_EXPANDED_PADDING_H = 12.dp
private val LOGI_EXPANDED_PADDING_V = 10.dp

/** 折叠态内边距。 */
private val LOGI_CAPSULE_PADDING_H = 12.dp
private val LOGI_CAPSULE_PADDING_V = 6.dp

/** 电量图标字号：展开态更大。 */
private const val LOGI_EXPANDED_GLYPH_SP = 20f
private const val LOGI_CAPSULE_GLYPH_SP = 15f

/** 设备名最大宽度：长名以省略号截断。 */
private val LOGI_NAME_MAX_WIDTH = 132.dp

/** 收起态设备名宽度上限：超出后由滚动组件滚动显示。 */
private val LOGI_CAPSULE_NAME_WIDTH = 96.dp

/** 收起态滚动文本的滚动速度与端点停留时长。 */
private const val LOGI_SCROLL_SPEED = 60f
private const val LOGI_SCROLL_END_PAUSE_MS = 800

/** 汇总岛在可交互区域集合中的键。 */
private const val LOGI_REGION_KEY = "logi-island"

/**
 * 罗技电池汇总岛。
 *
 * 所有罗技设备合并为一条与超级岛同形的卡片。折叠态只给电量图标与百分比，
 * 展开态逐台列出设备名、电量图标、百分比与充电状态；电量、充电状态或在线状态变化时展开
 * [LOGI_EXPAND_MS]，无可用设备时不绘制。
 *
 * 设备集合由 [DesktopOverlaySettings.logiEnabled] 与隐藏离线开关决定。
 *
 * @param interactiveEnabled 开启时点击胶囊可手动展开
 * @param interactiveRegions 可交互区域集合，供原生层切换点击穿透
 */
@Composable
internal fun LogiIslandCard(
    interactiveEnabled: Boolean = false,
    interactiveRegions: androidx.compose.runtime.snapshots.SnapshotStateMap<String, java.awt.Rectangle>? = null,
) {
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
    // 电量变化自动展开一次；点击胶囊手动展开同样走这里，点击展开态则收起
    var expanded by remember { mutableStateOf(false) }
    var expandGeneration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(batteryKey, expandGeneration) {
        expanded = true
        delay(LOGI_EXPAND_MS)
        expanded = false
    }

    // 收起态可点击：登记可交互区域，鼠标移入时关闭点击穿透才能收到点击
    val regionKey = LOGI_REGION_KEY
    val clickable = interactiveEnabled && interactiveRegions != null
    DisposableEffect(regionKey, clickable) {
        onDispose { interactiveRegions?.remove(regionKey) }
    }
    val regionModifier =
        if (clickable) {
            Modifier.onGloballyPositioned { coords ->
                val pos = coords.localToRoot(Offset.Zero)
                val sz = coords.size
                interactiveRegions[regionKey] =
                    java.awt.Rectangle(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        sz.width,
                        sz.height,
                    )
            }
        } else {
            Modifier
        }

    SuperIslandExpandableCard(
        expanded = expanded,
        expandedBackgroundColor = superIslandCardColor(isOverlapping = false),
        collapsedBackgroundColor = collapsedCapsuleColor(isOverlapping = false),
        modifier = regionModifier,
        expandedContent = {
            Row(
                modifier =
                    Modifier
                        .widthIn(max = LOGI_CARD_MAX_WIDTH)
                        .then(
                            if (interactiveEnabled) {
                                Modifier.clickable { expanded = false }
                            } else {
                                Modifier
                            },
                        ).padding(horizontal = LOGI_EXPANDED_PADDING_H, vertical = LOGI_EXPANDED_PADDING_V),
                horizontalArrangement = Arrangement.spacedBy(LOGI_ITEM_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                devices.forEach { device ->
                    ExpandedLogiDeviceItem(device)
                }
            }
        },
        collapsedContent = {
            Box(
                Modifier.then(
                    if (interactiveEnabled) {
                        Modifier.clickable {
                            expandGeneration++
                        }
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
                    devices.forEach { device ->
                        CollapsedLogiDeviceItem(device)
                    }
                }
            }
        },
    )
}

/** 展开态设备条目：设备名在上，电量图标 + 百分比与充电状态在下。 */
@Composable
private fun ExpandedLogiDeviceItem(device: LogiBatteryDevice) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = device.deviceName,
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = LOGI_NAME_MAX_WIDTH),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = logiSummaryGlyph(device),
                color = logiSummaryColor(device),
                fontSize = LOGI_EXPANDED_GLYPH_SP.sp,
                fontFamily = deviceIconFontFamily(),
            )
            Text(
                text = logiPercentLabel(device),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val chargeLabel = logiChargeLabel(device)
            if (chargeLabel.isNotBlank()) {
                Text(
                    text = chargeLabel,
                    color = Color(0xB3FFFFFF),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 折叠态设备条目：设备名与电量图标外显，不显示具体电量值。
 *
 * 设备名超过 [LOGI_CAPSULE_NAME_WIDTH] 时滚动显示，与媒体卡收起胶囊同一处理方式。
 */
@Composable
private fun CollapsedLogiDeviceItem(device: LogiBatteryDevice) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = logiSummaryGlyph(device),
            color = logiSummaryColor(device),
            fontSize = LOGI_CAPSULE_GLYPH_SP.sp,
            fontFamily = deviceIconFontFamily(),
        )
        Box(Modifier.width(LOGI_CAPSULE_NAME_WIDTH)) {
            AutoScrollText(
                text = device.deviceName,
                style = TextStyle(fontSize = 12.sp),
                color = Color(0xE6FFFFFF),
                baseSpeedPxPerSec = LOGI_SCROLL_SPEED,
                pauseMillis = 0,
                endPauseMillis = LOGI_SCROLL_END_PAUSE_MS,
            )
        }
    }
}

/** 电量文案：无电量数据时用占位符，设备名另有位置，不在此重复。 */
private fun logiPercentLabel(device: LogiBatteryDevice): String = if (device.batteryPercent >= 0) device.batteryPercent.toString() + "%" else "--"

/** 充电状态文案；未充电时为空串。 */
private fun logiChargeLabel(device: LogiBatteryDevice): String =
    when {
        !device.online -> "离线"
        device.statusRaw == 1 -> "充电中"
        device.statusRaw == 2 -> "慢充"
        device.statusRaw == 3 -> "已充满"
        device.statusRaw == 4 -> "充电异常"
        else -> ""
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
