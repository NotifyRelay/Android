package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import io.github.miuzarte.scrcpyforandroid.constants.AppDefaults
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlide
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import kotlin.math.roundToInt

private data class ThemeModeOption(
    val label: String,
    val mode: ColorSchemeMode,
)

private val THEME_BASE_OPTIONS = listOf(
    ThemeModeOption("跟随系统", ColorSchemeMode.System),
    ThemeModeOption("浅色", ColorSchemeMode.Light),
    ThemeModeOption("深色", ColorSchemeMode.Dark),
)

fun resolveThemeMode(baseIndex: Int, monetEnabled: Boolean): ColorSchemeMode {
    return when (baseIndex.coerceIn(0, 2)) {
        0 -> if (monetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        1 -> if (monetEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        else -> if (monetEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }
}

private fun resolveThemeLabel(baseIndex: Int, monetEnabled: Boolean): String {
    val base = THEME_BASE_OPTIONS.getOrNull(baseIndex.coerceIn(0, 2))?.label ?: "跟随系统"
    return if (monetEnabled) "Monet（$base）" else base
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    themeBaseIndex: Int,
    onThemeBaseIndexChange: (Int) -> Unit,
    monetEnabled: Boolean,
    onMonetEnabledChange: (Boolean) -> Unit,
    fullscreenDebugInfoEnabled: Boolean,
    onFullscreenDebugInfoEnabledChange: (Boolean) -> Unit,
    keepScreenOnWhenStreamingEnabled: Boolean,
    onKeepScreenOnWhenStreamingEnabledChange: (Boolean) -> Unit,
    devicePreviewCardHeightDp: Int,
    onDevicePreviewCardHeightDpChange: (Int) -> Unit,
    showFullscreenVirtualButtons: Boolean,
    onOpenReorderDevices: () -> Unit,
    onOpenVirtualButtonOrder: () -> Unit,
    onShowFullscreenVirtualButtonsChange: (Boolean) -> Unit,
    customServerUri: String?,
    onPickServer: () -> Unit,
    onClearServer: () -> Unit,
    serverRemotePath: String,
    onServerRemotePathChange: (String) -> Unit,
    adbKeyName: String,
    onAdbKeyNameChange: (String) -> Unit,
    adbPairingAutoDiscoverOnDialogOpen: Boolean,
    onAdbPairingAutoDiscoverOnDialogOpenChange: (Boolean) -> Unit,
    adbAutoReconnectPairedDevice: Boolean,
    onAdbAutoReconnectPairedDeviceChange: (Boolean) -> Unit,
    scrollBehavior: ScrollBehavior,
) {
    val baseModeItems = THEME_BASE_OPTIONS.map { it.label }
    val context = LocalContext.current

    // 设置
    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            SectionSmallTitle("主题")
            Card {
                SuperDropdown(
                    title = "外观模式",
                    summary = resolveThemeLabel(themeBaseIndex, monetEnabled),
                    items = baseModeItems,
                    selectedIndex = themeBaseIndex.coerceIn(0, baseModeItems.lastIndex),
                    onSelectedIndexChange = onThemeBaseIndexChange,
                )
                SuperSwitch(
                    title = "Monet",
                    summary = "开启后使用 Monet 动态配色",
                    checked = monetEnabled,
                    onCheckedChange = onMonetEnabledChange,
                )
            }

            SectionSmallTitle("投屏")
            Card {
                SuperSwitch(
                    title = "启用调试信息",
                    summary = "在全屏界面显示触点数量、设备分辨率和实时 FPS",
                    checked = fullscreenDebugInfoEnabled,
                    onCheckedChange = onFullscreenDebugInfoEnabledChange,
                )
                SuperSwitch(
                    title = "投屏时保持屏幕常亮",
                    summary = "Scrcpy 启动后保持本机屏幕常亮，避免锁屏导致 ADB 断开",
                    checked = keepScreenOnWhenStreamingEnabled,
                    onCheckedChange = onKeepScreenOnWhenStreamingEnabledChange,
                )
                SuperSlide(
                    title = "预览卡高度",
                    summary = "设备页预览卡高度",
                    value = devicePreviewCardHeightDp.toFloat(),
                    onValueChange = {
                        onDevicePreviewCardHeightDpChange(
                            it.roundToInt().coerceAtLeast(120)
                        )
                    },
                    valueRange = 160f..600f,
                    steps = 439,
                    unit = "dp",
                    displayFormatter = { it.roundToInt().toString() },
                    inputInitialValue = devicePreviewCardHeightDp.toString(),
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 120f..Float.MAX_VALUE,
                    onInputConfirm = { raw ->
                        raw.toIntOrNull()
                            ?.let { onDevicePreviewCardHeightDpChange(it.coerceAtLeast(120)) }
                    },
                )
                SuperArrow(
                    title = "快速设备排序",
                    summary = "手动排序设备页的快速设备",
                    onClick = onOpenReorderDevices,
                )
                SuperArrow(
                    title = "虚拟按钮排序",
                    summary = "手动排序预览/全屏时的虚拟按钮，并选择哪些按钮展示在外",
                    onClick = onOpenVirtualButtonOrder,
                )
                SuperSwitch(
                    title = "全屏显示虚拟按钮",
                    summary = "在全屏控制页底部显示返回键、主页键等虚拟按钮",
                    checked = showFullscreenVirtualButtons,
                    onCheckedChange = onShowFullscreenVirtualButtonsChange,
                )
            }

            SectionSmallTitle("scrcpy-server")
            Card {
                Spacer(modifier = Modifier.padding(top = UiSpacing.CardContent))
                Text(
                    text = "自定义 binary",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                TextField(
                    value = customServerUri ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = "scrcpy-server-v3.3.4",
                    useLabelAsPlaceholder = customServerUri == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    trailingIcon = {
                        Row(modifier = Modifier.padding(end = UiSpacing.SectionTitleLeadingGap)) {
                            if (customServerUri != null) IconButton(onClick = onClearServer) {
                                Icon(Icons.Rounded.Clear, contentDescription = "清空")
                            }
                            IconButton(onClick = onPickServer) {
                                Icon(Icons.Rounded.FileOpen, contentDescription = "选择文件")
                            }
                        }
                    },
                )
                Text(
                    text = "Remote Path",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                TextField(
                    value = serverRemotePath,
                    onValueChange = onServerRemotePathChange,
                    label = AppDefaults.SERVER_REMOTE_PATH,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
            }

            SectionSmallTitle("ADB")
            Card {
                Text(
                    text = "自定义 ADB 密钥名",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(top = UiSpacing.CardContent, bottom = UiSpacing.FieldLabelBottom),
                    fontWeight = FontWeight.Medium,
                )
                TextField(
                    value = adbKeyName,
                    onValueChange = onAdbKeyNameChange,
                    label = AppDefaults.ADB_KEY_NAME,
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
                SuperSwitch(
                    title = "配对时自动启用发现服务",
                    summary = "打开配对弹窗后自动搜索可用配对端口",
                    checked = adbPairingAutoDiscoverOnDialogOpen,
                    onCheckedChange = onAdbPairingAutoDiscoverOnDialogOpenChange,
                )
                SuperSwitch(
                    title = "自动重连已配对设备",
                    summary = "自动发现开启无线调试的设备，更新快速设备的随机端口并尝试连接（效果比较随缘）",
                    checked = adbAutoReconnectPairedDevice,
                    onCheckedChange = onAdbAutoReconnectPairedDeviceChange,
                )
            }

            SectionSmallTitle("关于")
            Card {
                SuperArrow(
                    title = "前往仓库",
                    summary = "github.com/Miuzarte/ScrcpyForAndroid",
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/Miuzarte/ScrcpyForAndroid".toUri()
                        )
                        context.startActivity(intent)
                    },
                )
            }
        }

        // TODO: 放进 [AppPageLazyColumn] 里
        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}
