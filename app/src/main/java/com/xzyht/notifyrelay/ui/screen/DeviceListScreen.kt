package com.xzyht.notifyrelay.ui.screen

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzyht.notifyrelay.R
import com.xzyht.notifyrelay.feature.device.model.HandshakeRequest
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import com.xzyht.notifyrelay.ui.dialog.ConnectDeviceDialog
import com.xzyht.notifyrelay.ui.dialog.HandshakeRequestDialog
import com.xzyht.notifyrelay.ui.dialog.RejectedDevicesDialog
import com.xzyht.notifyrelay.ui.navigation.Navigator
import notifyrelay.base.util.ToastUtils
import notifyrelay.core.util.BatteryIconConverter
import notifyrelay.core.util.BatteryUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局设备选中状态单例
 */
object GlobalSelectedDeviceHolder {
    private var _selectedDevice by mutableStateOf<DeviceInfo?>(null)
    var selectedDevice: DeviceInfo?
        get() = _selectedDevice
        set(value) { _selectedDevice = value }

    /**
     * Compose可组合函数，供其他页面监听选中设备变化。
     */
    @Composable
    fun current(): State<DeviceInfo?> {
        rememberUpdatedState(_selectedDevice)
        return remember { object : State<DeviceInfo?> {
            override val value: DeviceInfo? get() = _selectedDevice
        } }
    }
}

/**
 * 设备列表屏幕状态管理类
 * 用于在父组件和子组件之间共享弹窗状态
 */
class DeviceListScreenState {
    var showConnectDialog by mutableStateOf(false)
        internal set
    var pendingConnectDevice by mutableStateOf<DeviceInfo?>(null)
        internal set
    var showHandshakeDialog by mutableStateOf(false)
        internal set
    var pendingHandshakeRequest by mutableStateOf<HandshakeRequest?>(null)
        internal set
    var showRejectedDialog by mutableStateOf(false)
        internal set
    
    /**
     * 检查是否有任何弹窗显示
     */
    fun hasAnyDialogShowing(): Boolean {
        return showConnectDialog || showHandshakeDialog || showRejectedDialog
    }
    
    /**
     * 关闭所有弹窗
     */
    fun dismissAllDialogs() {
        showConnectDialog = false
        showHandshakeDialog = false
        showRejectedDialog = false
        pendingConnectDevice = null
        pendingHandshakeRequest = null
    }
}

/**
 * 设备列表屏幕
 * 纯 Compose 实现，弹窗返回逻辑由父组件统一处理
 * 支持横屏（左侧列表）和竖屏（顶部列表）两种布局
 */
@Composable
fun DeviceListScreen(
    navigator: Navigator,
    state: DeviceListScreenState = remember { DeviceListScreenState() }
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val deviceManager = remember { DeviceConnectionManagerSingleton.getDeviceManager(context) }
    
    var authedDeviceUuids by rememberSaveable { mutableStateOf(setOf<String>()) }
    var rejectedDeviceUuids by rememberSaveable { mutableStateOf(setOf<String>()) }
    var udpDiscoveryEnabled by remember { mutableStateOf(true) }
    
    val deviceMap: Map<String, Pair<DeviceInfo, Boolean>> by deviceManager.devices.collectAsState(initial = emptyMap())
    val devices: List<DeviceInfo> = deviceMap.values.map { it.first }
    val deviceStates: Map<String, Boolean> = deviceMap.mapValues { it.value.second }
    var selectedDevice by remember { mutableStateOf(GlobalSelectedDeviceHolder.selectedDevice) }
    
    val localBatteryLevel = remember {
        BatteryUtils.getBatteryLevel(context)
    }
    
    val allDevices: List<DeviceInfo?> = listOf<DeviceInfo?>(null) + devices
    val validAuthedDeviceUuids = authedDeviceUuids.intersect(devices.map { it.uuid }.toSet())
    val unauthedDevices = if (udpDiscoveryEnabled) {
        devices.filter { d ->
            !validAuthedDeviceUuids.contains(d.uuid) && !rejectedDeviceUuids.contains(d.uuid)
        }
    } else {
        emptyList()
    }
    val rejectedDevices = rejectedDeviceUuids.mapNotNull { uuid ->
        devices.find { it.uuid == uuid } ?: DeviceInfo(uuid, "未知设备", "", 0)
    }
    
    fun findOtherUuidsWithSameIp(ip: String, exceptUuid: String): List<String> {
        return deviceMap.values.map { it.first }
            .filter { it.ip == ip && it.uuid != exceptUuid && authedDeviceUuids.contains(it.uuid) }
            .map { it.uuid }
    }
    
    LaunchedEffect(deviceMap, state.showRejectedDialog) {
        val authMap = deviceManager.getAuthenticatedDevices()
        authedDeviceUuids = authMap.filter { (_, auth) -> auth.isAccepted }.keys.toSet()
        rejectedDeviceUuids = deviceManager.getRejectedDevices()
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(deviceManager) {
        val handler = object : DeviceConnectionManager.HandshakeRequestHandler {
            override fun onHandshakeRequest(deviceInfo: DeviceInfo, publicKey: String, callback: (Boolean) -> Unit) {
                mainHandler.post {
                    state.pendingHandshakeRequest = HandshakeRequest(deviceInfo, publicKey, callback)
                    state.showHandshakeDialog = true
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

    fun isAuthed(uuid: String) = authedDeviceUuids.contains(uuid)

    val onSelectDevice: (DeviceInfo?) -> Unit = { deviceInfo ->
        if (deviceInfo == null) {
            selectedDevice = null
            GlobalSelectedDeviceHolder.selectedDevice = null
        } else if (authedDeviceUuids.contains(deviceInfo.uuid)) {
            selectedDevice = deviceInfo
            GlobalSelectedDeviceHolder.selectedDevice = deviceInfo
        } else {
            state.pendingConnectDevice = deviceInfo
            state.showConnectDialog = true
        }
    }

    val buttonMinHeight = 44.dp

    @Composable
    fun UdpDiscoverySwitch() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = "显示未认证设备",
                style = textStyles.body2,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = udpDiscoveryEnabled,
                onCheckedChange = { udpDiscoveryEnabled = it }
            )
        }
    }

    @Composable
    fun LocalDeviceButton() {
        val batteryLevel = localBatteryLevel
        val isCharging = remember {
            mutableStateOf(if (BatteryUtils.isCharging(context)) '1' else '0')
        }
        val batteryIcon = BatteryIconConverter.getBatteryIcon(batteryLevel, isCharging.value)
        
        val buttonColors = if (selectedDevice == null) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        }
        
        val buttonModifier = if (isLandscape) {
            Modifier
                .defaultMinSize(minHeight = buttonMinHeight)
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        } else {
            Modifier
                .defaultMinSize(minHeight = buttonMinHeight)
                .wrapContentWidth()
                .padding(end = 6.dp)
        }
        
        Column(
            horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Button(
                onClick = { onSelectDevice(null) },
                modifier = buttonModifier,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                colors = buttonColors
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "本机",
                        style = textStyles.body2.copy(color = if (selectedDevice == null) colorScheme.onPrimary else colorScheme.primary),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun AuthenticatedDeviceButton(device: DeviceInfo) {
        val isOnline = deviceStates[device.uuid] == true
        val context = LocalContext.current
        
        val batteryLevel = remember { mutableStateOf(100) }
        val isCharging = remember { mutableStateOf(device.chargingStatus) }

        LaunchedEffect(device.batteryLevel) {
            if (device.batteryLevel != -1 && device.batteryLevel != batteryLevel.value) {
                batteryLevel.value = device.batteryLevel.coerceIn(0, 100)
            }
        }
        
        LaunchedEffect(device.chargingStatus) {
            if (device.chargingStatus != '*' && device.chargingStatus != isCharging.value) {
                isCharging.value = device.chargingStatus
            }
        }
        
        val batteryIcon = BatteryIconConverter.getBatteryIcon(batteryLevel.value, isCharging.value)
        
        val buttonColors = if (selectedDevice?.uuid == device.uuid) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        }
        
        Row(
            verticalAlignment = Alignment.Top,
            modifier = if (isLandscape) Modifier.padding(bottom = 4.dp) else Modifier.padding(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val buttonModifier = if (isLandscape) {
                Modifier
                    .defaultMinSize(minHeight = buttonMinHeight)
                    .fillMaxWidth()
            } else {
                Modifier
                    .defaultMinSize(minHeight = buttonMinHeight)
                    .wrapContentWidth()
            }
            
            Column(
                modifier = if (isLandscape) Modifier.weight(1f) else Modifier,
                horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Button(
                    onClick = { onSelectDevice(device) },
                    modifier = buttonModifier,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = buttonColors
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isOnline) {
                            Text(
                                text = batteryIcon,
                                fontFamily = FontFamily(Font(resId = R.font.segsmdl2)),
                                fontSize = 16.sp,
                                color = BatteryIconConverter.getBatteryColor(batteryLevel.value),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            text = device.displayName + if (!isOnline) " (离线)" else "",
                            style = textStyles.body2.copy(color = if (selectedDevice?.uuid == device.uuid) colorScheme.onPrimary else colorScheme.primary),
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            if (selectedDevice?.uuid == device.uuid) {
                Spacer(Modifier.width(4.dp))
                DoubleClickConfirmButton(
                    text = "删除",
                    confirmText = "确认?",
                    onClick = {},
                    onConfirm = {
                        try {
                            val removed = deviceManager.removeAuthenticatedDevice(device.uuid)
                            if (removed) {
                                authedDeviceUuids = authedDeviceUuids - device.uuid
                            } else {
                                ToastUtils.showShortToast(context, "删除设备失败: 设备不存在或已被删除")
                            }
                        } catch (e: Exception) {
                            ToastUtils.showShortToast(context, "删除设备失败: ${e.message ?: "未知错误"}")
                        }
                        selectedDevice = null
                        GlobalSelectedDeviceHolder.selectedDevice = null
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = buttonMinHeight, minWidth = 60.dp)
                        .heightIn(min = buttonMinHeight)
                        .widthIn(min = 60.dp),
                    colors = ButtonDefaults.buttonColors(color = Color.Red),
                    confirmColors = ButtonDefaults.buttonColors(color = Color(0xFFFF0000)),
                    textColor = Color.White,
                    confirmTextColor = Color.White
                )
            }
        }
    }

    @Composable
    fun UnauthenticatedDeviceButton(device: DeviceInfo) {
        val isOnline = deviceStates[device.uuid] == true
        Button(
            onClick = { onSelectDevice(device) },
            modifier = Modifier
                .then(if (isLandscape) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = buttonMinHeight)
                .then(if (isLandscape) Modifier.padding(vertical = 2.dp) else Modifier.padding(end = 6.dp)),
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(color = colorScheme.surface)
        ) {
            Text(
                device.displayName + if (!isOnline) " (离线)" else "",
                style = textStyles.body2.copy(color = colorScheme.primary),
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun RejectedDevicesButton() {
        Button(
            onClick = { state.showRejectedDialog = true },
            modifier = Modifier
                .then(if (isLandscape) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = buttonMinHeight)
                .then(if (isLandscape) Modifier.padding(vertical = 2.dp) else Modifier.padding(end = 6.dp)),
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(color = colorScheme.secondaryContainer)
        ) {
            Text(
                "查看已拒绝设备",
                style = textStyles.body2.copy(color = colorScheme.secondary),
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            UdpDiscoverySwitch()
            LocalDeviceButton()
            
            allDevices.forEach { device: DeviceInfo? ->
                if (device != null && authedDeviceUuids.contains(device.uuid)) {
                    AuthenticatedDeviceButton(device)
                }
            }
            
            unauthedDevices.forEach {
                UnauthenticatedDeviceButton(it)
            }
            
            RejectedDevicesButton()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.background)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
        ) {
            UdpDiscoverySwitch()
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                item { LocalDeviceButton() }
                
                items(allDevices.filterNotNull().filter { authedDeviceUuids.contains(it.uuid) }) {
                    AuthenticatedDeviceButton(it)
                }
                
                items(unauthedDevices) {
                    UnauthenticatedDeviceButton(it)
                }
                
                item { RejectedDevicesButton() }
            }
        }
    }

    if (state.showConnectDialog && state.pendingConnectDevice != null) {
        val activity = LocalContext.current as? Activity
        val showDialog = remember { mutableStateOf(true) }
        ConnectDeviceDialog(
            showDialog = showDialog,
            device = state.pendingConnectDevice,
            onConnect = { device ->
                try {
                    val field = deviceManager.javaClass.getDeclaredField("authenticatedDevices")
                    field.isAccessible = true
                    val rawMap = field.get(deviceManager)
                    val safeMap = if (rawMap is MutableMap<*, *>) {
                        val m = mutableMapOf<String, Any?>()
                        for ((k, v) in rawMap) {
                            if (k is String) m[k] = v
                        }
                        m
                    } else mutableMapOf()
                    val oldUuids = findOtherUuidsWithSameIp(device.ip, "") + device.uuid
                    val appContext = context.applicationContext
                    for (uuid in oldUuids.distinct()) {
                        safeMap.remove(uuid)
                        try {
                            val notificationDataClass = Class.forName("com.xzyht.notifyrelay.feature.device.model.NotificationData")
                            val getInstance = notificationDataClass.getDeclaredMethod("getInstance", Context::class.java)
                            val notificationData = getInstance.invoke(null, appContext)
                            val clearDeviceHistory = notificationDataClass.getDeclaredMethod("clearDeviceHistory", String::class.java, Context::class.java)
                            clearDeviceHistory.invoke(notificationData, uuid, appContext)
                        } catch (_: Exception) {}
                    }
                    deviceManager.saveAuthedDevicesPublic()
                    deviceManager.updateDeviceListPublic()
                } catch (_: Exception) {}
                deviceManager.connectToDevice(device) { success, msg ->
                    if (!success && msg != null && activity != null) {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "连接失败: $msg", Toast.LENGTH_SHORT).show()
                        }
                    } else if (success) {
                        try {
                            val field = deviceManager.javaClass.getDeclaredField("authenticatedDevices")
                            field.isAccessible = true
                            val rawMap = field.get(deviceManager)
                            @Suppress("UNCHECKED_CAST")
                            val map = if (rawMap is Map<*, *>) rawMap as Map<String, *> else null
                            authedDeviceUuids = map?.filter { entry ->
                                val v = entry.value
                                v?.let {
                                    val isAcceptedField = v.javaClass.getDeclaredField("isAccepted").apply { isAccessible = true }
                                    isAcceptedField.getBoolean(v)
                                } ?: false
                            }?.keys?.toSet() ?: emptySet()
                        } catch (_: Exception) {}
                    }
                    showDialog.value = false
                    state.showConnectDialog = false
                    state.pendingConnectDevice = null
                }
            },
            onDismiss = {
                showDialog.value = false
                state.showConnectDialog = false
                state.pendingConnectDevice = null
            }
        )
    }

    if (state.showHandshakeDialog && state.pendingHandshakeRequest != null) {
        val req = state.pendingHandshakeRequest!!
        val showDialog = remember { mutableStateOf(true) }
        HandshakeRequestDialog(
            showDialog = showDialog,
            handshakeRequest = req,
            onAccept = { handshakeReq ->
                try {
                    val field = deviceManager.javaClass.getDeclaredField("authenticatedDevices")
                    field.isAccessible = true
                    val rawMap = field.get(deviceManager)
                    val safeMap = if (rawMap is MutableMap<*, *>) {
                        val m = mutableMapOf<String, Any?>()
                        for ((k, v) in rawMap) {
                            if (k is String) m[k] = v
                        }
                        m
                    } else mutableMapOf()
                    val allUuidsToRemove = findOtherUuidsWithSameIp(handshakeReq.device.uuid, "") + handshakeReq.device.uuid
                    val appContext = context.applicationContext
                    for (uuid in allUuidsToRemove.distinct()) {
                        safeMap.remove(uuid)
                        try {
                            val notificationDataClass = Class.forName("com.xzyht.notifyrelay.feature.device.model.NotificationData")
                            val getInstance = notificationDataClass.getDeclaredMethod("getInstance", Context::class.java)
                            val notificationData = getInstance.invoke(null, appContext)
                            val clearDeviceHistory = notificationDataClass.getDeclaredMethod("clearDeviceHistory", String::class.java, Context::class.java)
                            clearDeviceHistory.invoke(notificationData, uuid, appContext)
                        } catch (_: Exception) {}
                        deviceManager.saveAuthedDevicesPublic()
                        deviceManager.updateDeviceListPublic()
                    }
                } catch (_: Exception) {}
                handshakeReq.callback(true)
                deviceManager.updateDeviceListPublic()
                try {
                    val authMap = deviceManager.getAuthenticatedDevices()
                    authedDeviceUuids = authMap.filter { (_, auth) -> auth.isAccepted }.keys.toSet()
                } catch (_: Exception) {}
            },
            onReject = { handshakeReq ->
                handshakeReq.callback(false)
            },
            onDismiss = {
                showDialog.value = false
                state.showHandshakeDialog = false
                state.pendingHandshakeRequest = null
            }
        )
    }

    if (state.showRejectedDialog) {
        val showDialog = remember { mutableStateOf(true) }
        RejectedDevicesDialog(
            showDialog = showDialog,
            rejectedDevices = rejectedDevices,
            onRestoreDevice = { device ->
                val field = deviceManager.javaClass.getDeclaredField("rejectedDevices")
                field.isAccessible = true
                val rawSet = field.get(deviceManager)
                if (rawSet is MutableSet<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val ms = rawSet as MutableSet<String>
                    val allUuids = findOtherUuidsWithSameIp(device.ip, "") + device.uuid
                    allUuids.distinct().forEach { ms.remove(it) }
                }
                @Suppress("UNCHECKED_CAST")
                rejectedDeviceUuids = if (rawSet is MutableSet<*>) (rawSet as MutableSet<String>).toSet() else emptySet()
            },
            onDismiss = {
                showDialog.value = false
                state.showRejectedDialog = false
            }
        )
    }
}
