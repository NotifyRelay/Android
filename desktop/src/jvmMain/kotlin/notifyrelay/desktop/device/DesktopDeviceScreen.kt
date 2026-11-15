package notifyrelay.desktop.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.screen.DeviceListContentArgs
import notifyrelay.shared.ui.screen.DeviceListScreen
import notifyrelay.shared.ui.screen.DeviceListTabContent
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder

/**
 * 桌面端设备列表页。
 *
 * 复用公共端 [DeviceListScreen] 的交互与状态管理；内容渲染与 Android 共用
 * [DeviceListTabContent]，只在选中设备时额外同步全局选中态并切页。
 */
@Composable
fun DesktopDeviceScreen(
    navigator: Navigator,
    onEditDevice: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DeviceListScreen(
            navigator = navigator,
            content = { args: DeviceListContentArgs ->
                DeviceListTabContent(
                    args = args,
                    onSelectDevice = { device ->
                        args.onSelectDevice(device)
                        GlobalSelectedDeviceHolder.selectedDevice = device
                        onEditDevice()
                    },
                )
            },
        )
    }
}
