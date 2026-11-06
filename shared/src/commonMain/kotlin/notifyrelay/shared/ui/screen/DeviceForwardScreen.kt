package notifyrelay.shared.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.pages.RemoteAppsPage
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import notifyrelay.shared.ui.viewmodel.DevicePortsViewModel
import notifyrelay.shared.ui.viewmodel.RemoteFilterViewModel
import notifyrelay.shared.ui.viewmodel.devicePortsViewModel

@Composable
fun DeviceForwardScreen(
    portsViewModel: DevicePortsViewModel = devicePortsViewModel(),
    filterViewModel: RemoteFilterViewModel,
    clipboardSync: @Composable () -> Unit,
    musicControl: @Composable () -> Unit,
    scrcpyScreen: @Composable (notifyrelay.domain.feature.device.model.SelectedDeviceInfo?, () -> Unit) -> Unit,
) {
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        if (!filterViewModel.isConfigLoaded()) {
            filterViewModel.ensureLoaded()
        }
    }

    val tabTitles = listOf("剪贴板同步", "音乐控制", "屏幕镜像", "应用列表")

    val deviceManager = remember { portsViewModel.adminPort() }
    val selectedDeviceState = GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceState.value

    val selectedDeviceInfo =
        selectedDevice?.let {
            val authInfo = deviceManager.getAuthenticatedDevices()[it.uuid]
            notifyrelay.domain.feature.device.model.SelectedDeviceInfo(
                displayName = it.displayName,
                ip = it.ip,
                deviceType = authInfo?.deviceType,
            )
        }

    // 每页内容各自套一层内边距与左上对齐：页签容器不假设内容的布局。
    TabbedScreen(tabTitles) { page ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when (page) {
                0 -> clipboardSync()
                1 -> musicControl()
                2 -> scrcpyScreen(selectedDeviceInfo) { navigator.push(Route.ScrcpyAdvanced) }
                3 ->
                    RemoteAppsPage(
                        deviceUuid = selectedDevice?.uuid,
                        deviceIp = selectedDeviceInfo?.ip,
                    )
            }
        }
    }
}
