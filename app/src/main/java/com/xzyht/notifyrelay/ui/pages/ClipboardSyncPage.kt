package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xzyht.notifyrelay.servers.clipboard.FcitxClipboardManager
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ClipboardSyncMode(val displayName: String) {
    OFF("关闭"),
    FCITX5("Fcitx5")
}

@Composable
fun ClipboardSyncPage() {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var fcitx5Paired by remember {
        FcitxClipboardManager.restorePairedState(context)
        mutableStateOf(FcitxClipboardManager.isPaired)
    }

    var selectedMode by remember {
        mutableIntStateOf(
            if (fcitx5Paired) ClipboardSyncMode.FCITX5.ordinal
            else ClipboardSyncMode.OFF.ordinal
        )
    }

    var pairingCode by remember { mutableStateOf("") }
    var pairingState by remember { mutableStateOf("") }

    fun refreshStatus() {
        fcitx5Paired = FcitxClipboardManager.isPaired
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(fcitx5Paired) {
        if (fcitx5Paired) {
            selectedMode = ClipboardSyncMode.FCITX5.ordinal
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "剪贴板同步",
            style = textStyles.title1,
            color = colorScheme.onSurface
        )

        Text(
            text = "管理设备间的剪贴板同步功能",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )

        Text(
            text = when (selectedMode) {
                ClipboardSyncMode.FCITX5.ordinal -> if (fcitx5Paired) "已启用 - 通过 Fcitx5 同步" else "未配对 - 请输入配对码"
                else -> "已关闭 - 可手动点击通知栏按钮同步"
            },
            style = textStyles.body2,
            color = when (selectedMode) {
                ClipboardSyncMode.FCITX5.ordinal -> if (fcitx5Paired) colorScheme.primary else colorScheme.error
                else -> colorScheme.onSurfaceSecondary
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        WindowDropdownPreference(
            title = "同步模式",
            summary = when {
                fcitx5Paired -> "Fcitx5 - 已配对"
                selectedMode == ClipboardSyncMode.FCITX5.ordinal -> "Fcitx5 - 未配对"
                else -> "关闭"
            },
            items = ClipboardSyncMode.entries.map { it.displayName },
            selectedIndex = selectedMode,
            onSelectedIndexChange = { index ->
                when (index) {
                    ClipboardSyncMode.OFF.ordinal -> {
                        selectedMode = ClipboardSyncMode.OFF.ordinal
                        if (fcitx5Paired) {
                            FcitxClipboardManager.revokePairing(context)
                            fcitx5Paired = false
                        }
                        ToastUtils.showShortToast(context, "已关闭剪贴板同步")
                    }
                    ClipboardSyncMode.FCITX5.ordinal -> {
                        selectedMode = ClipboardSyncMode.FCITX5.ordinal
                        if (!fcitx5Paired) {
                            FcitxClipboardManager.bindService(context)
                        }
                    }
                }
            }
        )

        if (selectedMode == ClipboardSyncMode.FCITX5.ordinal) {
            if (fcitx5Paired) {
                Text(
                    text = "Fcitx5 配对状态：已配对",
                    style = textStyles.body1,
                    color = colorScheme.primary
                )

                DoubleClickConfirmButton(
                    text = "撤销配对",
                    confirmText = "确认撤销?",
                    onClick = {},
                    onConfirm = {
                        scope.launch(Dispatchers.IO) {
                            FcitxClipboardManager.revokePairing(context)
                            withContext(Dispatchers.Main) {
                                fcitx5Paired = false
                                selectedMode = ClipboardSyncMode.OFF.ordinal
                                ToastUtils.showShortToast(context, "已撤销 Fcitx5 配对")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "配对状态：未配对",
                    style = textStyles.body1,
                    color = colorScheme.error
                )

                TextField(
                    value = pairingCode,
                    onValueChange = { pairingCode = it.take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "配对码",
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (pairingCode.length != 6) {
                                ToastUtils.showShortToast(context, "请输入 6 位配对码")
                                return@Button
                            }
                            pairingState = "配对中..."
                            scope.launch(Dispatchers.IO) {
                                FcitxClipboardManager.requestPairing(context, pairingCode) { success ->
                                    scope.launch(Dispatchers.Main) {
                                        if (success) {
                                            fcitx5Paired = true
                                            pairingState = "配对成功"
                                            ToastUtils.showShortToast(context, "Fcitx5 配对成功")
                                        } else {
                                            pairingState = "配对失败，请重试"
                                            ToastUtils.showShortToast(context, "Fcitx5 配对失败")
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("配对")
                    }
                }

                if (pairingState.isNotEmpty()) {
                    Text(
                        text = pairingState,
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceSecondary
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            text = "注意：",
            style = textStyles.body1,
            color = colorScheme.onSurface
        )

        Text(
            text = "1. Fcitx5 模式：需安装 Fcitx5 输入法并开启剪贴板广播功能\n" +
                    "2. 关闭时可通过点击通知栏按钮手动同步",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
