package com.xzyht.notifyrelay.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.ui.dialog.PairingMode

/**
 * 设备列表屏幕状态管理类
 * 用于在父组件和子组件之间共享弹窗状态
 */
class DeviceListScreenState {
    var pendingConnectDevice by mutableStateOf<DeviceInfo?>(null)
        internal set
    var showRejectedDialog by mutableStateOf(false)
        internal set
    var showPairingCodeDialog by mutableStateOf(false)
        internal set
    var pairingCodeDialogMode by mutableStateOf(PairingMode.SERVER_MODE)
        internal set
    var serverPairingCode by mutableStateOf("")
        internal set

    /**
     * 检查是否有任何弹窗显示
     */
    fun hasAnyDialogShowing(): Boolean = showRejectedDialog || showPairingCodeDialog

    /**
     * 关闭所有弹窗
     */
    fun dismissAllDialogs() {
        showRejectedDialog = false
        showPairingCodeDialog = false
        pendingConnectDevice = null
    }
}
