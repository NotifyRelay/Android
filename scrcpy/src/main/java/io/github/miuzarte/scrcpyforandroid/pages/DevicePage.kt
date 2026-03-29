package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.model.ConnectionTarget
import notifyrelay.data.model.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.services.DevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.fetchConnectedDeviceInfo
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.loadQuickDevices
import io.github.miuzarte.scrcpyforandroid.services.parseQuickTarget
import io.github.miuzarte.scrcpyforandroid.services.replaceQuickDevicePort
import io.github.miuzarte.scrcpyforandroid.services.saveDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.saveQuickDevices
import io.github.miuzarte.scrcpyforandroid.services.syncFromAuthenticatedDevices
import io.github.miuzarte.scrcpyforandroid.services.updateQuickDeviceNameIfEmpty
import io.github.miuzarte.scrcpyforandroid.services.upsertQuickDevice
import io.github.miuzarte.scrcpyforandroid.widgets.ConfigPanel
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceEditorScreen
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTile
import io.github.miuzarte.scrcpyforandroid.widgets.LogsPanel
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.PreviewCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
import io.github.miuzarte.scrcpyforandroid.widgets.ReorderableList
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.widgets.StatusCard
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonAction
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private const val ADB_CONNECT_TIMEOUT_MS = 3_000L
private const val ADB_KEEPALIVE_INTERVAL_MS = 3_000L
private const val ADB_KEEPALIVE_TIMEOUT_MS = 1_500L
private const val ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS = 1_200L
private const val ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS = 1_500L
private const val ADB_TCP_PROBE_TIMEOUT_MS = 600
private const val DEVICE_SHORTCUT_SEPARATOR = "\u001F"
private const val LOG_TAG = "DevicePage"

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
                ).joinToString(DEVICE_SHORTCUT_SEPARATOR)
            }
        },
        restore = { saved ->
            saved.mapNotNull { line ->
                val parts = line.split(DEVICE_SHORTCUT_SEPARATOR)
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

private val StringStateListSaver =
    listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )

@Composable
fun DeviceTabScreen() {
    val viewModel = LocalScrcpyUiViewModel.current
    val navigation = LocalScrcpyNavigation.current
    val contentPadding = LocalScrcpyPagePadding.current
    val scrollBehavior = LocalScrcpyScrollBehavior.current
    val snack = LocalScrcpySnackbarHostState.current ?: remember { SnackbarHostState() }
    val themeBaseIndex = LocalScrcpyThemeBaseIndex.current
    val nativeCore = viewModel.nativeCore
    val virtualButtonsLayout = viewModel.virtualButtonsLayout
    val showPreviewVirtualButtonText = viewModel.showPreviewVirtualButtonText
    val previewCardHeightDp = viewModel.devicePreviewCardHeightDp
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val virtualButtonLayout = remember(virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(VirtualButtonActions.parseStoredLayout(virtualButtonsLayout))
    }
    val initialSettings = remember(context) { loadDevicePageSettings(context) }
    val scope = rememberCoroutineScope()
    val adbWorkerDispatcher = remember {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "adb-connect-worker").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }

    // Run adb operations on a dedicated single thread.
    // Try to avoid blocking UI/recomposition and keeps adb call ordering deterministic.

    DisposableEffect(adbWorkerDispatcher) {
        onDispose {
            adbWorkerDispatcher.close()
        }
    }

    var busy by rememberSaveable { mutableStateOf(false) }
    var statusLine by rememberSaveable { mutableStateOf("未连接") }
    var adbConnected by rememberSaveable { mutableStateOf(false) }
    var currentTargetHost by rememberSaveable { mutableStateOf("") }
    var currentTargetPort by rememberSaveable { mutableIntStateOf(ScrcpyDefaults.ADB_PORT) }
    var connectedDeviceLabel by rememberSaveable { mutableStateOf("未连接") }
    var sessionInfoWidth by rememberSaveable { mutableIntStateOf(0) }
    var sessionInfoHeight by rememberSaveable { mutableIntStateOf(0) }
    var sessionInfoDeviceName by rememberSaveable { mutableStateOf("") }
    var sessionInfoCodec by rememberSaveable { mutableStateOf("") }
    var sessionInfoControlEnabled by rememberSaveable { mutableStateOf(false) }
    var sessionInfo by remember {
        mutableStateOf<ScrcpySessionInfo?>(null)
    }
    LaunchedEffect(
        sessionInfoWidth,
        sessionInfoHeight,
        sessionInfoDeviceName,
        sessionInfoCodec,
        sessionInfoControlEnabled
    ) {
        sessionInfo = if (sessionInfoDeviceName.isNotBlank()) {
            ScrcpySessionInfo(
                width = sessionInfoWidth,
                height = sessionInfoHeight,
                deviceName = sessionInfoDeviceName,
                codec = sessionInfoCodec,
                controlEnabled = sessionInfoControlEnabled,
            )
        } else {
            null
        }
    }
    var previewControlsVisible by rememberSaveable { mutableStateOf(false) }
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeDeviceActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showReorderSheet by rememberSaveable { mutableStateOf(false) }
    var adbConnecting by rememberSaveable { mutableStateOf(false) }

    var connectHost by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf(ScrcpyDefaults.ADB_PORT.toString()) }
    var quickConnectInput by rememberSaveable { mutableStateOf(initialSettings.quickConnectInput) }
    var audioForwardingSupported by rememberSaveable { mutableStateOf(true) }
    var cameraMirroringSupported by rememberSaveable { mutableStateOf(true) }

    var bitRateMbps by rememberSaveable { mutableFloatStateOf(initialSettings.videoBitRateMbps) }
    var bitRateInput by rememberSaveable { mutableStateOf(initialSettings.videoBitRateInput) }
    var audioBitRateKbps by rememberSaveable { mutableIntStateOf(initialSettings.audioBitRateKbps) }
    val currentTarget = if (currentTargetHost.isNotBlank()) ConnectionTarget(
        currentTargetHost,
        currentTargetPort
    ) else null

    val eventLog = rememberSaveable(saver = StringStateListSaver) { mutableStateListOf() }
    val quickDevices =
        rememberSaveable(saver = DeviceShortcutStateListSaver) { mutableStateListOf() }
    val sessionReconnectBlacklistHosts = remember { mutableSetOf<String>() }
    var onlineDevicesFromApp by remember { mutableStateOf<List<notifyrelay.data.model.OnlineDeviceInfo>>(emptyList()) }

    LaunchedEffect(eventLog.size) {
        viewModel.canClearLogs = eventLog.isNotEmpty()
    }

    fun logEvent(message: String, level: Int = Log.INFO, error: Throwable? = null) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$timestamp] $message"
        eventLog.add(0, line)
        if (eventLog.size > ScrcpyDefaults.EVENT_LOG_LINES) {
            eventLog.removeRange(ScrcpyDefaults.EVENT_LOG_LINES, eventLog.size)
        }
        when (level) {
            Log.ERROR -> if (error != null) Log.e(LOG_TAG, message, error) else Log.e(
                LOG_TAG,
                message
            )

            Log.WARN -> if (error != null) Log.w(LOG_TAG, message, error) else Log.w(
                LOG_TAG,
                message
            )

            Log.DEBUG -> if (error != null) Log.d(LOG_TAG, message, error) else Log.d(
                LOG_TAG,
                message
            )

            else -> if (error != null) Log.i(LOG_TAG, message, error) else Log.i(LOG_TAG, message)
        }
    }

    /**
     * Disconnect the current ADB connection and stop any running scrcpy session.
     *
     * Concurrency / thread boundary:
     * - Native calls that may block are executed on [adbWorkerDispatcher] using [withContext].
     * - This ensures UI coroutines are never blocked by synchronous native I/O.
     *
     * Side effects:
     * - Calls `nativeCore.scrcpyStop()` and `nativeCore.adbDisconnect()` (best-effort).
     * - Resets UI-visible connection state: `adbConnected`, `currentTargetHost/Port`,
     *   `sessionInfo`, device capability flags, `statusLine`, and `connectedDeviceLabel`.
     * - Updates the saved quick-device list via [upsertQuickDevice] when a target is provided.
     * - Logs an optional [logMessage] to the local event log.
     * - Shows an optional snackbar message asynchronously (launched on the composition scope)
     *   so callers don't get blocked by `snack.showSnackbar` (it is suspending).
     *
     * Usage notes:
     * - Prefer calling this from UI code wrapped by `runAdbConnect`/`runBusy` where appropriate
     *   so the UI busy/connect gates are respected.
     * - This function is idempotent from the UI state perspective: calling it when already
     *   disconnected will simply reset UI fields and not throw.
     */
    suspend fun disconnectAdbConnection(
        clearQuickOnlineForTarget: ConnectionTarget? = currentTarget,
        logMessage: String? = null,
        showSnackMessage: String? = null,
    ) {
        withContext(adbWorkerDispatcher) {
            // Also stops scrcpy.
            runCatching { nativeCore.scrcpyStop() }
            runCatching { nativeCore.adbDisconnect() }
        }
        adbConnected = false
        currentTargetHost = ""
        currentTargetPort = ScrcpyDefaults.ADB_PORT
        audioForwardingSupported = true
        cameraMirroringSupported = true
        sessionInfo = null
        statusLine = "未连接"
        connectedDeviceLabel = "未连接"
        clearQuickOnlineForTarget?.let { target ->
            if (target.host.isNotBlank()) {
                upsertQuickDevice(context, quickDevices, target.host, target.port, false)
            }
        }
        logMessage?.let { logEvent(it) }
        if (!showSnackMessage.isNullOrBlank()) {
            scope.launch {
                snack.showSnackbar(showSnackMessage)
            }
        }
    }

    suspend fun disconnectCurrentTargetBeforeConnecting(newHost: String, newPort: Int) {
        // Force old target cleanup before switching to another endpoint.
        val current = currentTarget
        if (!adbConnected || current == null) return
        if (current.host == newHost && current.port == newPort) return

        sessionReconnectBlacklistHosts += current.host
        logEvent("切换连接目标，先断开当前设备: ${current.host}:${current.port}")
        disconnectAdbConnection(clearQuickOnlineForTarget = current)
    }

    fun applyConnectedDeviceCapabilities(sdkInt: Int, release: String) {
        val audioSupported = sdkInt !in 0..<30
        audioForwardingSupported = audioSupported
        if (!audioSupported && viewModel.audioEnabled) {
            viewModel.audioEnabled = false
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持音频转发，已自动关闭",
                Log.WARN
            )
        }
        val cameraSupported = sdkInt !in 0..<31
        cameraMirroringSupported = cameraSupported
        if (!cameraSupported && viewModel.videoSourcePreset == "camera") {
            viewModel.videoSourcePreset = "display"
            logEvent(
                "设备 Android ${release.ifBlank { "?" }} (SDK $sdkInt) 不支持 camera mirroring，已切换为 display",
                Log.WARN
            )
        }
    }

    /**
     * Attempt to connect to an adb endpoint within a short timeout.
     *
     * Execution:
     * - Runs `nativeCore.adbConnect(host, port)` on [adbWorkerDispatcher] and wraps it with
     *   [withTimeout] to avoid hanging forever. Returns true on success, false / throws on failure
     *   depending on the underlying native behavior.
     *
     * Why this exists:
     * - Some adb endpoints can take long to accept TCP connects; the UI should not wait
     *   indefinitely. Use a small, caller-chosen timeout to keep UX snappy.
     */
    suspend fun connectWithTimeout(host: String, port: Int): Boolean {
        return withContext(adbWorkerDispatcher) {
            withTimeout(ADB_CONNECT_TIMEOUT_MS) {
                nativeCore.adbConnect(host, port)
            }
        }
    }

    /**
     * Validate that the current ADB connection is still alive.
     *
     * Behavior:
     * - Runs on [adbWorkerDispatcher] with a short timeout.
     * - First checks `nativeCore.adbIsConnected()` to avoid unnecessary shell calls.
     * - Executes a lightweight `adb shell` command (`echo -n 1`) to verify the remote side is
     *   responsive. Returns true only when both checks succeed.
     *
     * Notes for reliability:
     * - Some devices may accept TCP connections but have a hung adb-server process; the shell
     *   echo check helps detect that state.
     */
    suspend fun keepAliveCheck(host: String, port: Int): Boolean {
        return withContext(adbWorkerDispatcher) {
            withTimeout(ADB_KEEPALIVE_TIMEOUT_MS) {
                val connected = nativeCore.adbIsConnected()
                if (!connected) {
                    return@withTimeout false
                }
                runCatching {
                    nativeCore.adbShell("echo -n 1")
                    true
                }.getOrElse { false }
            }
        }
    }

    /**
     * Quickly test TCP reachability to an endpoint.
     *
     * - Uses a plain Socket connect on [Dispatchers.IO] with a very short timeout.
     * - This is useful before attempting an adb connect to avoid long native timeouts.
     * - Returns true when TCP handshake succeeds within [ADB_TCP_PROBE_TIMEOUT_MS].
     */
    suspend fun probeTcpReachable(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), ADB_TCP_PROBE_TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
        }
    }

    /**
     * Execute a suspend [block] while toggling the `busy` UI gate.
     *
     * - Intended for non-adb user actions (UI-level operations) that should disable
     *   certain controls while active (e.g. scrcpy start/stop, pairing, listing).
     * - Errors are logged and surfaced via a snackbar where appropriate. The snackbar
     *   is launched asynchronously so the outer coroutine can continue to unwind.
     * - Ensures `busy` is reset in `finally` so the UI recovers even on exceptions.
     */
    fun runBusy(label: String, onFinished: (() -> Unit)? = null, block: suspend () -> Unit) {
        // For non-adb actions (start/stop/pair/list refresh...).
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent("$label 超时", Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 参数错误: $detail", Log.WARN, e)
                scope.launch {
                    snack.showSnackbar("$label 参数错误: $detail")
                }
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 失败: $detail", Log.ERROR, e)
            } finally {
                busy = false
                onFinished?.invoke()
            }
        }
    }

    /**
     * Execute a manual ADB-related suspend [block] while toggling `adbConnecting`.
     *
     * Purpose:
     * - Called from explicit user actions (pressing "connect" / "disconnect").
     * - Keeps the UI responsive by marking only user-initiated connect operations as in-progress.
     *
     * Concurrency notes:
     * - Background auto-reconnect attempts deliberately DO NOT set `adbConnecting` so that
     *   UI controls remain actionable while background retries occur.
     * - Errors and timeouts are logged and surfaced similarly to `runBusy`.
     */
    fun runAdbConnect(label: String, onFinished: (() -> Unit)? = null, block: suspend () -> Unit) {
        // For manual adb operations from user actions.
        if (adbConnecting) return
        scope.launch {
            adbConnecting = true
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                logEvent("$label 超时", Log.WARN)
            } catch (e: IllegalArgumentException) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 参数错误: $detail", Log.WARN, e)
                scope.launch {
                    snack.showSnackbar("$label 参数错误: $detail")
                }
            } catch (e: Exception) {
                val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                logEvent("$label 失败: $detail", Log.ERROR, e)
            } finally {
                adbConnecting = false
                onFinished?.invoke()
            }
        }
    }

    suspend fun runAutoAdbConnect(host: String, port: Int): Boolean {
        return runCatching {
            connectWithTimeout(host, port)
        }.getOrElse { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            logEvent("自动重连失败: $host:$port ($detail)", Log.WARN)
            false
        }
    }

    fun refreshEncoderLists() {
        if (!adbConnected) return
        val remotePath = viewModel.serverRemotePath.trim().ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH }
        runCatching {
            nativeCore.scrcpyListEncoders(
                customServerUri = viewModel.customServerUri,
                remotePath = remotePath,
            )
        }.onSuccess { lists ->
            viewModel.videoEncoderOptions.clear()
            viewModel.videoEncoderOptions.addAll(lists.videoEncoders)
            viewModel.audioEncoderOptions.clear()
            viewModel.audioEncoderOptions.addAll(lists.audioEncoders)
            viewModel.videoEncoderTypeMap.clear()
            viewModel.videoEncoderTypeMap.putAll(lists.videoEncoderTypes)
            viewModel.audioEncoderTypeMap.clear()
            viewModel.audioEncoderTypeMap.putAll(lists.audioEncoderTypes)
            if (viewModel.videoEncoder.isNotBlank() && viewModel.videoEncoder !in viewModel.videoEncoderOptions) {
                viewModel.videoEncoder = ""
            }
            if (viewModel.audioEncoder.isNotBlank() && viewModel.audioEncoder !in viewModel.audioEncoderOptions) {
                viewModel.audioEncoder = ""
            }
            logEvent("编码器列表已刷新: video=${lists.videoEncoders.size} audio=${lists.audioEncoders.size}")
            if (lists.videoEncoders.isEmpty() && lists.audioEncoders.isEmpty()) {
                logEvent("提示: 编码器为空，请检查 server 路径/版本与设备系统日志", Log.WARN)
                val preview = lists.rawOutput.lineSequence().take(20).joinToString(" | ")
                if (preview.isNotBlank()) {
                    logEvent("编码器原始输出: $preview", Log.DEBUG)
                }
            }
        }.onFailure { e ->
            viewModel.videoEncoderOptions.clear()
            viewModel.audioEncoderOptions.clear()
            viewModel.videoEncoderTypeMap.clear()
            viewModel.audioEncoderTypeMap.clear()
            logEvent("读取编码器列表失败: ${e.message ?: e.javaClass.simpleName}", Log.ERROR, e)
        }
    }

    fun refreshCameraSizeLists() {
        if (!adbConnected) return
        val remotePath = viewModel.serverRemotePath.trim().ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH }
        runCatching {
            nativeCore.scrcpyListCameraSizes(
                customServerUri = viewModel.customServerUri,
                remotePath = remotePath,
            )
        }.onSuccess { lists ->
            viewModel.cameraSizeOptions.clear()
            viewModel.cameraSizeOptions.addAll(lists.sizes)
            if (viewModel.cameraSizePreset.isNotBlank() && viewModel.cameraSizePreset != "custom" && viewModel.cameraSizePreset !in lists.sizes) {
                viewModel.cameraSizePreset = ""
            }
            logEvent("camera sizes 已刷新: count=${lists.sizes.size}")
            if (lists.sizes.isEmpty()) {
                val preview = lists.rawOutput.lineSequence().take(20).joinToString(" | ")
                if (preview.isNotBlank()) {
                    logEvent("camera sizes 原始输出: $preview", Log.DEBUG)
                }
            }
        }.onFailure { e ->
            viewModel.cameraSizeOptions.clear()
            logEvent("读取 camera sizes 失败: ${e.message ?: e.javaClass.simpleName}", Log.ERROR, e)
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
        applyConnectedDeviceCapabilities(info.sdkInt, info.androidRelease)
        updateQuickDeviceNameIfEmpty(context, quickDevices, host, port, fullLabel)
        connectHost = host
        connectPort = port.toString()
        statusLine = "$host:$port"

        logEvent("ADB 已连接: model=${info.model}, serial=${info.serial.ifBlank { "unknown" }}, manufacturer=${info.manufacturer.ifBlank { "unknown" }}, brand=${info.brand.ifBlank { "unknown" }}, device=${info.device.ifBlank { "unknown" }}, android=${info.androidRelease.ifBlank { "unknown" }}, sdk=${info.sdkInt}")
        scope.launch {
            snack.showSnackbar("ADB 已连接")
        }
        refreshEncoderLists()
        refreshCameraSizeLists()
    }

    LaunchedEffect(bitRateInput) {
        val parsed = bitRateInput.toFloatOrNull() ?: return@LaunchedEffect
        bitRateMbps = parsed.coerceAtLeast(0.1f)
    }

    LaunchedEffect(
        quickConnectInput,
        audioBitRateKbps,
        bitRateMbps,
        bitRateInput,
        viewModel.turnScreenOff,
        viewModel.noControl,
        viewModel.noVideo,
        viewModel.videoSourcePreset,
        viewModel.displayIdInput,
        viewModel.cameraIdInput,
        viewModel.cameraFacingPreset,
        viewModel.cameraSizePreset,
        viewModel.cameraSizeCustom,
        viewModel.cameraArInput,
        viewModel.cameraFpsInput,
        viewModel.cameraHighSpeed,
        viewModel.audioSourcePreset,
        viewModel.audioSourceCustom,
        viewModel.audioDup,
        viewModel.noAudioPlayback,
        viewModel.requireAudio,
        viewModel.maxSizeInput,
        viewModel.maxFpsInput,
        viewModel.videoEncoder,
        viewModel.videoCodecOptions,
        viewModel.audioEncoder,
        viewModel.audioCodecOptions,
        viewModel.newDisplayWidth,
        viewModel.newDisplayHeight,
        viewModel.newDisplayDpi,
        viewModel.cropWidth,
        viewModel.cropHeight,
        viewModel.cropX,
        viewModel.cropY,
    ) {
        saveDevicePageSettings(
            context,
            DevicePageSettings(
                quickConnectInput = quickConnectInput,
                audioBitRateKbps = audioBitRateKbps,
                audioBitRateInput = audioBitRateKbps.toString(),
                videoBitRateMbps = bitRateMbps,
                videoBitRateInput = bitRateInput,
                turnScreenOff = viewModel.turnScreenOff,
                noControl = viewModel.noControl,
                noVideo = viewModel.noVideo,
                videoSourcePreset = viewModel.videoSourcePreset,
                displayIdInput = viewModel.displayIdInput,
                cameraIdInput = viewModel.cameraIdInput,
                cameraFacingPreset = viewModel.cameraFacingPreset,
                cameraSizePreset = viewModel.cameraSizePreset,
                cameraSizeCustom = viewModel.cameraSizeCustom,
                cameraAr = viewModel.cameraArInput,
                cameraFps = viewModel.cameraFpsInput,
                cameraHighSpeed = viewModel.cameraHighSpeed,
                audioSourcePreset = viewModel.audioSourcePreset,
                audioSourceCustom = viewModel.audioSourceCustom,
                audioDup = viewModel.audioDup,
                noAudioPlayback = viewModel.noAudioPlayback,
                requireAudio = viewModel.requireAudio,
                maxSizeInput = viewModel.maxSizeInput,
                maxFpsInput = viewModel.maxFpsInput,
                videoEncoder = viewModel.videoEncoder,
                videoCodecOptions = viewModel.videoCodecOptions,
                audioEncoder = viewModel.audioEncoder,
                audioCodecOptions = viewModel.audioCodecOptions,
                newDisplayWidth = viewModel.newDisplayWidth,
                newDisplayHeight = viewModel.newDisplayHeight,
                newDisplayDpi = viewModel.newDisplayDpi,
                cropWidth = viewModel.cropWidth,
                cropHeight = viewModel.cropHeight,
                cropX = viewModel.cropX,
                cropY = viewModel.cropY,
            ),
        )
    }

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

    LaunchedEffect(adbConnected, currentTargetHost, currentTargetPort) {
        if (!adbConnected || currentTargetHost.isBlank()) return@LaunchedEffect

        // Keep-alive loop for current target.
        // On failure: try to reconnect once; if failed, fully disconnect and reset UI state.
        val host = currentTargetHost
        val port = currentTargetPort
        while (adbConnected && currentTargetHost == host && currentTargetPort == port) {
            delay(ADB_KEEPALIVE_INTERVAL_MS)
            val alive = runCatching { keepAliveCheck(host, port) }.getOrElse { false }
            if (alive) continue

            logEvent("ADB 长连接中断，尝试自动重连: $host:$port", Log.WARN)
            val reconnected = runCatching { connectWithTimeout(host, port) }.getOrElse { false }
            adbConnected = reconnected
            if (reconnected) {
                statusLine = "$host:$port"
                logEvent("ADB 自动重连成功: $host:$port")
                scope.launch {
                    snack.showSnackbar("ADB 自动重连成功")
                }
            } else {
                disconnectAdbConnection()
                statusLine = "ADB 连接断开"
                logEvent("ADB 自动重连失败: $host:$port", Log.ERROR)
                scope.launch {
                    snack.showSnackbar("ADB 自动重连失败")
                }
                break
            }
        }
    }

    LaunchedEffect(adbConnected, viewModel.adbAutoReconnectPairedDevice, viewModel.adbMdnsLanDiscoveryEnabled) {
        if (adbConnected || !viewModel.adbAutoReconnectPairedDevice) return@LaunchedEffect

        // Background auto reconnect pipeline:
        // 1) try quick list targets with reachable TCP ports
        // 2) fallback to mDNS discovery
        val quickConnectTriedOnce = mutableSetOf<String>()
        while (!adbConnected && viewModel.adbAutoReconnectPairedDevice) {
            if (busy || adbConnecting || sessionInfo != null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val quickCandidates = quickDevices.toList()
            if (quickCandidates.isNotEmpty()) {
                for (target in quickCandidates) {
                    if (adbConnected || adbConnecting) break
                    if (sessionReconnectBlacklistHosts.contains(target.host)) continue
                    val targetKey = "${target.host}:${target.port}"
                    if (quickConnectTriedOnce.contains(targetKey)) continue

                    val portReachable = probeTcpReachable(target.host, target.port)
                    if (!portReachable) continue

                    quickConnectTriedOnce += targetKey
                    val ok = runAutoAdbConnect(target.host, target.port)
                    adbConnected = ok
                    upsertQuickDevice(
                        context,
                        quickDevices,
                        target.host,
                        target.port,
                        ok
                    )
                    if (ok) {
                        handleAdbConnected(target.host, target.port)
                        logEvent("ADB 快速探测连接成功: ${target.host}:${target.port}")
                        break
                    }
                }
                if (adbConnected) break
            }

            val discovered = withContext(Dispatchers.IO) {
                nativeCore.adbDiscoverConnectService(
                    timeoutMs = ADB_AUTO_RECONNECT_DISCOVER_TIMEOUT_MS,
                    includeLanDevices = viewModel.adbMdnsLanDiscoveryEnabled,
                )
            }

            if (discovered == null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val (discoveredHost, discoveredPort) = discovered
            if (sessionReconnectBlacklistHosts.contains(discoveredHost)) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }
            val knownDevice = quickDevices.firstOrNull { it.host == discoveredHost }
            if (knownDevice == null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }
            val portToReplace = quickDevices.firstOrNull {
                it.host == discoveredHost &&
                        it.port != ScrcpyDefaults.ADB_PORT &&
                        it.port != discoveredPort
            }?.port
            if (portToReplace != null) {
                replaceQuickDevicePort(
                    context = context,
                    quickDevices = quickDevices,
                    host = discoveredHost,
                    oldPort = portToReplace,
                    newPort = discoveredPort,
                    online = false,
                )
                logEvent(
                    "mDNS 发现新端口，已更新快速设备: $discoveredHost:$portToReplace -> $discoveredHost:$discoveredPort"
                )
            }

            if (adbConnected || adbConnecting) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val ok = runAutoAdbConnect(discoveredHost, discoveredPort)
            adbConnected = ok
            upsertQuickDevice(
                context,
                quickDevices,
                discoveredHost,
                discoveredPort,
                ok
            )
            if (ok) {
                handleAdbConnected(discoveredHost, discoveredPort)
                logEvent("ADB 自动重连成功: $discoveredHost:$discoveredPort")
            } else {
                logEvent("ADB 自动重连失败: $discoveredHost:$discoveredPort", Log.WARN)
            }

            delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
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

    LaunchedEffect(sessionInfo) {
        if (sessionInfo != null) {
            sessionInfoWidth = sessionInfo?.width ?: 0
            sessionInfoHeight = sessionInfo?.height ?: 0
            sessionInfoDeviceName = sessionInfo?.deviceName.orEmpty()
            sessionInfoCodec = sessionInfo?.codec.orEmpty()
            sessionInfoControlEnabled = sessionInfo?.controlEnabled == true
        } else {
            sessionInfoWidth = 0
            sessionInfoHeight = 0
            sessionInfoDeviceName = ""
            sessionInfoCodec = ""
            sessionInfoControlEnabled = false
        }
        viewModel.sessionStarted = sessionInfo != null
    }

    DisposableEffect(Unit) {
        viewModel.refreshEncodersAction = {
            runBusy("刷新编码器") { refreshEncoderLists() }
        }
        viewModel.refreshCameraSizesAction = {
            runBusy("刷新 Camera Sizes") { refreshCameraSizeLists() }
        }
        viewModel.clearLogsAction = {
            eventLog.clear()
        }
        viewModel.openReorderDevicesAction = {
            showReorderSheet = true
        }
        onDispose {
            viewModel.refreshEncodersAction = null
            viewModel.refreshCameraSizesAction = null
            viewModel.clearLogsAction = null
            viewModel.canClearLogs = false
            viewModel.openReorderDevicesAction = null
        }
    }

    SuperBottomSheet(
        show = showReorderSheet,
        title = "快速设备排序",
        onDismissRequest = { showReorderSheet = false },
    ) {
        val list = remember {
            ReorderableList(
                {
                    quickDevices.map { device ->
                        ReorderableList.Item(
                            id = device.id,
                            title = device.name.ifBlank { device.host },
                            subtitle = "${device.host}:${device.port}",
                        )
                    }
                },
                onSettle = { fromIndex, toIndex ->
                    if (fromIndex < 0) return@ReorderableList
                    val to = toIndex.coerceIn(0, quickDevices.size)
                    if (fromIndex == to) return@ReorderableList

                    val moved = quickDevices.removeAt(fromIndex)
                    quickDevices.add(to.coerceIn(0, quickDevices.size), moved)
                    saveQuickDevices(context, quickDevices)
                },
            )
        }
        list()
        Spacer(Modifier.height(UiSpacing.BottomSheetBottom))
    }

    fun sendVirtualButtonAction(action: VirtualButtonAction) {
        val keycode = action.keycode ?: return
        runBusy("发送 ${action.title}") {
            nativeCore.scrcpyInjectKeycode(0, keycode)
            nativeCore.scrcpyInjectKeycode(1, keycode)
        }
    }

    if (editingDeviceId != null) {
        val device = quickDevices.firstOrNull { it.id == editingDeviceId }
        if (device != null) {
            DeviceEditorScreen(
                contentPadding = contentPadding,
                device = device,
                onSave = { updated ->
                    val idx = quickDevices.indexOfFirst { it.id == device.id }
                    if (idx >= 0) {
                        quickDevices[idx] = updated.copy(online = quickDevices[idx].online)
                        saveQuickDevices(context, quickDevices)
                    }
                    editingDeviceId = null
                },
                onDelete = {
                    quickDevices.removeAll { it.id == device.id }
                    saveQuickDevices(context, quickDevices)
                    editingDeviceId = null
                },
                onBack = { editingDeviceId = null },
            )
            return
        }
        editingDeviceId = null
    }

    // 设备
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
                themeBaseIndex = themeBaseIndex,
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
                onLongPress = { editingDeviceId = device.id },
                onContentClick = {
                    scope.launch {
                        snack.showSnackbar("长按可编辑设备")
                    }
                },
                onAction = {
                    haptics.contextClick()
                    if (isConnectedTarget) {
                        activeDeviceActionId = device.id
                        runAdbConnect("断开 ADB", onFinished = { activeDeviceActionId = null }) {
                            sessionReconnectBlacklistHosts += host
                            disconnectAdbConnection(
                                clearQuickOnlineForTarget = ConnectionTarget(host, port),
                                logMessage = "ADB 已断开: ${device.name}",
                                showSnackMessage = "ADB 已断开",
                            )
                        }
                    } else {
                        activeDeviceActionId = device.id
                        runAdbConnect("连接 ADB", onFinished = { activeDeviceActionId = null }) {
                            disconnectCurrentTargetBeforeConnecting(host, port)
                            val ok = connectWithTimeout(host, port)
                            adbConnected = ok
                            upsertQuickDevice(context, quickDevices, host, port, ok)
                            if (ok) {
                                handleAdbConnected(host, port)
                            } else {
                                statusLine = "ADB 连接失败"
                                logEvent("ADB 连接失败: $host:$port", Log.ERROR)
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
            // "快速连接"
            QuickConnectCard(
                input = quickConnectInput,
                onInputChange = { quickConnectInput = it },
                enabled = !adbConnecting,
                onlineDevices = onlineDevicesFromApp,
                onAddDevice = {
                    val target = parseQuickTarget(quickConnectInput) ?: return@QuickConnectCard
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
                    val target = parseQuickTarget(quickConnectInput) ?: return@QuickConnectCard
                    runAdbConnect("连接 ADB") {
                        disconnectCurrentTargetBeforeConnecting(target.host, target.port)
                        val ok = connectWithTimeout(target.host, target.port)
                        adbConnected = ok
                        upsertQuickDevice(context, quickDevices, target.host, target.port, ok)
                        if (ok) {
                            handleAdbConnected(target.host, target.port)
                        } else {
                            statusLine = "ADB 连接失败"
                            logEvent("ADB 连接失败: ${target.host}:${target.port}", Log.ERROR)
                            scope.launch {
                                snack.showSnackbar("ADB 连接失败")
                            }
                        }
                    }
                },
            )
            SectionSmallTitle("无线配对")
            // "使用配对码配对设备"
            PairingCard(
                busy = busy,
                autoDiscoverOnDialogOpen = viewModel.adbPairingAutoDiscoverOnDialogOpen,
                onDiscoverTarget = {
                    nativeCore.adbDiscoverPairingService(
                        includeLanDevices = viewModel.adbMdnsLanDiscoveryEnabled,
                    )
                },
                onPair = { host, port, code ->
                    runBusy("执行配对") {
                        val resolvedHost = host.trim()
                        val resolvedPort = port.toIntOrNull() ?: return@runBusy
                        val resolvedCode = code.trim()
                        val ok = nativeCore.adbPair(
                            resolvedHost,
                            resolvedPort,
                            resolvedCode,
                        )
                        logEvent(
                            if (ok) "配对成功" else "配对失败",
                            if (ok) Log.INFO else Log.ERROR
                        )
                        scope.launch {
                            snack.showSnackbar(if (ok) "配对成功" else "配对失败")
                        }
                    }
                },
            )
        }

        if (adbConnected) {
            item {
                ConfigPanel(
                    busy = busy,
                    bitRateMbps = bitRateMbps,
                    onBitRateSliderChange = {
                        bitRateMbps = it
                        @SuppressLint("DefaultLocale")
                        bitRateInput = String.format("%.1f", it)
                    },
                    onBitRateInputChange = { bitRateInput = it },
                    audioBitRateKbps = audioBitRateKbps,
                    onAudioBitRateChange = { audioBitRateKbps = it },
                    videoCodec = viewModel.videoCodec,
                    onVideoCodecChange = { viewModel.videoCodec = it },
                    audioEnabled = viewModel.audioEnabled,
                    onAudioEnabledChange = { viewModel.audioEnabled = it },
                    audioForwardingSupported = audioForwardingSupported,
                    audioCodec = viewModel.audioCodec,
                    onAudioCodecChange = { viewModel.audioCodec = it },
                    onOpenAdvanced = navigation.openAdvancedPage,
                    onStartStopHaptic = { haptics.contextClick() },
                    onStart = {
                        runBusy("启动 scrcpy") {
                            if (viewModel.noVideo && !viewModel.audioEnabled) {
                                throw IllegalArgumentException("--no-video 需要同时启用音频")
                            }
                            if (viewModel.audioEnabled && viewModel.audioSourcePreset == "custom" && viewModel.audioSourceCustom.isBlank()) {
                                throw IllegalArgumentException("audio-source 选择自定义时不能为空")
                            }
                            val resolvedVideoSource = viewModel.videoSourcePreset.trim().ifBlank { "display" }
                            if (resolvedVideoSource == "camera" && !cameraMirroringSupported) {
                                throw IllegalArgumentException("camera mirroring 需要 Android 12+ (SDK 31+)")
                            }
                            val resolvedCameraSize = when (viewModel.cameraSizePreset) {
                                "custom" -> viewModel.cameraSizeCustom.trim()
                                else -> viewModel.cameraSizePreset.trim()
                            }
                            if (resolvedVideoSource == "camera" && viewModel.cameraSizePreset == "custom" && resolvedCameraSize.isBlank()) {
                                throw IllegalArgumentException("camera-size 选择自定义时不能为空")
                            }
                            val resolvedCameraId = viewModel.cameraIdInput.trim()
                            val resolvedCameraFacing = viewModel.cameraFacingPreset.trim()
                            if (resolvedVideoSource == "camera" && resolvedCameraId.isNotBlank() && resolvedCameraFacing.isNotBlank()) {
                                throw IllegalArgumentException("camera-id 与 camera-facing 不能同时设置")
                            }
                            val resolvedCameraAr = viewModel.cameraArInput.trim()
                            val resolvedCameraFps =
                                viewModel.cameraFpsInput.filter(Char::isDigit).toIntOrNull() ?: 0
                            if (resolvedVideoSource == "camera" && viewModel.cameraHighSpeed && resolvedCameraFps <= 0) {
                                throw IllegalArgumentException("启用 --camera-high-speed 时，--camera-fps 不能为 0")
                            }
                            val maxSize =
                                viewModel.maxSizeInput.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
                                    ?: 0
                            val maxFps =
                                viewModel.maxFpsInput.filter(Char::isDigit).toIntOrNull()?.toFloat() ?: 0f
                            if (resolvedVideoSource == "camera" && resolvedCameraSize.isNotBlank() && (maxSize > 0 || resolvedCameraAr.isNotBlank())) {
                                throw IllegalArgumentException("显式 camera-size 时不能同时设置 --max-size 或 --camera-ar")
                            }
                            val bitRateBps = (bitRateMbps * 1_000_000).toInt()
                            val audioBitRateBps = (audioBitRateKbps.coerceAtLeast(1)) * 1_000
                            val resolvedAudioSource = when (viewModel.audioSourcePreset) {
                                "custom" -> viewModel.audioSourceCustom.trim()
                                else -> viewModel.audioSourcePreset.trim()
                            }
                            val newDisplayArg = buildNewDisplayArg(
                                viewModel.newDisplayWidth.filter(Char::isDigit),
                                viewModel.newDisplayHeight.filter(Char::isDigit),
                                viewModel.newDisplayDpi.filter(Char::isDigit),
                            )
                            val displayId = viewModel.displayIdInput.filter(Char::isDigit).toIntOrNull()
                                ?.takeIf { it > 0 }
                            val crop = buildCropArg(
                                viewModel.cropWidth.filter(Char::isDigit),
                                viewModel.cropHeight.filter(Char::isDigit),
                                viewModel.cropX.filter(Char::isDigit),
                                viewModel.cropY.filter(Char::isDigit),
                            )
                            val effectiveTurnScreenOff = viewModel.turnScreenOff && !viewModel.noControl
                            val session = nativeCore.scrcpyStart(
                                NativeCoreFacade.defaultStartRequest(
                                    customServerUri = viewModel.customServerUri,
                                    maxSize = maxSize,
                                    maxFps = maxFps,
                                    videoBitRate = bitRateBps,
                                    remotePath = viewModel.serverRemotePath.trim(),
                                    videoCodec = viewModel.videoCodec,
                                    audio = viewModel.audioEnabled,
                                    audioCodec = viewModel.audioCodec,
                                    audioBitRate = audioBitRateBps,
                                    noControl = viewModel.noControl,
                                    videoEncoder = viewModel.videoEncoder,
                                    videoCodecOptions = viewModel.videoCodecOptions,
                                    audioEncoder = viewModel.audioEncoder,
                                    audioCodecOptions = viewModel.audioCodecOptions,
                                    audioDup = viewModel.audioDup,
                                    audioSource = resolvedAudioSource,
                                    videoSource = resolvedVideoSource,
                                    cameraId = resolvedCameraId,
                                    cameraFacing = resolvedCameraFacing,
                                    cameraSize = resolvedCameraSize,
                                    cameraAr = resolvedCameraAr,
                                    cameraFps = resolvedCameraFps,
                                    cameraHighSpeed = viewModel.cameraHighSpeed,
                                    noAudioPlayback = viewModel.noAudioPlayback,
                                    noVideo = viewModel.noVideo,
                                    requireAudio = viewModel.requireAudio,
                                    turnScreenOff = effectiveTurnScreenOff,
                                    newDisplay = newDisplayArg,
                                    displayId = displayId,
                                    crop = crop,
                                ),
                            )
                            sessionInfo = session
                            statusLine = "scrcpy 运行中"
                            @SuppressLint("DefaultLocale")
                            val videoDetail = if (viewModel.noVideo) {
                                "off"
                            } else {
                                "${session.codec} ${session.width}x${session.height} @${
                                    String.format(
                                        "%.1f",
                                        bitRateMbps
                                    )
                                }Mbps"
                            }
                            val audioDetail = if (!viewModel.audioEnabled) {
                                "off"
                            } else {
                                val playback = if (viewModel.noAudioPlayback) "(no-playback)" else ""
                                "${viewModel.audioCodec} ${audioBitRateKbps}kbps source=${resolvedAudioSource.ifBlank { "default" }}$playback"
                            }
                            logEvent("scrcpy 已启动: device=${session.deviceName}, video=$videoDetail, audio=$audioDetail, control=${!viewModel.noControl}, turnScreenOff=$effectiveTurnScreenOff, maxSize=${if (maxSize > 0) maxSize else "auto"}, maxFps=${if (maxFps > 0f) maxFps else "auto"}")
                            scope.launch {
                                snack.showSnackbar("scrcpy 已启动")
                            }
                            nativeCore.getLastScrcpyServerCommand()?.let { command ->
                                logEvent("scrcpy-server args: $command")
                            }
                        }
                    },
                    onStop = {
                        runBusy("停止 scrcpy") {
                            nativeCore.scrcpyStop()
                            sessionInfo = null
                            statusLine =
                                currentTarget?.let { "${it.host}:${it.port}" } ?: "ADB 已连接"
                            logEvent("scrcpy 已停止")
                            scope.launch {
                                snack.showSnackbar("scrcpy 已停止")
                            }
                        }
                    },
                    sessionStarted = sessionInfo != null,
                )
            }

            if (
                sessionInfo != null &&
                sessionInfo!!.width > 0 &&
                sessionInfo!!.height > 0
            ) {
                item {
                    PreviewCard(
                        sessionInfo = sessionInfo,
                        nativeCore = nativeCore,
                        previewHeightDp = previewCardHeightDp.coerceAtLeast(120),
                        controlsVisible = previewControlsVisible,
                        onTapped = {
                            previewControlsVisible = !previewControlsVisible
                        },
                        onOpenFullscreen = {
                            val info = sessionInfo ?: return@PreviewCard
                            navigation.openFullscreenPage(info)
                        },
                        onOpenFullscreenHaptic = { haptics.contextClick() },
                    )
                }
                item {
                    VirtualButtonCard(
                        busy = busy,
                        outsideActions = virtualButtonLayout.first,
                        moreActions = virtualButtonLayout.second,
                        showText = showPreviewVirtualButtonText,
                        onAction = ::sendVirtualButtonAction,
                    )
                }
            }
        }

        if (eventLog.isNotEmpty()) item {
            Spacer(Modifier.height(UiSpacing.PageItem))
            LogsPanel(lines = eventLog)
        }

        // TODO: 放进 [AppPageLazyColumn] 里
        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}

private fun buildNewDisplayArg(width: String, height: String, dpi: String): String {
    val w = width.toIntOrNull()?.takeIf { it > 0 }
    val h = height.toIntOrNull()?.takeIf { it > 0 }
    val d = dpi.toIntOrNull()?.takeIf { it > 0 }
    val sizePart = if (w != null && h != null) "${w}x${h}" else ""
    return when {
        sizePart.isNotEmpty() && d != null -> "$sizePart/$d"
        sizePart.isNotEmpty() -> sizePart
        d != null -> "/$d"
        else -> ""
    }
}

private fun buildCropArg(width: String, height: String, x: String, y: String): String {
    val w = width.toIntOrNull()?.takeIf { it > 0 } ?: return ""
    val h = height.toIntOrNull()?.takeIf { it > 0 } ?: return ""
    val ox = x.toIntOrNull()?.takeIf { it >= 0 } ?: return ""
    val oy = y.toIntOrNull()?.takeIf { it >= 0 } ?: return ""
    return "$w:$h:$ox:$oy"
}

@Composable
fun ScrcpyDevicePage(
    onOpenAdvanced: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ScrcpyUiViewModel = viewModel(factory = ScrcpyUiViewModel.Factory(app))
    val snackHostState = remember { SnackbarHostState() }
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    val navigationActions = remember(onOpenAdvanced) {
        ScrcpyNavigationActions(
            openAdvancedPage = onOpenAdvanced,
            openVirtualButtonOrder = {},
            openFullscreenPage = { session ->
                viewModel.fullscreenLaunch = FullscreenControlLaunch(
                    deviceName = session.deviceName,
                    width = session.width,
                    height = session.height,
                    codec = session.codec,
                )
            },
            openReorderDevices = { viewModel.openReorderDevicesAction?.invoke() },
            pickServer = {},
        )
    }
    val fullscreenActions = remember {
        ScrcpyFullscreenActions(
            onDismiss = { viewModel.fullscreenLaunch = null },
            onVideoSizeChanged = { _, _ -> },
        )
    }

    ProvideScrcpyUiEnvironment(
        viewModel = viewModel,
        contentPadding = PaddingValues(0.dp),
        scrollBehavior = null,
        snackHostState = snackHostState,
        themeBaseIndex = themeBaseIndex,
        navigationActions = navigationActions,
        fullscreenActions = fullscreenActions,
    ) {
        if (viewModel.fullscreenLaunch != null) {
            FullscreenControlPage()
        } else {
            DeviceTabScreen()
        }
    }
}

@Composable
fun ScrcpyAdvancedPage(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ScrcpyUiViewModel = viewModel(factory = ScrcpyUiViewModel.Factory(app))
    val snackHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(canScroll = { true })
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "高级参数",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackHostState) },
    ) { pagePadding ->
        ProvideScrcpyUiEnvironment(
            viewModel = viewModel,
            contentPadding = pagePadding,
            scrollBehavior = scrollBehavior,
            snackHostState = snackHostState,
            themeBaseIndex = themeBaseIndex,
        ) {
            AdvancedConfigPage()
        }
    }
}
