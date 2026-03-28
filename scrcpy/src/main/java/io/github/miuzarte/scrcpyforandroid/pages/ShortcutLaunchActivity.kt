package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.ScrcpySessionInfo
import io.github.miuzarte.scrcpyforandroid.services.loadMainSettings
import io.github.miuzarte.scrcpyforandroid.widgets.FullscreenControlScreen
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonActions
import io.github.miuzarte.scrcpyforandroid.widgets.VirtualButtonBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.data.config.ScrcpyDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class ShortcutLaunchActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_DEVICE_IP = "device_ip"
        private const val EXTRA_DEVICE_PORT = "device_port"
        private const val EXTRA_DEVICE_NAME = "device_name"

        fun startFullscreenControl(context: Context, ip: String, port: Int, name: String) {
            val intent = Intent(context, ShortcutLaunchActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_IP, ip)
                putExtra(EXTRA_DEVICE_PORT, port)
                putExtra(EXTRA_DEVICE_NAME, name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceIp = intent?.getStringExtra(EXTRA_DEVICE_IP)
        val devicePort = intent?.getIntExtra(EXTRA_DEVICE_PORT, ScrcpyDefaults.ADB_PORT) ?: ScrcpyDefaults.ADB_PORT
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: deviceIp ?: "设备"

        if (deviceIp.isNullOrBlank()) {
            Toast.makeText(this, "无效的设备信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            ShortcutLaunchScreen(
                deviceIp = deviceIp,
                devicePort = devicePort,
                deviceName = deviceName,
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
private fun ShortcutLaunchScreen(
    deviceIp: String,
    devicePort: Int,
    deviceName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val nativeCore = remember(context) { NativeCoreFacade.get(context.applicationContext) }
    val settings = remember(context) { loadMainSettings(context) }
    val scope = rememberCoroutineScope()

    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Connecting) }
    var sessionInfo by remember { mutableStateOf<ScrcpySessionInfo?>(null) }
    var currentFps by remember { mutableFloatStateOf(0f) }
    var virtualButtonsLayout by remember { mutableStateOf(settings.virtualButtonsLayout) }
    var showDebugInfo by remember { mutableStateOf(settings.fullscreenDebugInfoEnabled) }
    var showVirtualButtons by remember { mutableStateOf(settings.showFullscreenVirtualButtons) }

    val virtualButtonLayout = remember(virtualButtonsLayout) {
        VirtualButtonActions.splitLayout(VirtualButtonActions.parseStoredLayout(virtualButtonsLayout))
    }
    val bar = remember(virtualButtonLayout) {
        VirtualButtonBar(
            outsideActions = virtualButtonLayout.first,
            moreActions = virtualButtonLayout.second,
        )
    }

    val themeController = remember { ThemeController(colorSchemeMode = ColorSchemeMode.Dark) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            onDismiss()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "再次返回以退出", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Int, Int) -> Unit = { w, h ->
            sessionInfo?.let { sessionInfo = it.copy(width = w, height = h) }
        }
        nativeCore.addVideoSizeListener(listener)
        onDispose {
            nativeCore.removeVideoSizeListener(listener)
        }
    }

    DisposableEffect(nativeCore) {
        val listener: (Float) -> Unit = { fps -> currentFps = fps }
        nativeCore.addVideoFpsListener(listener)
        onDispose {
            nativeCore.removeVideoFpsListener(listener)
        }
    }

    LaunchedEffect(deviceIp, devicePort) {
        connectionState = ConnectionState.Connecting

        val connected = withContext(Dispatchers.IO) {
            nativeCore.adbConnect(deviceIp, devicePort)
        }

        if (!connected) {
            connectionState = ConnectionState.ConnectionFailed("ADB 连接失败")
            return@LaunchedEffect
        }

        connectionState = ConnectionState.StartingScrcpy

        val session = try {
            withContext(Dispatchers.IO) {
                nativeCore.scrcpyStart(
                    NativeCoreFacade.defaultStartRequest(
                        customServerUri = settings.customServerUri?.ifBlank { null },
                        maxSize = 0,
                        videoBitRate = (ScrcpyDefaults.VIDEO_BIT_RATE_MBPS * 1_000_000).toInt(),
                        remotePath = settings.serverRemotePath.ifBlank { ScrcpyDefaults.SERVER_REMOTE_PATH },
                        videoCodec = settings.videoCodec,
                        audio = ScrcpyDefaults.AUDIO_ENABLED,
                        audioCodec = ScrcpyDefaults.AUDIO_CODEC,
                        audioBitRate = ScrcpyDefaults.AUDIO_BIT_RATE_KBPS * 1_000,
                        noControl = false,
                        noVideo = false,
                        audioDup = ScrcpyDefaults.AUDIO_DUP,
                        audioSource = ScrcpyDefaults.AUDIO_SOURCE_PRESET,
                    )
                )
            }
        } catch (e: Exception) {
            connectionState = ConnectionState.ConnectionFailed("scrcpy 启动失败: ${e.message}")
            return@LaunchedEffect
        }

        sessionInfo = session
        connectionState = ConnectionState.Connected(session)
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                runCatching { nativeCore.scrcpyStop() }
                runCatching { nativeCore.adbDisconnect() }
            }
        }
    }

    MiuixTheme(controller = themeController) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                when (val state = connectionState) {
                    is ConnectionState.Connecting -> {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text("正在连接 $deviceName...")
                        }
                    }
                    is ConnectionState.StartingScrcpy -> {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text("正在启动 scrcpy...")
                        }
                    }
                    is ConnectionState.ConnectionFailed -> {
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            Text(state.message)
                        }
                    }
                    is ConnectionState.Connected -> {
                        val session = state.session
                        FullscreenControlScreen(
                            session = session,
                            nativeCore = nativeCore,
                            onDismiss = onDismiss,
                            showDebugInfo = showDebugInfo,
                            currentFps = currentFps,
                            enableBackHandler = false,
                            onInjectTouch = { action, pointerId, x, y, pressure, buttons ->
                                nativeCore.scrcpyInjectTouch(
                                    action = action,
                                    pointerId = pointerId,
                                    x = x,
                                    y = y,
                                    screenWidth = session.width,
                                    screenHeight = session.height,
                                    pressure = pressure,
                                    actionButton = 0,
                                    buttons = buttons,
                                )
                            },
                        )

                        if (showVirtualButtons) {
                            bar.Fullscreen(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                onAction = { action ->
                                    action.keycode?.let { keycode ->
                                        nativeCore.scrcpyInjectKeycode(0, keycode)
                                        nativeCore.scrcpyInjectKeycode(1, keycode)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class ConnectionState {
    data object Connecting : ConnectionState()
    data object StartingScrcpy : ConnectionState()
    data class ConnectionFailed(val message: String) : ConnectionState()
    data class Connected(val session: ScrcpySessionInfo) : ConnectionState()
}
