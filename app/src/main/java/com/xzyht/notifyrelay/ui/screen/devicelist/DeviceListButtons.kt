package com.xzyht.notifyrelay.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzyht.notifyrelay.R
import com.xzyht.notifyrelay.feature.device.model.DeviceInfo
import com.xzyht.notifyrelay.ui.common.DoubleClickConfirmButton
import notifyrelay.core.util.BatteryIconConverter
import notifyrelay.core.util.BatteryUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles

/**
 * 设备列表按钮的最小高度。
 */
private val ButtonMinHeight = 44.dp

/**
 * 设备列表内容区：横屏为可滚动左栏，竖屏为顶部横向列表。
 *
 * 仅负责布局编排与按钮调用，状态 derivation 与副作用保留在 [DeviceListScreen]。
 */
@Composable
internal fun DeviceListScreenContent(
    isLandscape: Boolean,
    discoveryEnabled: Boolean,
    onDiscoveryChange: (Boolean) -> Unit,
    allDevices: List<DeviceInfo?>,
    authedDeviceUuids: Set<String>,
    deviceStates: Map<String, Boolean>,
    unauthedDevices: List<DeviceInfo>,
    selectedDevice: DeviceInfo?,
    onSelectDevice: (DeviceInfo?) -> Unit,
    onRequestDelete: (DeviceInfo) -> Unit,
    onShowRejectedDialog: () -> Unit,
    localBatteryLevel: Int,
    colorScheme: Colors,
    textStyles: TextStyles,
) {
    if (isLandscape) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(colorScheme.background)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            DiscoverySwitch(
                discoveryEnabled = discoveryEnabled,
                onDiscoveryChange = onDiscoveryChange,
                textStyles = textStyles,
            )
            LocalDeviceButton(
                selectedDevice = selectedDevice,
                onSelectDevice = onSelectDevice,
                localBatteryLevel = localBatteryLevel,
                isLandscape = isLandscape,
                colorScheme = colorScheme,
                textStyles = textStyles,
            )

            allDevices.forEach { device: DeviceInfo? ->
                if (device != null && authedDeviceUuids.contains(device.uuid)) {
                    AuthenticatedDeviceButton(
                        device = device,
                        deviceStates = deviceStates,
                        selectedDevice = selectedDevice,
                        onSelectDevice = onSelectDevice,
                        onRequestDelete = onRequestDelete,
                        isLandscape = isLandscape,
                        colorScheme = colorScheme,
                        textStyles = textStyles,
                    )
                }
            }

            unauthedDevices.forEach {
                UnauthenticatedDeviceButton(
                    device = it,
                    deviceStates = deviceStates,
                    onSelectDevice = onSelectDevice,
                    isLandscape = isLandscape,
                    colorScheme = colorScheme,
                    textStyles = textStyles,
                )
            }

            RejectedDevicesButton(
                onShowRejectedDialog = onShowRejectedDialog,
                isLandscape = isLandscape,
                colorScheme = colorScheme,
                textStyles = textStyles,
            )
        }
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colorScheme.background)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        ) {
            DiscoverySwitch(
                discoveryEnabled = discoveryEnabled,
                onDiscoveryChange = onDiscoveryChange,
                textStyles = textStyles,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                item {
                    LocalDeviceButton(
                        selectedDevice = selectedDevice,
                        onSelectDevice = onSelectDevice,
                        localBatteryLevel = localBatteryLevel,
                        isLandscape = isLandscape,
                        colorScheme = colorScheme,
                        textStyles = textStyles,
                    )
                }

                items(allDevices.filterNotNull().filter { authedDeviceUuids.contains(it.uuid) }) {
                    AuthenticatedDeviceButton(
                        device = it,
                        deviceStates = deviceStates,
                        selectedDevice = selectedDevice,
                        onSelectDevice = onSelectDevice,
                        onRequestDelete = onRequestDelete,
                        isLandscape = isLandscape,
                        colorScheme = colorScheme,
                        textStyles = textStyles,
                    )
                }

                items(unauthedDevices) {
                    UnauthenticatedDeviceButton(
                        device = it,
                        deviceStates = deviceStates,
                        onSelectDevice = onSelectDevice,
                        isLandscape = isLandscape,
                        colorScheme = colorScheme,
                        textStyles = textStyles,
                    )
                }

                item {
                    RejectedDevicesButton(
                        onShowRejectedDialog = onShowRejectedDialog,
                        isLandscape = isLandscape,
                        colorScheme = colorScheme,
                        textStyles = textStyles,
                    )
                }
            }
        }
    }
}

/**
 * 「显示未认证设备」开关。
 */
@Composable
internal fun DiscoverySwitch(
    discoveryEnabled: Boolean,
    onDiscoveryChange: (Boolean) -> Unit,
    textStyles: TextStyles,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
    ) {
        Text(
            text = "显示未认证设备",
            style = textStyles.body2,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = discoveryEnabled,
            onCheckedChange = onDiscoveryChange,
        )
    }
}

/**
 * 本机（未指定设备）按钮。
 */
@Composable
internal fun LocalDeviceButton(
    selectedDevice: DeviceInfo?,
    onSelectDevice: (DeviceInfo?) -> Unit,
    localBatteryLevel: Int,
    isLandscape: Boolean,
    colorScheme: Colors,
    textStyles: TextStyles,
) {
    val context = LocalContext.current
    val batteryLevel = localBatteryLevel
    // 充电状态需随系统变化刷新：原实现用 remember 只在首次组合时读一次，插拔电源后图标不再更新。
    // ACTION_BATTERY_CHANGED 为粘性广播，注册后立即回调一次当前状态，故无需额外初始化读取。
    val isCharging = remember { mutableStateOf(if (BatteryUtils.isCharging(context)) '1' else '0') }
    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging.value =
                        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                        ) {
                            '1'
                        } else {
                            '0'
                        }
                }
            }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {
            // 注册失败时保留首次读取的静态值，不影响其余 UI
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }
    val batteryIcon = BatteryIconConverter.getBatteryIcon(batteryLevel, isCharging.value)

    val buttonColors =
        if (selectedDevice == null) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        }

    val buttonModifier =
        if (isLandscape) {
            Modifier
                .defaultMinSize(minHeight = ButtonMinHeight)
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        } else {
            Modifier
                .defaultMinSize(minHeight = ButtonMinHeight)
                .wrapContentWidth()
                .padding(end = 6.dp)
        }

    Column(
        horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Button(
            onClick = { onSelectDevice(null) },
            modifier = buttonModifier,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            colors = buttonColors,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "本机",
                    style = textStyles.body2.copy(color = if (selectedDevice == null) colorScheme.onPrimary else colorScheme.primary),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 已认证设备按钮（含选中态下的删除按钮）。
 */
@Composable
internal fun AuthenticatedDeviceButton(
    device: DeviceInfo,
    deviceStates: Map<String, Boolean>,
    selectedDevice: DeviceInfo?,
    onSelectDevice: (DeviceInfo?) -> Unit,
    onRequestDelete: (DeviceInfo) -> Unit,
    isLandscape: Boolean,
    colorScheme: Colors,
    textStyles: TextStyles,
) {
    val isOnline = deviceStates[device.uuid] == true
    val context = LocalContext.current

    val batteryLevel = remember { mutableIntStateOf(100) }
    val isCharging = remember { mutableStateOf(device.chargingStatus) }

    LaunchedEffect(device.batteryLevel) {
        // 未知电量（超出 [-100,100]）不更新显示
        val level = kotlin.math.abs(device.batteryLevel)
        if (level <= 100 && level != batteryLevel.intValue) {
            batteryLevel.intValue = level
        }
    }

    LaunchedEffect(device.chargingStatus) {
        if (device.chargingStatus != '*' && device.chargingStatus != isCharging.value) {
            isCharging.value = device.chargingStatus
        }
    }

    val batteryIcon = BatteryIconConverter.getBatteryIcon(batteryLevel.intValue, isCharging.value)

    val buttonColors =
        if (selectedDevice?.uuid == device.uuid) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = if (isLandscape) Modifier.padding(bottom = 4.dp) else Modifier.padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val buttonModifier =
            if (isLandscape) {
                Modifier
                    .defaultMinSize(minHeight = ButtonMinHeight)
                    .fillMaxWidth()
            } else {
                Modifier
                    .defaultMinSize(minHeight = ButtonMinHeight)
                    .wrapContentWidth()
            }

        Column(
            modifier = if (isLandscape) Modifier.weight(1f) else Modifier,
            horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Button(
                onClick = { onSelectDevice(device) },
                modifier = buttonModifier,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                colors = buttonColors,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isOnline) {
                        Text(
                            text = batteryIcon,
                            fontFamily = FontFamily(Font(resId = R.font.segsmdl2)),
                            fontSize = 16.sp,
                            color = BatteryIconConverter.getBatteryColor(batteryLevel.intValue),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(
                        text = device.displayName + if (!isOnline) " (离线)" else "",
                        style = textStyles.body2.copy(color = if (selectedDevice?.uuid == device.uuid) colorScheme.onPrimary else colorScheme.primary),
                        overflow = TextOverflow.Ellipsis,
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
                onConfirm = { onRequestDelete(device) },
                modifier =
                    Modifier
                        .defaultMinSize(minHeight = ButtonMinHeight, minWidth = 60.dp)
                        .heightIn(min = ButtonMinHeight)
                        .widthIn(min = 60.dp),
                colors = ButtonDefaults.buttonColors(color = colorScheme.error),
                confirmColors = ButtonDefaults.buttonColors(color = colorScheme.error),
                textColor = colorScheme.onError,
                confirmTextColor = colorScheme.onError,
            )
        }
    }
}

/**
 * 未认证设备按钮。
 */
@Composable
internal fun UnauthenticatedDeviceButton(
    device: DeviceInfo,
    deviceStates: Map<String, Boolean>,
    onSelectDevice: (DeviceInfo?) -> Unit,
    isLandscape: Boolean,
    colorScheme: Colors,
    textStyles: TextStyles,
) {
    val isOnline = deviceStates[device.uuid] == true
    Button(
        onClick = { onSelectDevice(device) },
        modifier =
            Modifier
                .then(if (isLandscape) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = ButtonMinHeight)
                .then(if (isLandscape) Modifier.padding(vertical = 2.dp) else Modifier.padding(end = 6.dp)),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(color = colorScheme.surface),
    ) {
        Text(
            device.displayName + if (!isOnline) " (离线)" else "",
            style = textStyles.body2.copy(color = colorScheme.primary),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 「查看已拒绝设备」按钮。
 */
@Composable
internal fun RejectedDevicesButton(
    onShowRejectedDialog: () -> Unit,
    isLandscape: Boolean,
    colorScheme: Colors,
    textStyles: TextStyles,
) {
    Button(
        onClick = onShowRejectedDialog,
        modifier =
            Modifier
                .then(if (isLandscape) Modifier.fillMaxWidth() else Modifier)
                .defaultMinSize(minHeight = ButtonMinHeight)
                .then(if (isLandscape) Modifier.padding(vertical = 2.dp) else Modifier.padding(end = 6.dp)),
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(color = colorScheme.secondaryContainer),
    ) {
        Text(
            "查看已拒绝设备",
            style = textStyles.body2.copy(color = colorScheme.secondary),
            overflow = TextOverflow.Ellipsis,
        )
    }
}
