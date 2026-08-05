package com.xzyht.notifyrelay.ui.dialog

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
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import notifyrelay.base.util.Logger
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

enum class PairingMode { CLIENT_MODE, SERVER_MODE }

@Composable
fun PairingCodeDialog(
    mode: PairingMode,
    deviceManager: DeviceConnectionManager,
    targetDevice: DeviceInfo? = null,
    pairingCode: String = "",
    show: Boolean,
    onDismiss: () -> Unit,
    onPairingComplete: (success: Boolean, message: String) -> Unit = { _, _ -> }
) {
    if (!show) return

    val colorScheme = MiuixTheme.colorScheme

    if (mode == PairingMode.CLIENT_MODE) {
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val displayCode = remember {
            val ctx = deviceManager.rustContextInternal
            if (ctx != null) {
                NativeCore.generatePairingCode(ctx)
            } else {
                val r = java.security.SecureRandom()
                (r.nextInt(900_000) + 100_000).toString()
            }
        } ?: run {
            val r = java.security.SecureRandom()
            (r.nextInt(900_000) + 100_000).toString()
        }

        LaunchedEffect(show) {
            if (show && targetDevice != null) {
                delay(500)
                val handshakeDeferred = deviceManager.registerHandshakeWaiter(targetDevice.uuid)
                try {
                    val initSuccess = withContext(Dispatchers.IO) {
                        val ctx = deviceManager.rustContextInternal
                        if (ctx == null) {
                            false
                        } else {
                            val batteryLevel = notifyrelay.core.util.BatteryUtils.getBatteryLevel(deviceManager.contextInternal)
                            val isCharging = notifyrelay.core.util.BatteryUtils.isCharging(deviceManager.contextInternal)
                            val battery = if (isCharging) batteryLevel else -batteryLevel
                            NativeCore.sendPairingInit(ctx, deviceManager.uuid, targetDevice!!.uuid, displayCode, battery, "android") == 0
                        }
                    }
                    if (!initSuccess) {
                        onPairingComplete(false, "配对初始化失败")
                        onDismiss()
                        return@LaunchedEffect
                    }
                    val result = withTimeoutOrNull(90_000L) {
                        handshakeDeferred.await()
                    }
                    withContext(Dispatchers.Main) {
                        if (result == true) {
                            onPairingComplete(true, "配对成功")
                        } else if (result == false) {
                            onPairingComplete(false, "配对码验证失败")
                        } else {
                            onPairingComplete(false, "配对超时")
                        }
                        onDismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onPairingComplete(false, "配对异常: ${e.message}")
                        onDismiss()
                    }
                } finally {
                    deviceManager.cancelHandshakeWaiter(targetDevice.uuid, handshakeDeferred)
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = displayCode,
                        fontSize = 36.sp,
                        color = colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "5 分钟内有效",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        text = "点击复制",
                        onClick = {
                            clipboardManager.setText(AnnotatedString(displayCode))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "等待对方确认...",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = "取消",
                        onClick = {
                            deviceManager.rustContextInternal?.let { NativeCore.clearPairingCode(it) }
                            onDismiss()
                        }
                    )
                }
            }
        )
    } else {
        val serverScope = rememberCoroutineScope()
        var code by remember { mutableStateOf("") }
        var isPairing by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val pending = deviceManager.pendingPairing
        val remoteIp = remember {
            pending?.remoteIp ?: targetDevice?.ip ?: ""
        }
        val remoteUuid = remember {
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "请输入发起端设备上显示的 6 位配对码",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PairingCodeInputField(
                        code = code,
                        onCodeChange = {
                            code = it
                            errorMsg = null
                        },
                        errorMsg = errorMsg,
                        enabled = !isPairing
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            text = "取消",
                            enabled = !isPairing,
                            onClick = onDismiss
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

                                serverScope.launch {
                                                    val handshakeDeferred = deviceManager.registerHandshakeWaiter(remoteUuid)
                                                    try {
                                                        val result = withContext(Dispatchers.IO) {
                                                            try {
                                                                val ctx = deviceManager.rustContextInternal
                                                                if (ctx == null) return@withContext "配对失败：未初始化"
                                                                val ltPubKey = deviceManager.localPublicKey
                                                                val sendOk = NativeCore.sendPairingResp(ctx, remoteUuid, ltPubKey, code, remoteIp, 50, "android")
                                                                if (sendOk != 0) return@withContext "配对失败：发送响应失败"
                                                                val success = withTimeoutOrNull(30_000L) {
                                                                    handshakeDeferred.await()
                                                                }
                                                                if (success == true) "配对成功"
                                                                else if (success == false) "配对失败：对方拒绝了配对"
                                                                else "配对超时"
                                                            } catch (e: Exception) {
                                                                "配对失败: ${e.message}"
                                                            }
                                                        }
                                        if (result == "配对成功") {
                                            onPairingComplete(true, "配对成功")
                                            onDismiss()
                                        } else {
                                            isPairing = false
                                            errorMsg = result
                                        }
                                    } finally {
                                        deviceManager.cancelHandshakeWaiter(remoteUuid, handshakeDeferred)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        )
    }
}
