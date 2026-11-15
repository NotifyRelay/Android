package notifyrelay.shared.ui.screen

import androidx.compose.runtime.Composable
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.pages.history.NotificationHistoryScreen
import notifyrelay.shared.ui.pages.history.SuperIslandHistoryScreen
import notifyrelay.shared.ui.screen.devicelist.DeviceListScreenContent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备列表页签内容。
 *
 * 两端的渲染参数与来源完全一致，差异只在于宿主何时取到 [DeviceListContentArgs]；
 * 取值仍由各自的导航图负责，这里只负责把参数展开成页面。
 *
 * @param args 设备列表的渲染参数。
 * @param onSelectDevice 选中设备的回调；桌面端在此额外同步全局选中态并切页。
 */
@Composable
fun DeviceListTabContent(
    args: DeviceListContentArgs,
    onSelectDevice: (notifyrelay.domain.feature.device.model.DeviceInfo?) -> Unit = args.onSelectDevice,
) {
    DeviceListScreenContent(
        isLandscape = args.isLandscape,
        discoveryEnabled = args.discoveryEnabled,
        onDiscoveryChange = args.onDiscoveryChange,
        allDevices = args.allDevices,
        authedDeviceUuids = args.authedDeviceUuids,
        deviceStates = args.deviceStates,
        unauthedDevices = args.unauthedDevices,
        selectedDevice = args.selectedDevice,
        onSelectDevice = onSelectDevice,
        onRequestDelete = args.onRequestDelete,
        onShowRejectedDialog = args.onShowRejectedDialog,
        localBatteryLevel = args.localBatteryLevel,
        colorScheme = MiuixTheme.colorScheme,
        textStyles = MiuixTheme.textStyles,
    )
}

/**
 * 历史页签内容：通知历史、超级岛历史与聊天测试三个子页。
 *
 * 前两个子页两端同源；聊天测试依赖平台的消息发送能力，由调用方注入。
 *
 * @param navigator 当前导航器。
 * @param chatTest 聊天测试子页。
 */
@Composable
fun HistoryTabContent(
    navigator: Navigator,
    chatTest: @Composable () -> Unit,
) {
    HistoryScreen(
        navigator,
        notificationHistory = { NotificationHistoryScreen() },
        superIslandHistory = { SuperIslandHistoryScreen() },
        chatTest = chatTest,
    )
}
