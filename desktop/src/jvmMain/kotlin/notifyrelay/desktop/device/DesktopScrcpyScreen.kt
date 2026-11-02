package notifyrelay.desktop.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.desktop.platform.DesktopScrcpyLauncher
import notifyrelay.domain.feature.device.model.SelectedDeviceInfo
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 桌面端屏幕镜像页。
 *
 * 按当前选中设备启动 / 停止外部 scrcpy 进程；目标 adb 序列号由平台侧实测解析
 * （AndroidId / 型号匹配 → 设备偏好 → 必要时自动重连），界面展示解析结果。
 */
@Composable
internal fun DesktopScrcpyScreen(
    devicePortsProvider: DesktopDevicePortsProvider,
    fallbackDevice: SelectedDeviceInfo?,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val selectedDevice by GlobalSelectedDeviceHolder.current()
    val deviceUuid = selectedDevice?.uuid
    val deviceName = selectedDevice?.displayName ?: fallbackDevice?.displayName.orEmpty()
    val deviceIp = fallbackDevice?.ip.orEmpty()

    // 已解析的 adb 序列号；null 表示当前无可用 adb 连接
    var serial by remember(deviceUuid, deviceIp) { mutableStateOf<String?>(null) }
    var resolving by remember(deviceUuid, deviceIp) { mutableStateOf(false) }
    var running by remember(deviceUuid, deviceIp) { mutableStateOf(false) }

    // 进入页面即解析一次，避免展示与运行态基于假定的 ip:5555
    LaunchedEffect(deviceUuid, deviceIp) {
        if (deviceUuid == null) return@LaunchedEffect
        resolving = true
        serial = DesktopScrcpyLauncher.resolveSerialFor(deviceUuid, deviceName, deviceIp)
        running = serial?.let { DesktopScrcpyLauncher.isRunning(it) } ?: false
        resolving = false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("屏幕镜像", style = textStyles.title2, color = colorScheme.onSurface)

        if (deviceUuid == null) {
            Text(
                text = "请先在设备列表中选择一台已连接的设备",
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
            )
            return@Column
        }

        Text(
            text = "目标：" + deviceName,
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        Text(
            text =
                when {
                    resolving -> "正在解析 adb 连接…"
                    serial != null -> "adb 序列号：" + serial
                    else -> "未找到该设备的 adb 连接，请确认已开启 USB 调试或无线调试"
                },
            style = textStyles.body2,
            color = if (serial != null) colorScheme.onSurfaceSecondary else colorScheme.error,
        )

        Text(
            text =
                if (running) {
                    "投屏进行中。参数在「设置 → 屏幕镜像」中配置。"
                } else {
                    "停止状态。启动后由 scrcpy 创建独立窗口。"
                },
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        Button(
            onClick = {
                if (running) {
                    serial?.let { DesktopScrcpyLauncher.stop(it) }
                    running = false
                } else {
                    running = DesktopScrcpyLauncher.launch(deviceUuid, deviceName, deviceIp)
                    // 启动后回读实际序列号，失败时置空以便界面提示
                    serial = DesktopScrcpyLauncher.resolveSerialFor(deviceUuid, deviceName, deviceIp)
                }
            },
            enabled = !resolving && (running || serial != null),
            modifier = Modifier.fillMaxWidth(),
            colors =
                if (running) {
                    ButtonDefaults.buttonColors(color = colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors(color = colorScheme.primary)
                },
        ) {
            Text(if (running) "停止投屏" else "启动投屏")
        }
    }
}
