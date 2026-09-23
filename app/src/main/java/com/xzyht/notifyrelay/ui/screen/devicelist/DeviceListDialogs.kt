package com.xzyht.notifyrelay.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.ui.dialog.PairingCodeDialog
import com.xzyht.notifyrelay.ui.dialog.PairingMode
import com.xzyht.notifyrelay.ui.dialog.RejectedDevicesDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 配对码对话框（服务端模式 / 客户端模式两个分支）。
 *
 * 这些对话框块原先直接内联在 [DeviceListScreen] 函数体内，依赖外层闭包；
 * 抽离后原先的自由变量一律显式化为参数。配对成功后刷新已认证设备集合。
 */
@Composable
internal fun DeviceListPairingCodeDialog(
    state: DeviceListScreenState,
    deviceManager: DeviceConnectionManager,
    onAuthedUuidsChange: (Set<String>) -> Unit,
) {
    // 配对码对话框 - 服务端模式
    if (state.showPairingCodeDialog && state.pairingCodeDialogMode == PairingMode.SERVER_MODE) {
        PairingCodeDialog(
            mode = PairingMode.SERVER_MODE,
            deviceManager = deviceManager,
            pairingCode = state.serverPairingCode,
            show = state.showPairingCodeDialog,
            onDismiss = {
                deviceManager.cancelPendingPairing()
                state.showPairingCodeDialog = false
            },
            onPairingComplete = { success, _ ->
                if (success) {
                    state.showPairingCodeDialog = false
                    try {
                        deviceManager.triggerDeviceListRefresh()
                        val authMap = deviceManager.getAuthenticatedDevices()
                        onAuthedUuidsChange(authMap.filter { (_, auth) -> auth.isAccepted }.keys.toSet())
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }

    // 配对码对话框 - 客户端模式
    if (state.showPairingCodeDialog && state.pairingCodeDialogMode == PairingMode.CLIENT_MODE && state.pendingConnectDevice != null) {
        PairingCodeDialog(
            mode = PairingMode.CLIENT_MODE,
            deviceManager = deviceManager,
            targetDevice = state.pendingConnectDevice,
            show = state.showPairingCodeDialog,
            onDismiss = {
                state.showPairingCodeDialog = false
                state.pendingConnectDevice = null
            },
            onPairingComplete = { success: Boolean, _: String ->
                if (success) {
                    state.showPairingCodeDialog = false
                    state.pendingConnectDevice = null
                    try {
                        deviceManager.triggerDeviceListRefresh()
                        val authMap = deviceManager.getAuthenticatedDevices()
                        onAuthedUuidsChange(authMap.filter { (_, auth) -> auth.isAccepted }.keys.toSet())
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }
}

/**
 * 已拒绝设备对话框：查看并恢复被拒绝的设备。
 *
 * @param findOtherUuidsWithSameIp 查找与本设备同 IP 的其它已认证设备 uuid（原为外层局部函数）。
 */
@Composable
internal fun RejectedDeviceRestoreDialog(
    state: DeviceListScreenState,
    deviceManager: DeviceConnectionManager,
    rejectedDevices: List<DeviceInfo>,
    findOtherUuidsWithSameIp: (ip: String, exceptUuid: String) -> List<String>,
    onRejectedUuidsChange: (Set<String>) -> Unit,
) {
    if (state.showRejectedDialog) {
        val showDialog = remember { mutableStateOf(true) }
        RejectedDevicesDialog(
            showDialog = showDialog,
            rejectedDevices = rejectedDevices,
            onRestoreDevice = { device ->
                val allUuids = findOtherUuidsWithSameIp(device.ip, "") + device.uuid
                onRejectedUuidsChange(deviceManager.restoreRejectedDevices(allUuids.distinct()))
            },
            onDismiss = {
                showDialog.value = false
                state.showRejectedDialog = false
            },
        )
    }
}

/**
 * 删除设备确认对话框。
 *
 * @param onRemoveAuthedUuid 删除成功后从已认证 uuid 集合中移除该设备。
 * @param onClose 关闭对话框并清空选中/待删除状态（原为内联的四处状态写入）。
 */
@Composable
internal fun DeleteDeviceConfirmDialog(
    show: Boolean,
    device: DeviceInfo,
    deviceManager: DeviceConnectionManager,
    coroutineScope: CoroutineScope,
    onRemoveAuthedUuid: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current

    WindowDialog(
        show = show,
        title = "删除设备",
        summary = "是否同时删除「${device.displayName}」的通知历史？",
        titleColor = DialogDefaults.titleColor(),
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = onClose,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = "仅删除设备",
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val removed = deviceManager.removeAuthenticatedDevice(device.uuid, deleteHistory = false)
                                    if (removed) {
                                        onRemoveAuthedUuid(device.uuid)
                                    } else {
                                        ToastUtils.showShortToast(context, "删除设备失败: 设备不存在或持久化删除未完成，请重试")
                                    }
                                } catch (e: Exception) {
                                    ToastUtils.showShortToast(context, "删除设备失败: ${e.message ?: "未知错误"}")
                                }
                                onClose()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除并清除历史",
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val removed = deviceManager.removeAuthenticatedDevice(device.uuid, deleteHistory = true)
                                    if (removed) {
                                        onRemoveAuthedUuid(device.uuid)
                                    } else {
                                        ToastUtils.showShortToast(context, "删除设备失败: 设备不存在或持久化删除未完成，请重试")
                                    }
                                } catch (e: Exception) {
                                    ToastUtils.showShortToast(context, "删除设备失败: ${e.message ?: "未知错误"}")
                                }
                                onClose()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}
