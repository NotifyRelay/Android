package notifyrelay.desktop.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
 * 只做「按当前选中设备启动 / 停止外部 scrcpy 进程」；
 * 具体参数由设置页按设备配置，投屏窗口由 scrcpy 自身创建。
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
    // 序列号取设备 IP + scrcpy 默认 adb 端口；scrcpy 的 -s 接受 ip:port 形式
    val serial = fallbackDevice?.ip?.takeIf { it.isNotBlank() }?.let { it + ":5555" }

    var running by remember(serial) {
        mutableStateOf(serial != null && DesktopScrcpyLauncher.isRunning(serial))
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("屏幕镜像", style = textStyles.title2, color = colorScheme.onSurface)

        if (deviceUuid == null || serial == null) {
            Text(
                text = "请先在设备列表中选择一台已连接的设备",
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
            )
            return@Column
        }

        Text(
            text = "目标：${selectedDevice?.displayName ?: ""}（$serial）",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        Text(
            text =
                if (running) {
                    "投屏进行中。参数在「设置 → 屏幕镜像」中按设备配置。"
                } else {
                    "停止状态。启动后由 scrcpy 创建独立窗口。"
                },
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        Button(
            onClick = {
                running =
                    if (running) {
                        DesktopScrcpyLauncher.stop(serial)
                        false
                    } else {
                        DesktopScrcpyLauncher.launch(deviceUuid, serial)
                    }
            },
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
