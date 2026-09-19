package com.xzyht.notifyrelay.ui.screen

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.device.service.callback.HandshakeRequestHandler
import com.xzyht.notifyrelay.ui.dialog.PairingMode
import com.xzyht.notifyrelay.ui.navigation.Navigator
import notifyrelay.base.util.BatteryUtils
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
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val deviceManager = remember { DeviceConnectionManagerSingleton.getDeviceManager(context) }
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
            BatteryUtils.getBatteryLevel(context)
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
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(deviceManager) {
        val handler =
            object : HandshakeRequestHandler {
                override fun onPairingInitRequest(
                    deviceInfo: DeviceInfo,
                    tmpPublicKey: String,
                ) {
                    mainHandler.post {
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    DeviceListScreenContent(
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
        colorScheme = colorScheme,
        textStyles = textStyles,
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
