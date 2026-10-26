package notifyrelay.shared.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.contract.DevicePairingPort
import notifyrelay.shared.feature.device.pairing.LocalPairingDependencies
import notifyrelay.shared.feature.device.pairing.PairingViewModel
import notifyrelay.shared.ui.dialog.PairingCodeInputField
import notifyrelay.shared.ui.dialog.PairingMode
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 构造 [PairingViewModel]，注入核心端口与握手等待器。 */
@Composable
private fun pairingViewModelFactory(deviceManager: DevicePairingPort): ViewModelProvider.Factory {
    val deps =
        LocalPairingDependencies.current
            ?: error("LocalPairingDependencies 未提供")
    return remember(deviceManager, deps) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PairingViewModel(
                    core = deps.core,
                    pairingPort = deviceManager,
                    handshakePort = deps.handshakePort,
                ) as T
        }
    }
}

@Composable
fun PairingCodeDialog(
    mode: PairingMode,
    deviceManager: DevicePairingPort,
    targetDevice: DeviceInfo? = null,
    pairingCode: String = "",
    show: Boolean,
    onDismiss: () -> Unit,
    onPairingComplete: (success: Boolean, message: String) -> Unit = { _, _ -> },
    viewModel: PairingViewModel = viewModel(factory = pairingViewModelFactory(deviceManager)),
) {
    if (!show) return

    val deps = LocalPairingDependencies.current ?: error("LocalPairingDependencies 未提供")

    val colorScheme = MiuixTheme.colorScheme

    if (mode == PairingMode.CLIENT_MODE) {
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val displayCode =
            remember {
                viewModel.ensureDisplayCode()
            }

        LaunchedEffect(show) {
            if (show && targetDevice != null) {
                delay(500)
                val battery = deps.batteryProvider()
                viewModel.startClientPairing(targetDevice, battery) { success, message ->
                    onPairingComplete(success, message)
                    onDismiss()
                }
            }
        }

        WindowDialog(
            show = show,
            title = "设备配对",
            titleColor = DialogDefaults.titleColor(),
            summary = "请在目标设备上输入此配对码",
            summaryColor = DialogDefaults.summaryColor(),
            backgroundColor = DialogDefaults.backgroundColor(),
            enableWindowDim = true,
            onDismissRequest = onDismiss,
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = displayCode ?: "",
                        fontSize = 36.sp,
                        color = colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "5 分钟内有效",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        text = "点击复制",
                        onClick = {
                            displayCode?.let { clipboardManager.setText(AnnotatedString(it)) }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "等待对方确认...",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = "取消",
                        onClick = {
                            viewModel.clearCode()
                            onDismiss()
                        },
                    )
                }
            },
        )
    } else {
        var code by remember { mutableStateOf("") }
        var isPairing by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val pending = deviceManager.pendingPairing
        val remoteIp =
            remember {
                pending?.remoteIp ?: targetDevice?.ip ?: ""
            }
        val remoteUuid =
            remember {
                pending?.remoteUuid ?: targetDevice?.uuid ?: ""
            }

        WindowDialog(
            show = show,
            title = "输入配对码",
            titleColor = DialogDefaults.titleColor(),
            summary = targetDevice?.displayName ?: "未知设备",
            summaryColor = DialogDefaults.summaryColor(),
            backgroundColor = DialogDefaults.backgroundColor(),
            enableWindowDim = true,
            onDismissRequest = {
                if (!isPairing) onDismiss()
            },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "请输入发起端设备上显示的 6 位配对码",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PairingCodeInputField(
                        code = code,
                        onCodeChange = {
                            code = it
                            errorMsg = null
                        },
                        errorMsg = errorMsg,
                        enabled = !isPairing,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "取消",
                            enabled = !isPairing,
                            onClick = onDismiss,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            text = if (isPairing) "配对中..." else "配对",
                            enabled = code.length == 6 && !isPairing,
                            onClick = {
                                if (code.length != 6 || remoteUuid.isEmpty() || remoteIp.isEmpty()) {
                                    errorMsg = "连接信息不完整，请重新发起配对"
                                    return@TextButton
                                }
                                isPairing = true
                                val battery = deps.batteryProvider()
                                viewModel.submitServerPairing(remoteUuid, remoteIp, code, battery) { result, ok ->
                                    if (ok) {
                                        onPairingComplete(true, "配对成功")
                                        onDismiss()
                                    } else {
                                        isPairing = false
                                        errorMsg = result
                                    }
                                }
                            },
                        )
                    }
                }
            },
        )
    }
}
