package io.github.miuzarte.scrcpyforandroid.pages

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.services.DevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.PinShortcutManager
import io.github.miuzarte.scrcpyforandroid.services.fetchConnectedDeviceInfo
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.loadQuickDevices
import io.github.miuzarte.scrcpyforandroid.services.syncFromAuthenticatedDevices
import io.github.miuzarte.scrcpyforandroid.services.updateQuickDeviceNameIfEmpty
import io.github.miuzarte.scrcpyforandroid.services.upsertQuickDevice
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTile
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.widgets.StatusCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.model.ConnectionTarget
import notifyrelay.data.model.DeviceShortcut
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

private const val ADB_CONNECT_TIMEOUT_MS = 3_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L

private val DeviceShortcutStateListSaver =
    listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<DeviceShortcut>, String>(
        save = { list ->
            list.map { item ->
                listOf(
                    item.id,
                    item.name,
                    item.host,
                    item.port.toString(),
                    if (item.online) "1" else "0",
                ).joinToString("\u001F")
            }
        },
        restore = { saved ->
            saved.mapNotNull { line ->
                val parts = line.split("\u001F")
                if (parts.size != 5) return@mapNotNull null
                val port = parts[3].toIntOrNull() ?: return@mapNotNull null
                DeviceShortcut(
                    id = parts[0],
                    name = parts[1],
                    host = parts[2],
                    port = port,
                    online = parts[4] == "1",
                )
            }.toMutableStateList()
        },
    )

@Composable
fun ScrcpyDevicePage(
    contentPadding: PaddingValues,
    nativeCore: NativeCoreFacade,
    snack: SnackbarHostState,
    scrollBehavior: ScrollBehavior,
    onOpenFullscreenPage: (ScrcpySessionInfo) -> Unit,
    adbPairingAutoDiscoverOnDialogOpen: Boolean = false,
    adbAutoReconnectPairedDevice: Boolean = false,
    adbMdnsLanDiscoveryEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val initialSettings = remember(context) { loadDevicePageSettings(context) }
    val scope = rememberCoroutineScope()

    var busy by rememberSaveable { mutableStateOf(false) }
    var statusLine by rememberSaveable { mutableStateOf("未连接") }
    var adbConnected by rememberSaveable { mutableStateOf(false) }
    var currentTargetHost by rememberSaveable { mutableStateOf("") }
    var currentTargetPort by rememberSaveable { mutableIntStateOf(ScrcpyDefaults.ADB_PORT) }
    var connectedDeviceLabel by rememberSaveable { mutableStateOf("未连接") }
    var sessionInfo by remember { mutableStateOf<ScrcpySessionInfo?>(null) }
    var adbConnecting by rememberSaveable { mutableStateOf(false) }
    var activeDeviceActionId by rememberSaveable { mutableStateOf<String?>(null) }

    var connectHost by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf(ScrcpyDefaults.ADB_PORT.toString()) }
    var quickConnectInput by rememberSaveable { mutableStateOf(initialSettings.quickConnectInput) }
    var audioForwardingSupported by rememberSaveable { mutableStateOf(true) }

    var bitRateMbps by rememberSaveable { mutableFloatStateOf(initialSettings.videoBitRateMbps) }
    var bitRateInput by rememberSaveable { mutableStateOf(initialSettings.videoBitRateInput) }
    var audioBitRateKbps by rememberSaveable { mutableIntStateOf(initialSettings.audioBitRateKbps) }
    val currentTarget = if (currentTargetHost.isNotBlank()) ConnectionTarget(
        currentTargetHost,
        currentTargetPort
    ) else null

    val quickDevices =
        rememberSaveable(saver = DeviceShortcutStateListSaver) { mutableStateListOf() }
    var onlineDevicesFromApp by remember { mutableStateOf<List<notifyrelay.data.model.OnlineDeviceInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (quickDevices.isEmpty()) {
            quickDevices.clear()
            quickDevices.addAll(loadQuickDevices(context))
        }
        onlineDevicesFromApp = syncFromAuthenticatedDevices(context, quickDevices)
    }

    LaunchedEffect(adbConnected, currentTargetHost, currentTargetPort, quickDevices.size) {
        val activeId = if (adbConnected && currentTargetHost.isNotBlank()) {
            "$currentTargetHost:$currentTargetPort"
        } else {
            null
        }
        for (index in quickDevices.indices) {
            val item = quickDevices[index]
            val shouldOnline = activeId != null && item.id == activeId
            if (item.online != shouldOnline) {
                quickDevices[index] = item.copy(online = shouldOnline)
            }
        }
    }

    suspend fun connectWithTimeout(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(ADB_CONNECT_TIMEOUT_MS) {
                nativeCore.adbConnect(host, port)
            }
        }
    }

    suspend fun keepAliveCheck(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(ADB_KEEPALIVE_TIMEOUT_MS) {
                val connected = nativeCore.adbIsConnected()
                if (!connected) return@withTimeout false
                runCatching {
                    nativeCore.adbShell("echo -n 1")
                    true
                }.getOrElse { false }
            }
        }
    }

    fun handleAdbConnected(host: String, port: Int) {
        currentTargetHost = host
        currentTargetPort = port

        val info = fetchConnectedDeviceInfo(nativeCore, host, port)
        val fullLabel = if (info.serial.isNotBlank()) {
            "${info.model} (${info.serial})"
        } else {
            info.model
        }

        connectedDeviceLabel = info.model
        audioForwardingSupported = info.sdkInt !in 0..<30
        updateQuickDeviceNameIfEmpty(context, quickDevices, host, port, fullLabel)
        connectHost = host
        connectPort = port.toString()
        statusLine = "$host:$port"

        scope.launch {
            snack.showSnackbar("ADB 已连接")
        }
    }

    suspend fun disconnectAdbConnection() {
        withContext(Dispatchers.IO) {
            runCatching { nativeCore.scrcpyStop() }
            runCatching { nativeCore.adbDisconnect() }
        }
        adbConnected = false
        currentTargetHost = ""
        currentTargetPort = ScrcpyDefaults.ADB_PORT
        sessionInfo = null
        statusLine = "未连接"
        connectedDeviceLabel = "未连接"
    }

    fun runAdbConnect(label: String, onFinished: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (adbConnecting) return
        scope.launch {
            adbConnecting = true
            try {
                block()
            } catch (e: Exception) {
                scope.launch {
                    snack.showSnackbar("$label 失败: ${e.message}")
                }
            } finally {
                adbConnecting = false
                onFinished?.invoke()
            }
        }
    }

    LaunchedEffect(adbConnected, currentTargetHost, currentTargetPort) {
        if (!adbConnected || currentTargetHost.isBlank()) return@LaunchedEffect

        val host = currentTargetHost
        val port = currentTargetPort
        while (adbConnected && currentTargetHost == host && currentTargetPort == port) {
            delay(ADB_KEEPALIVE_INTERVAL_MS)
            val alive = runCatching { keepAliveCheck(host, port) }.getOrElse { false }
            if (alive) continue

            val reconnected = runCatching { connectWithTimeout(host, port) }.getOrElse { false }
            adbConnected = reconnected
            if (reconnected) {
                statusLine = "$host:$port"
                scope.launch {
                    snack.showSnackbar("ADB 自动重连成功")
                }
            } else {
                disconnectAdbConnection()
                statusLine = "ADB 连接断开"
                scope.launch {
                    snack.showSnackbar("ADB 连接断开")
                }
                break
            }
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Int, Int) -> Unit = { width, height ->
            sessionInfo = sessionInfo?.copy(width = width, height = height)
        }
        nativeCore.addVideoSizeListener(listener)
        onDispose {
            nativeCore.removeVideoSizeListener(listener)
        }
    }

    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            StatusCard(
                statusLine = statusLine,
                adbConnected = adbConnected,
                streaming = sessionInfo != null,
                sessionInfo = sessionInfo,
                busyLabel = null,
                connectedDeviceLabel = connectedDeviceLabel,
                themeBaseIndex = 0,
            )
        }

        itemsIndexed(quickDevices, key = { _, device -> device.id }) { _, device ->
            val host = device.host
            val port = device.port
            val isConnectedTarget =
                adbConnected && currentTarget?.host == host && currentTarget.port == port

            DeviceTile(
                device = device,
                actionText = if (isConnectedTarget) "断开" else "连接",
                actionEnabled = !busy && !adbConnecting,
                actionInProgress = adbConnecting && activeDeviceActionId == device.id,
                onLongPress = {
                    val success = PinShortcutManager.createPinnedShortcut(
                        context = context,
                        deviceName = device.name.ifBlank { device.host },
                        deviceIp = device.host,
                        devicePort = device.port
                    )
                    if (success) {
                        Toast.makeText(context, "正在创建快捷方式...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "当前启动器不支持创建快捷方式", Toast.LENGTH_SHORT).show()
                    }
                },
                onContentClick = {
                    scope.launch {
                        snack.showSnackbar("长按可创建桌面快捷方式")
                    }
                },
                onAction = {
                    haptics.contextClick()
                    if (isConnectedTarget) {
                        activeDeviceActionId = device.id
                        runAdbConnect("断开 ADB", onFinished = { activeDeviceActionId = null }) {
                            disconnectAdbConnection()
                        }
                    } else {
                        activeDeviceActionId = device.id
                        runAdbConnect("连接 ADB", onFinished = { activeDeviceActionId = null }) {
                            if (adbConnected) {
                                disconnectAdbConnection()
                            }
                            val ok = connectWithTimeout(host, port)
                            adbConnected = ok
                            upsertQuickDevice(context, quickDevices, host, port, ok)
                            if (ok) {
                                handleAdbConnected(host, port)
                            } else {
                                statusLine = "ADB 连接失败"
                                scope.launch {
                                    snack.showSnackbar("ADB 连接失败")
                                }
                            }
                        }
                    }
                },
            )
        }

        if (!adbConnected) item {
            QuickConnectCard(
                input = quickConnectInput,
                onInputChange = { quickConnectInput = it },
                enabled = !adbConnecting,
                onlineDevices = onlineDevicesFromApp,
                onAddDevice = {
                    val target = io.github.miuzarte.scrcpyforandroid.services.parseQuickTarget(quickConnectInput) ?: return@QuickConnectCard
                    upsertQuickDevice(
                        context,
                        quickDevices,
                        target.host,
                        target.port,
                        online = false
                    )
                    scope.launch {
                        snack.showSnackbar("已添加设备: ${target.host}:${target.port}")
                    }
                },
                onConnect = {
                    val target = io.github.miuzarte.scrcpyforandroid.services.parseQuickTarget(quickConnectInput) ?: return@QuickConnectCard
                    runAdbConnect("连接 ADB") {
                        if (adbConnected) {
                            disconnectAdbConnection()
                        }
                        val ok = connectWithTimeout(target.host, target.port)
                        adbConnected = ok
                        upsertQuickDevice(context, quickDevices, target.host, target.port, ok)
                        if (ok) {
                            handleAdbConnected(target.host, target.port)
                        } else {
                            statusLine = "ADB 连接失败"
                            scope.launch {
                                snack.showSnackbar("ADB 连接失败")
                            }
                        }
                    }
                },
            )
            SectionSmallTitle("无线配对")
            PairingCard(
                busy = busy,
                autoDiscoverOnDialogOpen = adbPairingAutoDiscoverOnDialogOpen,
                onDiscoverTarget = {
                    nativeCore.adbDiscoverPairingService(
                        includeLanDevices = adbMdnsLanDiscoveryEnabled,
                    )
                },
                onPair = { host, port, code ->
                    scope.launch {
                        busy = true
                        try {
                            val resolvedHost = host.trim()
                            val resolvedPort = port.toIntOrNull() ?: return@launch
                            val resolvedCode = code.trim()
                            val ok = nativeCore.adbPair(
                                resolvedHost,
                                resolvedPort,
                                resolvedCode,
                            )
                            snack.showSnackbar(if (ok) "配对成功" else "配对失败")
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }

        if (adbConnected) item {
            io.github.miuzarte.scrcpyforandroid.widgets.SimpleConfigPanel(
                busy = busy,
                bitRateMbps = bitRateMbps,
                onBitRateSliderChange = {
                    bitRateMbps = it
                    bitRateInput = String.format("%.1f", it)
                },
                onBitRateInputChange = { bitRateInput = it },
                audioBitRateKbps = audioBitRateKbps,
                onAudioBitRateChange = { audioBitRateKbps = it },
                audioEnabled = audioForwardingSupported,
                onStart = {
                    scope.launch {
                        busy = true
                        try {
                            val bitRateBps = (bitRateMbps * 1_000_000).toInt()
                            val audioBitRateBps = (audioBitRateKbps.coerceAtLeast(1)) * 1_000
                            val session = nativeCore.scrcpyStart(
                                NativeCoreFacade.defaultStartRequest(
                                    customServerUri = null,
                                    maxSize = 0,
                                    videoBitRate = bitRateBps,
                                    remotePath = ScrcpyDefaults.SERVER_REMOTE_PATH,
                                    videoCodec = ScrcpyDefaults.VIDEO_CODEC,
                                    audio = audioForwardingSupported,
                                    audioCodec = ScrcpyDefaults.AUDIO_CODEC,
                                    audioBitRate = audioBitRateBps,
                                    noControl = false,
                                    noVideo = false,
                                    audioDup = ScrcpyDefaults.AUDIO_DUP,
                                    audioSource = ScrcpyDefaults.AUDIO_SOURCE_PRESET,
                                ),
                            )
                            sessionInfo = session
                            statusLine = "scrcpy 运行中"
                            snack.showSnackbar("scrcpy 已启动")
                        } catch (e: Exception) {
                            snack.showSnackbar("启动失败: ${e.message}")
                        } finally {
                            busy = false
                        }
                    }
                },
                onStop = {
                    scope.launch {
                        busy = true
                        try {
                            nativeCore.scrcpyStop()
                            sessionInfo = null
                            statusLine = currentTarget?.let { "${it.host}:${it.port}" } ?: "ADB 已连接"
                            snack.showSnackbar("scrcpy 已停止")
                        } finally {
                            busy = false
                        }
                    }
                },
                onOpenFullscreen = {
                    val info = sessionInfo ?: return@SimpleConfigPanel
                    onOpenFullscreenPage(info)
                },
                sessionStarted = sessionInfo != null,
            )
        }

        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}
