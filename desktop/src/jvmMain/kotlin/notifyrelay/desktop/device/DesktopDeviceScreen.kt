package notifyrelay.desktop.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.screen.DeviceListContentArgs
import notifyrelay.shared.ui.screen.DeviceListScreen
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import notifyrelay.shared.ui.screen.devicelist.DeviceListScreenContent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 桌面端设备列表页。
 *
 * 复用公共端 [DeviceListScreen] 的交互与状态管理，内容由 [DeviceListScreenContent] 渲染，
 * 与 Android 端共用同一套设备按钮。
 */
@Composable
fun DesktopDeviceScreen(
    navigator: Navigator,
    onEditDevice: () -> Unit = {},
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    Column(modifier = Modifier.fillMaxSize()) {
        DeviceListScreen(
            navigator = navigator,
            content = { args: DeviceListContentArgs ->
                DeviceListScreenContent(
                    isLandscape = args.isLandscape,
                    discoveryEnabled = args.discoveryEnabled,
                    onDiscoveryChange = args.onDiscoveryChange,
                    allDevices = args.allDevices,
                    authedDeviceUuids = args.authedDeviceUuids,
                    deviceStates = args.deviceStates,
                    unauthedDevices = args.unauthedDevices,
                    selectedDevice = args.selectedDevice,
                    onSelectDevice = { device ->
                        args.onSelectDevice(device)
                        GlobalSelectedDeviceHolder.selectedDevice = device
                        onEditDevice()
                    },
                    onRequestDelete = args.onRequestDelete,
                    onShowRejectedDialog = args.onShowRejectedDialog,
                    localBatteryLevel = args.localBatteryLevel,
                    colorScheme = colorScheme,
                    textStyles = textStyles,
                )
            },
        )
    }
}
