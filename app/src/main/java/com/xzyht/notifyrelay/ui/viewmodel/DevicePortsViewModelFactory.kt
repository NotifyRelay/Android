package com.xzyht.notifyrelay.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.feature.device.service.AndroidDevicePortsProvider
import notifyrelay.shared.ui.viewmodel.DevicePortsViewModel

/** 以当前 Context 构造 [DevicePortsViewModel]。 */
@Composable
fun devicePortsViewModel(): DevicePortsViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DevicePortsViewModel(AndroidDevicePortsProvider(context)) as T
            },
    )
}
