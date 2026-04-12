package io.github.miuzarte.scrcpyforandroid.pages

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.haptics.rememberAppHaptics
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.model.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.services.DevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.fetchConnectedDeviceInfo
import io.github.miuzarte.scrcpyforandroid.services.loadDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.parseQuickTarget
import io.github.miuzarte.scrcpyforandroid.services.saveDevicePageSettings
import io.github.miuzarte.scrcpyforandroid.services.PinShortcutManager
import io.github.miuzarte.scrcpyforandroid.widgets.ConfigPanel
import io.github.miuzarte.scrcpyforandroid.widgets.DeviceTile
import io.github.miuzarte.scrcpyforandroid.widgets.LogsPanel
import io.github.miuzarte.scrcpyforandroid.widgets.PairingCard
import io.github.miuzarte.scrcpyforandroid.widgets.QuickConnectCard
import io.github.miuzarte.scrcpyforandroid.widgets.SectionSmallTitle
import io.github.miuzarte.scrcpyforandroid.widgets.StatusCard
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
private const val LOG_TAG = "DevicePage"

private val StringStateListSaver =
    listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )

@Composable
fun DeviceTabScreen(
    selectedDevice: notifyrelay.data.model.SelectedDeviceInfo? = null,
) {
    val viewModel = LocalScrcpyUiViewModel.current
    val navigation = LocalScrcpyNavigation.current
    val contentPadding = LocalScrcpyPagePadding.current
    val scrollBehavior = LocalScrcpyScrollBehavior.current
    val snack = LocalScrcpySnackbarHostState.current ?: remember { SnackbarHostState() }
    val themeBaseIndex = LocalScrcpyThemeBaseIndex.current
    val nativeCore = viewModel.nativeCore
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
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
    val sessionReconnectBlacklistHosts = remember { mutableSetOf<String>() }

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
        disconnectAdbConnection()
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

        // Background auto reconnect pipeline: mDNS discovery only
        while (!adbConnected && viewModel.adbAutoReconnectPairedDevice) {
            if (busy || adbConnecting || sessionInfo != null) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
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

            if (adbConnected || adbConnecting) {
                delay(ADB_AUTO_RECONNECT_RETRY_INTERVAL_MS)
                continue
            }

            val ok = runAutoAdbConnect(discoveredHost, discoveredPort)
            adbConnected = ok
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
        onDispose {
            viewModel.refreshEncodersAction = null
            viewModel.refreshCameraSizesAction = null
            viewModel.clearLogsAction = null
            viewModel.canClearLogs = false
        }
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
                selectedDevice = selectedDevice,
                themeBaseIndex = themeBaseIndex,
            )
        }

        // 显示当前选中的设备
        if (selectedDevice != null) {
            item {
                val host = selectedDevice.ip
                val port = ScrcpyDefaults.ADB_PORT
                val isConnectedTarget =
                    adbConnected && currentTarget?.host == host && currentTarget.port == port
                val isPcDevice = selectedDevice.deviceType == "pc"

                DeviceTile(
                    device = selectedDevice,
                    actionText = if (isPcDevice) "无法连接" else if (isConnectedTarget) "断开" else "连接",
                    actionEnabled = !isPcDevice && !busy && !adbConnecting,
                    actionInProgress = adbConnecting,
                    onContentClick = {
                        scope.launch {
                            if (isPcDevice) {
                                snack.showSnackbar("PC设备无法连接")
                            } else {
                                snack.showSnackbar("点击连接按钮可连接设备")
                            }
                        }
                    },
                    onAction = {
                        haptics.contextClick()
                        if (isPcDevice) {
                            scope.launch {
                                snack.showSnackbar("PC设备无法连接")
                            }
                        } else if (isConnectedTarget) {
                            runAdbConnect("断开 ADB") {
                                sessionReconnectBlacklistHosts.add(host)
                                disconnectAdbConnection(
                                    logMessage = "ADB 已断开: ${selectedDevice.displayName}",
                                    showSnackMessage = "ADB 已断开",
                                )
                            }
                        } else {
                            runAdbConnect("连接 ADB") {
                                disconnectCurrentTargetBeforeConnecting(host, port)
                                val ok = connectWithTimeout(host, port)
                                adbConnected = ok
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
                    onLongClick = {
                        if (!isPcDevice) {
                            haptics.contextClick()
                            val success = PinShortcutManager.createPinnedShortcut(
                                context,
                                deviceName = selectedDevice.displayName,
                                deviceIp = selectedDevice.ip,
                                devicePort = port
                            )
                            scope.launch {
                                if (success) {
                                    snack.showSnackbar("已创建桌面快捷方式: ${selectedDevice.displayName}")
                                } else {
                                    snack.showSnackbar("创建快捷方式失败，请检查系统设置")
                                }
                            }
                        }
                    },
                )
            }
        } else {
            item {
                SectionSmallTitle("未选中设备")
                Text(
                    text = "请先在设备列表页面选择一个设备",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = UiSpacing.PageHorizontal)
                )
            }
        }

        if (!adbConnected) item {
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

                        if (ok) {
                            try {
                                logEvent("正在发现ADB连接端口...", Log.INFO)
                                val connectInfo = nativeCore.adbDiscoverConnectService(
                                    timeoutMs = 5000,
                                    includeLanDevices = viewModel.adbMdnsLanDiscoveryEnabled,
                                )

                                if (connectInfo != null) {
                                    val (connectHost, connectPort) = connectInfo
                                    logEvent("发现ADB端口: $connectHost:$connectPort", Log.INFO)

                                    val connected = nativeCore.adbConnect(connectHost, connectPort)
                                    if (connected) {
                                        logEvent("已连接到ADB端口: $connectHost:$connectPort", Log.INFO)

                                        val tcpipOk = nativeCore.adbSetTcpPort(5555)
                                        if (tcpipOk) {
                                            logEvent("已启用 TCP/IP 模式，端口: 5555", Log.INFO)

                                            nativeCore.adbDisconnect()

                                            Thread.sleep(1000)

                                            val reconnectOk = nativeCore.adbConnect(connectHost, 5555)
                                            if (reconnectOk) {
                                                logEvent("已连接到5555端口", Log.INFO)
                                            } else {
                                                logEvent("连接到5555端口失败", Log.WARN)
                                            }
                                        } else {
                                            logEvent("启用 TCP/IP 模式失败", Log.WARN)
                                        }
                                    } else {
                                        logEvent("连接到ADB端口失败: $connectHost:$connectPort", Log.WARN)
                                    }
                                } else {
                                    logEvent("未发现ADB连接端口", Log.WARN)
                                }
                            } catch (e: Exception) {
                                logEvent("启用 TCP/IP 模式失败: ${e.message}", Log.WARN)
                            }
                        }

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
                        bitRateInput = String.format(Locale.US, "%.1f", it)
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
                    onStopHaptic = { haptics.contextClick() },
                    onFullscreenHaptic = { haptics.contextClick() },
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
                    onOpenFullscreen = {
                        if (currentTargetHost.isNotBlank()) {
                            val displayName = connectedDeviceLabel
                            ShortcutLaunchActivity.startFullscreenControl(
                                context,
                                currentTargetHost,
                                currentTargetPort,
                                displayName,
                                videoBitRateMbps = bitRateMbps,
                                audioBitRateKbps = audioBitRateKbps,
                                videoCodec = viewModel.videoCodec,
                                audioCodec = viewModel.audioCodec,
                                audioEnabled = viewModel.audioEnabled,
                                serverRemotePath = viewModel.serverRemotePath.trim().ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH },
                                customServerUri = viewModel.customServerUri,
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
                            )
                        }
                    },
                    sessionStarted = sessionInfo != null,
                )
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
    selectedDevice: notifyrelay.data.model.SelectedDeviceInfo? = null,
    onOpenAdvanced: () -> Unit = {},
    viewModel: ScrcpyUiViewModel,
) {
    val context = LocalContext.current
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
            openFullscreenPage = { _, _, _ -> },
            pickServer = {},
        )
    }

    ProvideScrcpyUiEnvironment(
        viewModel = viewModel,
        contentPadding = PaddingValues(0.dp),
        scrollBehavior = null,
        snackHostState = snackHostState,
        themeBaseIndex = themeBaseIndex,
        navigationActions = navigationActions,
    ) {
        DeviceTabScreen(selectedDevice = selectedDevice)
    }
}

@Composable
fun ScrcpyAdvancedPage(
    onBack: () -> Unit,
    viewModel: ScrcpyUiViewModel,
) {
    val context = LocalContext.current
    val snackHostState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior(canScroll = { true })
    var themeBaseIndex by remember { mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(context)) }
    val scope = rememberCoroutineScope()

    DisposableEffect(context) {
        val listener = ThemeSettingsManager.ThemeChangeListener { newBaseIndex ->
            themeBaseIndex = newBaseIndex
        }
        ThemeSettingsManager.addThemeChangeListener(context, listener)
        onDispose {
            ThemeSettingsManager.removeThemeChangeListener(context, listener)
        }
    }

    // 刷新编码器列表
    fun refreshEncoderLists() {
        val remotePath = viewModel.serverRemotePath.trim().ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH }
        runCatching {
            viewModel.nativeCore.scrcpyListEncoders(
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
            scope.launch {
                snackHostState.showSnackbar("编码器列表已刷新")
            }
        }.onFailure { e ->
            viewModel.videoEncoderOptions.clear()
            viewModel.audioEncoderOptions.clear()
            viewModel.videoEncoderTypeMap.clear()
            viewModel.audioEncoderTypeMap.clear()
            scope.launch {
                snackHostState.showSnackbar("读取编码器列表失败: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // 刷新 Camera Sizes
    fun refreshCameraSizeLists() {
        val remotePath = viewModel.serverRemotePath.trim().ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH }
        runCatching {
            viewModel.nativeCore.scrcpyListCameraSizes(
                customServerUri = viewModel.customServerUri,
                remotePath = remotePath,
            )
        }.onSuccess { lists ->
            viewModel.cameraSizeOptions.clear()
            viewModel.cameraSizeOptions.addAll(lists.sizes)
            if (viewModel.cameraSizePreset.isNotBlank() && viewModel.cameraSizePreset != "custom" && viewModel.cameraSizePreset !in lists.sizes) {
                viewModel.cameraSizePreset = ""
            }
            scope.launch {
                snackHostState.showSnackbar("Camera sizes 已刷新: count=${lists.sizes.size}")
            }
        }.onFailure { e ->
            viewModel.cameraSizeOptions.clear()
            scope.launch {
                snackHostState.showSnackbar("读取 camera sizes 失败: ${e.message ?: e.javaClass.simpleName}")
            }
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
            AdvancedConfigPage(
                onRefreshEncoders = { refreshEncoderLists() },
                onRefreshCameraSizes = { refreshCameraSizeLists() },
            )
        }
    }
}
