package notifyrelay.desktop.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.desktop.overlay.feature.DesktopOverlayFeatures
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.shared.ui.common.MiuixSpinnerPreference
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 叠加层扩展点设置内容（桌面端专有）。
 *
 * 直接读写 [DesktopOverlaySettings]；开关变化后经 [DesktopOverlayFeatures.current]
 * 即时同步各数据源的启停，不必重启应用。
 *
 * 本函数只铺列内容，滚动由外层设置页提供。
 */
@Composable
fun DesktopOverlayFeaturesContent() {
    // 设置版本号：用于在开关变化后重建各下拉项的选中态
    val revision = DesktopOverlaySettings.revision.value
    val features = DesktopOverlayFeatures.current

    var balanceEnabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.balanceEnabled) }
    var balanceToken by remember(revision) { mutableStateOf(DesktopOverlaySettings.balanceApiToken) }
    var logiEnabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.logiEnabled) }
    var logiHideDisconnected by remember(revision) { mutableStateOf(DesktopOverlaySettings.logiHideWhenDisconnected) }
    var keyboardEnabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.keyboardEnabled) }
    var clockEnabled by remember(revision) { mutableStateOf(DesktopOverlaySettings.clockEnabled) }
    var clockShowSeconds by remember(revision) { mutableStateOf(DesktopOverlaySettings.clockShowSeconds) }
    var clockUse24Hour by remember(revision) { mutableStateOf(DesktopOverlaySettings.clockUse24Hour) }

    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ======== DeepSeek 余额 ========

        SectionTitle("DeepSeek 余额")
        SwitchPreference(
            title = "显示余额浮窗",
            summary = "开启后按下方间隔轮询余额并显示；需先填入 API 凭据",
            checked = balanceEnabled,
            onCheckedChange = {
                balanceEnabled = it
                DesktopOverlaySettings.balanceEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )

        TextField(
            value = balanceToken,
            onValueChange = { balanceToken = it },
            label = "API 凭据",
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "保存凭据",
                onClick = {
                    DesktopOverlaySettings.balanceApiToken = balanceToken
                    DesktopOverlaySettings.notifyChanged()
                    features?.syncSwitches()
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "立即查询",
                onClick = {
                    DesktopOverlaySettings.balanceApiToken = balanceToken
                    features?.syncSwitches()
                },
                modifier = Modifier.weight(1f),
            )
        }

        AnchorPreferences(
            prefixTitle = "余额浮窗",
            anchor = DesktopOverlaySettings.balanceAnchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        // ======== 罗技电池 ========

        SectionTitle("罗技电池")
        SwitchPreference(
            title = "显示罗技电池浮窗",
            summary = if (features?.isLogiAvailable() != true) "原生库不可用；枚举到设备后才会显示" else "按罗技 HID++ 设备枚举电量，无设备时不显示",
            checked = logiEnabled,
            onCheckedChange = {
                logiEnabled = it
                DesktopOverlaySettings.logiEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )
        SwitchPreference(
            title = "隐藏离线设备",
            summary = "关闭后离线设备也占一行并显示占位电量",
            checked = logiHideDisconnected,
            onCheckedChange = {
                logiHideDisconnected = it
                DesktopOverlaySettings.logiHideWhenDisconnected = it
                DesktopOverlaySettings.notifyChanged()
            },
        )
        AnchorPreferences(
            prefixTitle = "罗技浮窗",
            anchor = DesktopOverlaySettings.logiAnchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        // ======== 键盘监控 ========

        SectionTitle("键盘监控")
        SwitchPreference(
            title = "显示按键浮窗",
            summary = "开启后安装全局低级键盘钩子，显示当前按键与锁定键状态；关闭即卸载钩子",
            checked = keyboardEnabled,
            onCheckedChange = {
                keyboardEnabled = it
                DesktopOverlaySettings.keyboardEnabled = it
                DesktopOverlaySettings.notifyChanged()
                features?.syncSwitches()
            },
        )
        AnchorPreferences(
            prefixTitle = "键盘浮窗",
            anchor = DesktopOverlaySettings.keyboardAnchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )
        KeyMappingPreferences(features = features)

        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

        // ======== 时间浮窗 ========

        SectionTitle("时间浮窗")
        SwitchPreference(
            title = "显示时间浮窗",
            summary = "在主屏按下方位置显示当前时间",
            checked = clockEnabled,
            onCheckedChange = {
                clockEnabled = it
                DesktopOverlaySettings.clockEnabled = it
                DesktopOverlaySettings.notifyChanged()
            },
        )
        SwitchPreference(
            title = "显示秒",
            summary = "关闭后只显示时与分，并在分钟边界刷新",
            checked = clockShowSeconds,
            onCheckedChange = {
                clockShowSeconds = it
                DesktopOverlaySettings.clockShowSeconds = it
                DesktopOverlaySettings.notifyChanged()
            },
        )
        SwitchPreference(
            title = "24 小时制",
            summary = "关闭后使用 12 小时制并带 AM/PM",
            checked = clockUse24Hour,
            onCheckedChange = {
                clockUse24Hour = it
                DesktopOverlaySettings.clockUse24Hour = it
                DesktopOverlaySettings.notifyChanged()
            },
        )
        AnchorPreferences(
            prefixTitle = "时间浮窗",
            anchor = DesktopOverlaySettings.clockAnchor,
            onChanged = { DesktopOverlaySettings.notifyChanged() },
        )
    }
}

/** 分组标题。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title4,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
    )
}

/** 位置、缩放、颜色与描边的公共下拉项。 */
@Composable
private fun AnchorPreferences(
    prefixTitle: String,
    anchor: DesktopOverlaySettings.Anchor,
    onChanged: () -> Unit,
) {
    MiuixSpinnerPreference(
        title = prefixTitle + "水平位置",
        summary = "占屏幕宽度的百分比，元素以该点水平居中",
        items = DesktopOverlaySettings.POSITION_PERCENT_OPTIONS.map { it.toString() + "%" },
        selectedIndex =
            DesktopOverlaySettings.POSITION_PERCENT_OPTIONS
                .indexOfFirst { it.toFloat() == anchor.xPercent }
                .coerceAtLeast(0),
        onSelectedIndexChange = { index ->
            val value = DesktopOverlaySettings.POSITION_PERCENT_OPTIONS.getOrNull(index)?.toFloat() ?: return@MiuixSpinnerPreference
            anchor.xPercent = value
            onChanged()
        },
    )
    MiuixSpinnerPreference(
        title = prefixTitle + "垂直位置",
        summary = "占屏幕高度的百分比，元素以该点垂直居中",
        items = DesktopOverlaySettings.POSITION_PERCENT_OPTIONS.map { it.toString() + "%" },
        selectedIndex =
            DesktopOverlaySettings.POSITION_PERCENT_OPTIONS
                .indexOfFirst { it.toFloat() == anchor.yPercent }
                .coerceAtLeast(0),
        onSelectedIndexChange = { index ->
            val value = DesktopOverlaySettings.POSITION_PERCENT_OPTIONS.getOrNull(index)?.toFloat() ?: return@MiuixSpinnerPreference
            anchor.yPercent = value
            onChanged()
        },
    )
    MiuixSpinnerPreference(
        title = prefixTitle + "缩放",
        summary = "文本与描边按该倍数放大",
        items = DesktopOverlaySettings.SCALE_OPTIONS.map { it.toString() + "×" },
        selectedIndex =
            DesktopOverlaySettings.SCALE_OPTIONS
                .indexOfFirst { it == anchor.scale }
                .coerceAtLeast(0),
        onSelectedIndexChange = { index ->
            val value = DesktopOverlaySettings.SCALE_OPTIONS.getOrNull(index) ?: return@MiuixSpinnerPreference
            anchor.scale = value
            onChanged()
        },
    )
    MiuixSpinnerPreference(
        title = prefixTitle + "文本描边",
        summary = "0 表示不描边",
        items = DesktopOverlaySettings.OUTLINE_WIDTH_OPTIONS.map { it.toInt().toString() + "px" },
        selectedIndex =
            DesktopOverlaySettings.OUTLINE_WIDTH_OPTIONS
                .indexOfFirst { it == anchor.outlineWidth }
                .coerceAtLeast(0),
        onSelectedIndexChange = { index ->
            val value = DesktopOverlaySettings.OUTLINE_WIDTH_OPTIONS.getOrNull(index) ?: return@MiuixSpinnerPreference
            anchor.outlineWidth = value
            onChanged()
        },
    )
}

/**
 * 快捷键映射的查看与编辑。
 *
 * 映射以「源键 + 目标键 + 显示文案」表达；本页做整条的启用切换与删除，
 * 新增按「录入」方式捕获按键组合：订阅按键状态，取一组非空且稳定后的按键作为源键。
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
        // 录入态：订阅按键状态，取「非修饰键且已按下」的集合作为源键
        val source = features
        if (source == null) {
            ArrowPreference(
                title = "无法录入",
                summary = "叠加层未启用，键盘监控不可用",
                onClick = { recording = false },
            )
        } else {
            androidx.compose.runtime.DisposableEffect(source) {
                var captured: Set<Int> = emptySet()
                val subscription =
                    source.observeKeyboard { snapshot ->
                        val keys = snapshot.pressedKeys.toSet()
                        if (keys.isEmpty()) {
                            captured = emptySet()
                        } else {
                            captured = keys
                            if (keys.size >= 1) {
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
