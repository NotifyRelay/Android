package io.github.miuzarte.scrcpyforandroid.pages

import android.app.Application
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.base.util.ToastUtils
import notifyrelay.data.config.ScrcpyDefaults
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun ScrcpySettingsPage(
    onOpenVirtualButtonOrder: () -> Unit = {},
    onPickServer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ScrcpyUiViewModel = viewModel(factory = ScrcpyUiViewModel.Factory(app))
    val snackHostState = remember { SnackbarHostState() }
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    val navigationActions = remember(onOpenVirtualButtonOrder, onPickServer) {
        ScrcpyNavigationActions(
            openAdvancedPage = {},
            openVirtualButtonOrder = onOpenVirtualButtonOrder,
            openFullscreenPage = { _, _, _ -> },
            pickServer = onPickServer,
        )
    }

    ProvideScrcpyUiEnvironment(
        viewModel = viewModel,
        contentPadding = PaddingValues(0.dp),
        scrollBehavior = null,
        snackHostState = snackHostState,
        themeBaseIndex = themeBaseIndex,
        navigationActions = navigationActions,
    ) {
        val contentPadding = LocalScrcpyPagePadding.current
        val scrollBehavior = LocalScrcpyScrollBehavior.current

        AppPageLazyColumn(
            contentPadding = contentPadding,
            scrollBehavior = scrollBehavior,
        ) {
            item {
                SectionSmallTitle("投屏")
                Card {
                    SwitchPreference(
                        title = "启用调试信息",
                        summary = "在全屏界面显示触点数量、设备分辨率和实时 FPS",
                        checked = viewModel.fullscreenDebugInfoEnabled,
                        onCheckedChange = { viewModel.fullscreenDebugInfoEnabled = it },
                    )
                    SwitchPreference(
                        title = "投屏时保持屏幕常亮",
                        summary = "Scrcpy 启动后保持本机屏幕常亮，避免锁屏导致 ADB 断开",
                        checked = viewModel.keepScreenOnWhenStreamingEnabled,
                        onCheckedChange = { viewModel.keepScreenOnWhenStreamingEnabled = it },
                    )
                    SwitchPreference(
                        title = "低延迟音频",
                        summary = "启用低延迟音频路径（可能增加功耗）",
                        checked = viewModel.lowLatency,
                        onCheckedChange = { viewModel.lowLatency = it },
                    )
                    ArrowPreference(
                        title = "虚拟按钮排序",
                        summary = "手动排序预览/全屏时的虚拟按钮，并选择哪些按钮展示在外",
                        onClick = navigationActions.openVirtualButtonOrder,
                    )
                    SwitchPreference(
                        title = "全屏显示虚拟按钮",
                        summary = "在全屏控制页底部显示返回键、主页键等虚拟按钮",
                        checked = viewModel.showFullscreenVirtualButtons,
                        onCheckedChange = { viewModel.showFullscreenVirtualButtons = it },
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
                        value = viewModel.customServerUri ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = "scrcpy-server-v3.3.4",
                        useLabelAsPlaceholder = viewModel.customServerUri == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                        trailingIcon = {
                            Row(modifier = Modifier.padding(end = UiSpacing.SectionTitleLeadingGap)) {
                                if (viewModel.customServerUri != null) IconButton(onClick = { viewModel.customServerUri = null }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "清空")
                                }
                                IconButton(onClick = navigationActions.pickServer) {
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
                        value = viewModel.serverRemotePath,
                        onValueChange = { viewModel.serverRemotePath = it },
                        label = ScrcpyDefaults.SERVER_REMOTE_PATH,
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
                        text = "当前 ADB 密钥名",
                        modifier = Modifier
                            .padding(horizontal = UiSpacing.CardTitle)
                            .padding(top = UiSpacing.CardContent, bottom = UiSpacing.FieldLabelBottom),
                        fontWeight = FontWeight.Medium,
                    )
                    TextField(
                        value = viewModel.currentAdbKeyName,
                        onValueChange = {},
                        readOnly = true,
                        label = "设备名@scrcpy",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                    SwitchPreference(
                        title = "配对时自动启用发现服务",
                        summary = "打开配对弹窗后自动搜索可用配对端口",
                        checked = viewModel.adbPairingAutoDiscoverOnDialogOpen,
                        onCheckedChange = { viewModel.adbPairingAutoDiscoverOnDialogOpen = it },
                    )
                    SwitchPreference(
                        title = "自动重连已配对设备",
                        summary = "自动发现开启无线调试的设备，更新快速设备的随机端口并尝试连接（效果比较随缘）",
                        checked = viewModel.adbAutoReconnectPairedDevice,
                        onCheckedChange = { viewModel.adbAutoReconnectPairedDevice = it },
                    )
                }

                SectionSmallTitle("关于")
                Card {
                    ArrowPreference(
                        title = "当前基于原Miuzarte/ScrcpyForAndroid项目",
                        summary = "的47d140提交",
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/Miuzarte/ScrcpyForAndroid/commit/47d140a5c7d539b4596dc74910a85369345edfd4".toUri()
                            )
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                ToastUtils.showShortToast(context, "未找到可打开链接的应用")
                            }
                        },
                    )
                }
            }

            item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
        }
    }
}
