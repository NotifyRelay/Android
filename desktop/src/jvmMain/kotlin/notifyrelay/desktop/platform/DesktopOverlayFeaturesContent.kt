package notifyrelay.desktop.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import notifyrelay.desktop.overlay.feature.DesktopOverlayFeatures
import notifyrelay.desktop.overlay.logiBatteryColor
import notifyrelay.desktop.overlay.logiBatteryGlyph
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.overlay.api.LogiBatteryDevice
import notifyrelay.shared.ui.common.deviceIconFontFamily
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

/**
 * 叠加层扩展点子页内容：余额、罗技电池、键盘、时间。
 *
 * 位置/缩放/描边等数值项一律用滑块；开关变化后经
 * [DesktopOverlayFeatures.current] 即时同步各数据源的启停。
 *
 * 以下依次为四个子页的入口函数与公共控件。
 */
@Composable
fun DesktopOverlayBalanceContent() {
    val revision = DesktopOverlaySettings.revision.value
    val features = DesktopOverlayFeatures.current
    val anchor = DesktopOverlaySettings.balanceAnchor

    var enabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.balanceEnabled) }
    var token by remember(revision) { mutableStateOf(DesktopOverlaySettings.balanceApiToken) }
    var intervalSeconds by remember(revision) { mutableStateOf(DesktopOverlaySettings.balanceIntervalMs / 1000f) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "显示余额浮窗",
            summary = "需先填入 API 凭据；开启后按下方间隔轮询",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.balanceEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )

        TextField(
            value = token,
            onValueChange = { token = it },
            label = "API 凭据",
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "保存凭据",
                onClick = {
                    DesktopOverlaySettings.balanceApiToken = token
                    DesktopOverlaySettings.notifyChanged()
                    features?.syncSwitches()
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "立即查询",
                onClick = {
                    DesktopOverlaySettings.balanceApiToken = token
                    features?.syncSwitches()
                },
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        OverlaySlider(
            title = "轮询间隔",
            summary = "余额查询的最小间隔",
            value = intervalSeconds,
            range = 60f..1800f,
            step = 60f,
            format = { (it / 60f).roundToInt().toString() + " 分钟" },
            onValueChangeFinished = {
                intervalSeconds = it
                DesktopOverlaySettings.balanceIntervalMs = (it * 1000).toLong()
            },
        )

        AnchorPreferences(
            prefixTitle = "余额浮窗",
            anchor = anchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )
    }
}

/** 罗技电池子页。 */
@Composable
fun DesktopOverlayLogiContent() {
    val revision = DesktopOverlaySettings.revision.value
    val features = DesktopOverlayFeatures.current
    val anchor = DesktopOverlaySettings.logiAnchor

    var enabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.logiEnabled) }
    var hideDisconnected by remember(revision) { mutableStateOf(DesktopOverlaySettings.logiHideWhenDisconnected) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "显示罗技电池浮窗",
            summary =
                if (features?.isLogiAvailable() != true) {
                    "原生库不可用；枚举到设备后才会显示"
                } else {
                    "按罗技 HID++ 设备枚举电量，无设备时不显示"
                },
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.logiEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )
        SwitchPreference(
            title = "隐藏离线设备",
            summary = "关闭后离线设备也占一行并显示占位电量",
            checked = hideDisconnected,
            onCheckedChange = {
                hideDisconnected = it
                DesktopOverlaySettings.logiHideWhenDisconnected = it
                DesktopOverlaySettings.notifyChanged()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        LogiDeviceListSection(features = features)

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        AnchorPreferences(
            prefixTitle = "罗技浮窗",
            anchor = anchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )
    }
}

/**
 * 罗技设备列表与设备名修改。
 *
 * 列表项复用浮窗同一套电量图标与分段着色；点击设备名可改写显示名，
 * 空名提交时回退到原生名。设备名覆盖按设备标识持久化。
 */
@Composable
private fun LogiDeviceListSection(features: DesktopOverlayFeatures?) {
    var devices by remember { mutableStateOf(features?.logiDevices().orEmpty()) }
    var editing by remember { mutableStateOf<LogiBatteryDevice?>(null) }
    var editText by remember { mutableStateOf("") }

    DisposableEffect(features) {
        val subscription = features?.observeLogiDevices { devices = it }
        onDispose { subscription?.close() }
    }

    val statusHint =
        when {
            features == null -> "叠加层未启用，设备列表不可用"
            !features.isLogiAvailable() -> "原生库未就绪，无法枚举设备"
            devices.isEmpty() -> "未检测到罗技设备（未配对、HID 未授权 或 设备离线）"
            else -> null
        }

    ArrowPreference(
        title = "罗技设备列表",
        summary = statusHint ?: "共 " + devices.size + " 台；点击设备名可修改显示名",
        endActions = {
            TextButton(
                text = "立即刷新",
                onClick = { features?.refreshLogiNow() },
            )
        },
    )

    if (devices.isNotEmpty()) {
        val iconFontFamily = deviceIconFontFamily()
        devices.forEach { device ->
            ArrowPreference(
                title = device.deviceName,
                summary = logiDeviceSummary(device),
                onClick = {
                    editing = device
                    editText = device.deviceName
                },
                endActions = {
                    Text(
                        text = logiBatteryGlyph(device),
                        color = logiBatteryColor(device),
                        fontSize = 20.sp,
                        fontFamily = iconFontFamily,
                    )
                },
            )
        }
    }

    val target = editing
    if (target != null) {
        OverlayDialog(
            title = "修改设备名",
            summary = "留空提交将回退到原生设备名",
            show = true,
            onDismissRequest = { editing = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    label = target.deviceName,
                    singleLine = true,
                )
                TextButton(
                    text = "保存",
                    onClick = {
                        features?.setLogiDeviceName(target.deviceId, editText)
                        editing = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = "取消",
                    onClick = { editing = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 设备行摘要：电量、充电状态与在线状态。 */
private fun logiDeviceSummary(device: LogiBatteryDevice): String {
    val battery =
        when {
            !device.hasBattery || device.batteryPercent < 0 -> "无电量数据"
            device.isCharging -> device.batteryPercent.toString() + "% · 充电中"
            else -> device.batteryPercent.toString() + "%"
        }
    val online = if (device.online) "在线" else "离线"
    return battery + " · " + online + " · 槽位 " + device.slot
}

/** 键盘监控子页。 */
@Composable
fun DesktopOverlayKeyboardContent() {
    val revision = DesktopOverlaySettings.revision.value
    val features = DesktopOverlayFeatures.current
    val anchor = DesktopOverlaySettings.keyboardAnchor

    var enabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.keyboardEnabled) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "显示按键浮窗",
            summary = "开启后安装全局低级键盘钩子，显示当前按键与锁定键；关闭即卸载钩子",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.keyboardEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        AnchorPreferences(
            prefixTitle = "键盘浮窗",
            anchor = anchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        KeyMappingPreferences(features = features)
    }
}

/** 时间浮窗子页。 */
@Composable
fun DesktopOverlayClockContent() {
    val revision = DesktopOverlaySettings.revision.value
    val features = DesktopOverlayFeatures.current
    val anchor = DesktopOverlaySettings.clockAnchor

    var enabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.clockEnabled) }
    var showSeconds by remember(revision) { mutableStateOf(DesktopOverlaySettings.clockShowSeconds) }
    var use24Hour by remember(revision) { mutableStateOf(DesktopOverlaySettings.clockUse24Hour) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "显示时间浮窗",
            summary = "在主屏按下方位置显示当前时间",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                DesktopOverlaySettings.clockEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )
        SwitchPreference(
            title = "显示秒",
            summary = "关闭后只显示时与分，并在分钟边界刷新",
            checked = showSeconds,
            onCheckedChange = {
                showSeconds = it
                DesktopOverlaySettings.clockShowSeconds = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncClockConfig()
            },
        )
        SwitchPreference(
            title = "24 小时制",
            summary = "关闭后使用 12 小时制并带 AM/PM",
            checked = use24Hour,
            onCheckedChange = {
                use24Hour = it
                DesktopOverlaySettings.clockUse24Hour = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncClockConfig()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        AnchorPreferences(
            prefixTitle = "时间浮窗",
            anchor = anchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )
    }
}

/**
 * 位置、缩放与描边的公共滑块组。
 *
 * 位置按屏幕宽高百分比（0~100），缩放 0.5~4 倍，描边 0~8 像素。
 * 拖动期经 [onValueChange] 实时写入 Anchor 背景态，渲染侧读取即触发重组，浮窗位置跟手；
 * 松手时经 [onValueChangeFinished] 落盘并 [DesktopOverlaySettings.notifyChanged]。
 */
@Composable
private fun AnchorPreferences(
    prefixTitle: String,
    anchor: DesktopOverlaySettings.Anchor,
    onChanged: () -> Unit,
) {
    OverlaySlider(
        title = prefixTitle + "水平位置",
        summary = "占屏幕宽度的百分比，元素以该点水平居中",
        value = anchor.xPercent,
        range = 0f..100f,
        step = 1f,
        format = { it.roundToInt().toString() + "%" },
        onValueChange = { anchor.xPercent = it },
        onValueChangeFinished = {
            anchor.xPercent = it
            anchor.persist()
            onChanged()
        },
    )
    OverlaySlider(
        title = prefixTitle + "垂直位置",
        summary = "占屏幕高度的百分比，元素以该点垂直居中",
        value = anchor.yPercent,
        range = 0f..100f,
        step = 1f,
        format = { it.roundToInt().toString() + "%" },
        onValueChange = { anchor.yPercent = it },
        onValueChangeFinished = {
            anchor.yPercent = it
            anchor.persist()
            onChanged()
        },
    )
    OverlaySlider(
        title = prefixTitle + "缩放",
        summary = "文本按该倍数放大",
        value = anchor.scale,
        range = 0.5f..4f,
        step = 0.05f,
        format = { formatDecimal(it, 2) + "×" },
        onValueChange = { anchor.scale = it },
        onValueChangeFinished = {
            anchor.scale = it
            anchor.persist()
            onChanged()
        },
    )
    OverlaySlider(
        title = prefixTitle + "文本描边",
        summary = "0 表示不描边",
        value = anchor.outlineWidth,
        range = 0f..8f,
        step = 0.25f,
        format = { formatDecimal(it, 2) + "px" },
        onValueChange = { anchor.outlineWidth = it },
        onValueChangeFinished = {
            anchor.outlineWidth = it
            anchor.persist()
            onChanged()
        },
    )
}

/**
 * 快捷键映射的查看与编辑。
 *
 * 映射以「源键 + 目标键 + 显示文案」表达；本页做整条的启用切换与删除，
 * 新增按「录入」方式捕获按键组合：订阅按键状态，取一组非空按键作为源键。
 */
@Composable
private fun KeyMappingPreferences(features: DesktopOverlayFeatures?) {
    val revision = DesktopOverlaySettings.revision.value
    var mappings by remember(revision) { mutableStateOf(DesktopOverlaySettings.keyboardMappings) }
    var recording by remember { mutableStateOf(false) }

    ArrowPreference(
        title = "快捷键映射",
        summary = if (mappings.isEmpty()) "暂无映射；新增后按键命中时显示映射文案" else "共 " + mappings.size + " 条",
    )

    mappings.forEachIndexed { index, mapping ->
        ArrowPreference(
            title = mapping.name.ifBlank { "映射 " + (index + 1) },
            summary = mappingLabel(mapping) + if (mapping.enabled) "" else "（已停用）",
            endActions = {
                TextButton(
                    text = "删除",
                    onClick = {
                        val updated = mappings.toMutableList().also { it.removeAt(index) }
                        mappings = updated
                        features?.setMappings(updated)
                        DesktopOverlaySettings.notifyChanged()
                    },
                )
            },
            onClick = {
                val updated =
                    mappings.toMutableList().also {
                        it[index] = mapping.copy(enabled = !mapping.enabled)
                    }
                mappings = updated
                features?.setMappings(updated)
                DesktopOverlaySettings.notifyChanged()
            },
        )
    }

    if (recording) {
        val source = features
        if (source == null) {
            ArrowPreference(
                title = "无法录入",
                summary = "叠加层未启用，键盘监控不可用",
                onClick = { recording = false },
            )
        } else {
            DisposableEffect(source) {
                val subscription =
                    source.observeKeyboard { snapshot ->
                        val keys = snapshot.pressedKeys.toSet()
                        if (keys.isNotEmpty()) {
                            val updated =
                                mappings +
                                    KeyMapping(
                                        id = "mapping_" + System.currentTimeMillis(),
                                        name = keys.sorted().joinToString("+") { KeyboardSource.keyName(it) },
                                        sourceKeys = keys.sorted(),
                                        targetKey = keys.sorted().last(),
                                    )
                            mappings = updated
                            source.setMappings(updated)
                            DesktopOverlaySettings.notifyChanged()
                            recording = false
                        }
                    }
                onDispose { subscription.close() }
            }
            ArrowPreference(
                title = "正在录入：请按下一个组合键",
                summary = "捕获到按键后自动写入并退出录入",
                onClick = { recording = false },
            )
        }
    } else {
        TextButton(
            text = "新增映射（按下即录入）",
            onClick = { recording = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/** 映射摘要：源键 → 目标键 / 显示文案。 */
private fun mappingLabel(mapping: KeyMapping): String {
    val sources = mapping.sourceKeys.joinToString(" + ") { KeyboardSource.keyName(it) }
    val target = mapping.displayText.ifBlank { KeyboardSource.keyName(mapping.targetKey) }
    return sources + " → " + target
}

/** 定点小数显示：去掉无意义的尾随零。 */
internal fun formatDecimal(
    value: Float,
    decimals: Int,
): String {
    val text = String.format(java.util.Locale.US, "%." + decimals + "f", value)
    return text.trimEnd('0').trimEnd('.')
}
