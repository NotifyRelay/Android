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
import com.xzyht.notifyrelay.feature.device.service.AuthInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.sync.HandshakeSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.core.util.PairingCodeManager
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.security.KeyPair
import notifyrelay.core.util.EcdhKeyStore
import notifyrelay.core.util.EncryptionManager

/**
 * 配对码对话框。
 * - CLIENT_MODE（发起端）：生成并显示配对码
 * - SERVER_MODE（接收端）：输入对方显示的配对码
 */
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
        // ==================== 发起端：生成临时密钥对，显示配对码 ====================
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val displayCode = remember { pairingCode.ifEmpty { PairingCodeManager.generate() } }
        val tmpKeyPair = remember { EcdhKeyStore.generateEphemeralKeyPair() }
        val tmpPubKeyB64 = remember { EcdhKeyStore.encodePublicKey(tmpKeyPair.public) }
        // 存储临时私钥供 ServerLineRouter.handlePairingResp 解密配对码用
        LaunchedEffect(Unit) {
            deviceManager.pendingTempPrivKeyB64 = android.util.Base64.encodeToString(tmpKeyPair.private.encoded, android.util.Base64.NO_WRAP)
        }

        // 发送 PAIRING_INIT（携带临时公钥）
        LaunchedEffect(show) {
            if (show && targetDevice != null) {
                delay(500)
                withContext(Dispatchers.IO) {
                    val initResp = HandshakeSender.sendPairingInit(deviceManager, targetDevice, tmpPubKeyB64)
                    Logger.d("配对", "PAIRING_INIT response: $initResp")
                }
                // 等待配对完成：尝试 30 次，每次 3 秒
                var attempts = 0
                while (attempts < 30) {
                    delay(3000)
                    if (deviceManager.isAuthenticatedInternal(targetDevice.uuid)) {
                        withContext(Dispatchers.Main) {
                            onPairingComplete(true, "配对成功")
                            onDismiss()
                        }
                        return@LaunchedEffect
                    }
                    attempts++
                }
                deviceManager.pendingTempPrivKeyB64 = null
                onPairingComplete(false, "配对超时")
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
                            PairingCodeManager.clear()
                            deviceManager.pendingTempPrivKeyB64 = null
                            onDismiss()
                        }
                    )
                }
            }
        )
    } else {
        // ==================== 接收端：输入配对码，加密回传 ====================
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
        val remoteTmpPubKey = remember {
            pending?.tmpPubKey ?: ""  // 发起端的临时公钥，用于加密配对码
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

                    // 使用统一的 6 位配对码输入组件
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
                                if (remoteTmpPubKey.isEmpty()) {
                                    errorMsg = "未获取到发起端密钥信息"
                                    return@TextButton
                                }
                                isPairing = true

                                serverScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            // 1. 使用发起端的临时公钥加密配对码
                                            val initiator = DeviceInfo(remoteUuid, targetDevice?.displayName ?: "", remoteIp, 23333)
                                            
                                            // 加密配对码：使用临时密钥 + ECDH
                                            val receiverTmpKeyPair = EcdhKeyStore.generateEphemeralKeyPair()
                                            val sharedSecret = EcdhKeyStore.deriveRawSharedSecret(
                                                receiverTmpKeyPair.private, remoteTmpPubKey
                                            )
                                            val aesKey = EncryptionManager.hkdfDeriveKey(sharedSecret)
                                            val encryptedCode = EncryptionManager.encrypt(code, aesKey)
                                            
                                            // 发送 PAIRING_RESP（加密后的配对码）
                                            val receiverTmpPubKeyB64 = EcdhKeyStore.encodePublicKey(receiverTmpKeyPair.public)
                                            val resp = HandshakeSender.sendPairingResp(deviceManager, initiator, receiverTmpPubKeyB64, encryptedCode)
                                            
                                            if (resp?.startsWith("ACCEPT:") == true) {
                                                val parts = resp.split(":")
                                                // ACCEPT 格式: ACCEPT:<code>:<uuid>:<ltPubKey>:<ip>:<battery>:<deviceType>
                                                if (parts.size >= 5) {
                                                    val initiatorLtPubKey = parts[3]
                                                    // 使用长期 ECDH 密钥完成标准密钥交换
                                                    val success = deviceManager.completePairingWithLongTermKeys(
                                                        remoteUuid, initiatorLtPubKey,
                                                        displayName = targetDevice?.displayName ?: "",
                                                        lastIp = remoteIp
                                                    )
                                                    if (success) {
                                                        "配对成功"
                                                    } else {
                                                        "密钥交换失败"
                                                    }
                                                } else {
                                                    "配对失败：响应格式错误"
                                                }
                                            } else if (resp?.startsWith("REJECT:") == true) {
                                                "对方拒绝了配对请求"
                                            } else {
                                                "配对失败，请确认配对码后重试"
                                            }
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
                                }
                            }
                        )
                    }
                }
            }
        )
    }
}
