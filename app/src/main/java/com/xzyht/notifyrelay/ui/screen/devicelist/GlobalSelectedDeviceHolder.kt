package com.xzyht.notifyrelay.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo

/**
 * 全局设备选中状态单例
 */
object GlobalSelectedDeviceHolder {
    private var _selectedDevice by mutableStateOf<DeviceInfo?>(null)
    var selectedDevice: DeviceInfo?
        get() = _selectedDevice
        set(value) {
            _selectedDevice = value
        }

    /**
     * Compose可组合函数，供其他页面监听选中设备变化。
     */
    @Composable
    fun current(): State<DeviceInfo?> {
        rememberUpdatedState(_selectedDevice)
        return remember {
            object : State<DeviceInfo?> {
                override val value: DeviceInfo? get() = _selectedDevice
            }
        }
    }
}
