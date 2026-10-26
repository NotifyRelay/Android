package notifyrelay.shared.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.device.service.callback.HandshakeRequestHandler
import notifyrelay.shared.ui.dialog.PairingMode
import notifyrelay.shared.ui.navigation.Navigator
import notifyrelay.shared.ui.screen.DeviceListScreenState
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import notifyrelay.shared.ui.screen.devicelist.DeleteDeviceConfirmDialog
import notifyrelay.shared.ui.screen.devicelist.DeviceListPairingCodeDialog
import notifyrelay.shared.ui.screen.devicelist.LocalDeviceListPort
import notifyrelay.shared.ui.screen.devicelist.RejectedDeviceRestoreDialog
import notifyrelay.shared.ui.viewmodel.DevicePortsViewModel
import notifyrelay.shared.ui.viewmodel.devicePortsViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备列表屏幕
 * 纯 Compose 实现，弹窗返回逻辑由父组件统一处理
 * 支持横屏（左侧列表）和竖屏（顶部列表）两种布局
 */
@Composable
fun DeviceListScreen(
    navigator: Navigator,
    state: DeviceListScreenState = remember { DeviceListScreenState() },
    viewModel: DevicePortsViewModel = devicePortsViewModel(),
    content: @Composable (DeviceListContentArgs) -> Unit,
) {
    val deviceListPort = LocalDeviceListPort.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val deviceManager = remember { viewModel.adminPort() }
    val coroutineScope = rememberCoroutineScope()

    var authedDeviceUuids by rememberSaveable { mutableStateOf(setOf<String>()) }
    var rejectedDeviceUuids by rememberSaveable { mutableStateOf(setOf<String>()) }
    var discoveryEnabled by remember { mutableStateOf(true) }

    val deviceMap: Map<String, Pair<DeviceInfo, Boolean>> by deviceManager.devices.collectAsState(initial = emptyMap())
    val devices: List<DeviceInfo> = deviceMap.values.map { it.first }
    val deviceStates: Map<String, Boolean> = deviceMap.mapValues { it.value.second }
    var selectedDevice by remember { mutableStateOf(GlobalSelectedDeviceHolder.selectedDevice) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var pendingDeleteDevice by remember { mutableStateOf<DeviceInfo?>(null) }

    val localBatteryLevel =
        remember {
            deviceListPort?.localBatteryLevel() ?: 0
        }

    val allDevices: List<DeviceInfo?> = listOf<DeviceInfo?>(null) + devices
    val validAuthedDeviceUuids = authedDeviceUuids.intersect(devices.map { it.uuid }.toSet())
    val unauthedDevices =
        if (discoveryEnabled) {
            devices.filter { d ->
                !validAuthedDeviceUuids.contains(d.uuid) && !rejectedDeviceUuids.contains(d.uuid)
            }
        } else {
            emptyList()
        }
    val rejectedDevices =
        rejectedDeviceUuids.mapNotNull { uuid ->
            devices.find { it.uuid == uuid } ?: DeviceInfo(uuid, "未知设备", "", 0)
        }

    fun findOtherUuidsWithSameIp(
        ip: String,
        exceptUuid: String,
    ): List<String> =
        deviceMap.values
            .map { it.first }
            .filter { it.ip == ip && it.uuid != exceptUuid && authedDeviceUuids.contains(it.uuid) }
            .map { it.uuid }

    LaunchedEffect(deviceMap, state.showRejectedDialog) {
        val authMap = deviceManager.getAuthenticatedDevices()
        authedDeviceUuids = authMap.filter { (_, auth) -> auth.isAccepted }.keys.toSet()
        rejectedDeviceUuids = deviceManager.getRejectedDevices()
    }

    // 有未认证设备连接时：生成配对码并弹出显示

    DisposableEffect(deviceManager) {
        val handler =
            object : HandshakeRequestHandler {
                override fun onPairingInitRequest(
                    deviceInfo: DeviceInfo,
                    tmpPublicKey: String,
                ) {
                    coroutineScope.launch {
                        state.pendingConnectDevice = deviceInfo
                        state.pairingCodeDialogMode = PairingMode.SERVER_MODE
                        state.showPairingCodeDialog = true
                    }
                }
            }
        deviceManager.handshakeRequestHandler = handler
        onDispose {
            if (deviceManager.handshakeRequestHandler === handler) {
                deviceManager.handshakeRequestHandler = null
            }
        }
    }

    val isLandscape = deviceListPort?.isLandscape() ?: false

    val onSelectDevice: (DeviceInfo?) -> Unit = { deviceInfo ->
        if (deviceInfo == null) {
            selectedDevice = null
            GlobalSelectedDeviceHolder.selectedDevice = null
        } else if (authedDeviceUuids.contains(deviceInfo.uuid)) {
            selectedDevice = deviceInfo
            GlobalSelectedDeviceHolder.selectedDevice = deviceInfo
        } else {
            state.pendingConnectDevice = deviceInfo
            state.pairingCodeDialogMode = PairingMode.CLIENT_MODE
            state.showPairingCodeDialog = true
        }
    }

    val onRequestDelete: (DeviceInfo) -> Unit = { device ->
        pendingDeleteDevice = device
        showDeleteHistoryDialog = true
    }

    content(
        DeviceListContentArgs(
            isLandscape = isLandscape,
            discoveryEnabled = discoveryEnabled,
            onDiscoveryChange = { discoveryEnabled = it },
            allDevices = allDevices,
            authedDeviceUuids = authedDeviceUuids,
            deviceStates = deviceStates,
            unauthedDevices = unauthedDevices,
            selectedDevice = selectedDevice,
            onSelectDevice = onSelectDevice,
            onRequestDelete = onRequestDelete,
            onShowRejectedDialog = { state.showRejectedDialog = true },
            localBatteryLevel = localBatteryLevel,
        ),
    )

    DeviceListPairingCodeDialog(
        state = state,
        deviceManager = deviceManager,
        onAuthedUuidsChange = { authedDeviceUuids = it },
    )

    RejectedDeviceRestoreDialog(
        state = state,
        deviceManager = deviceManager,
        rejectedDevices = rejectedDevices,
        findOtherUuidsWithSameIp = ::findOtherUuidsWithSameIp,
        onRejectedUuidsChange = { rejectedDeviceUuids = it },
    )

    // 删除设备历史确认弹窗
    if (showDeleteHistoryDialog && pendingDeleteDevice != null) {
        DeleteDeviceConfirmDialog(
            show = showDeleteHistoryDialog,
            device = pendingDeleteDevice!!,
            deviceManager = deviceManager,
            coroutineScope = coroutineScope,
            onRemoveAuthedUuid = { authedDeviceUuids = authedDeviceUuids - it },
            onClose = {
                selectedDevice = null
                GlobalSelectedDeviceHolder.selectedDevice = null
                showDeleteHistoryDialog = false
                pendingDeleteDevice = null
            },
        )
    }
}
