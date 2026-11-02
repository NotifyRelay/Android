package notifyrelay.shared.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.data.config.ScrcpyAudioOutputMode
import notifyrelay.data.config.ScrcpyDevicePreference
import notifyrelay.data.config.ScrcpyOptions
import notifyrelay.data.config.ScrcpySettings
import notifyrelay.shared.ui.common.MiuixSpinnerPreference
import notifyrelay.shared.ui.common.ScrollableTopAppBarPage
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.port.LocalScrcpyPort
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 音频输出模式的可选文案，顺序与 [ScrcpyAudioOutputMode] 一致。 */
private val AUDIO_OUTPUT_LABELS = listOf("桌面播放", "仅远端", "两端同时")

/** 目标设备选择偏好的可选文案，顺序与 [ScrcpyDevicePreference] 一致。 */
private val DEVICE_PREFERENCE_LABELS = listOf("自动（优先 USB）", "优先 USB", "优先无线", "每次询问")

/**
 * 屏幕镜像设置页。
 *
 * 工具路径为全局配置；其余选项按「当前选中设备」分别保存（对齐 Windows 端按设备维度）。
 */
@Composable
fun ScrcpySettingsPage(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    ScrollableTopAppBarPage(
        title = "屏幕镜像",
        onBack = { navigator.pop() },
    ) {
        ScrcpySettingsContent(modifier)
    }
}

/** 设置页正文：由 [ScrcpySettingsPage] 套上带 TopAppBar 的页面容器。 */
@Composable
private fun ScrcpySettingsContent(modifier: Modifier = Modifier) {
    val scrcpyPort = LocalScrcpyPort.current ?: error("LocalScrcpyPort 未提供")
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val selectedDevice by GlobalSelectedDeviceHolder.current()
    val deviceUuid = selectedDevice?.uuid

    var scrcpyPath by remember { mutableStateOf(scrcpyPort.scrcpyPath()) }
    var adbPath by remember { mutableStateOf(scrcpyPort.adbPath()) }
    // 投屏参数为全局配置，切换设备不重新加载；解锁配置在切换设备时刷新
    var settings by remember(deviceUuid) {
        mutableStateOf(scrcpyPort.loadSettings(deviceUuid))
    }

    fun update(next: ScrcpySettings) {
        settings = next
        scrcpyPort.saveSettings(deviceUuid, next)
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("工具路径", style = textStyles.title2, color = colorScheme.onSurface)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = scrcpyPath,
                onValueChange = {
                    scrcpyPath = it
                    scrcpyPort.setScrcpyPath(it)
                },
                modifier = Modifier.weight(1f),
                label = "scrcpy 可执行文件",
            )
            Button(onClick = {
                scrcpyPort.pickScrcpyExecutable()?.let {
                    scrcpyPath = it
                    adbPath = scrcpyPort.adbPath()
                }
            }) {
                Text("浏览")
            }
        }

        TextField(
            value = adbPath,
            onValueChange = {
                adbPath = it
                scrcpyPort.setAdbPath(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = "adb 可执行文件（默认与 scrcpy 同目录）",
        )

        Text("画面", style = textStyles.title2, color = colorScheme.onSurface)

        SwitchPreference(
            title = "关闭设备屏幕",
            summary = "投屏时熄灭设备屏幕以省电",
            checked = settings.screenOff,
            onCheckedChange = { update(settings.copy(screenOff = it)) },
        )

        SwitchPreference(
            title = "物理键盘",
            summary = "以 UHID 方式接入，支持游戏与外接键盘",
            checked = settings.physicalKeyboard,
            onCheckedChange = { update(settings.copy(physicalKeyboard = it)) },
        )

        MiuixSpinnerPreference(
            title = "视频编解码",
            summary = ScrcpyOptions.videoCodecs[settings.videoCodec].display,
            items = ScrcpyOptions.videoCodecs.map { it.display },
            selectedIndex = settings.videoCodec,
            onSelectedIndexChange = { update(settings.copy(videoCodec = it)) },
        )

        MiuixSpinnerPreference(
            title = "显示方向",
            summary = ScrcpyOptions.displayOrientations[settings.displayOrientation].display,
            items = ScrcpyOptions.displayOrientations.map { it.display },
            selectedIndex = settings.displayOrientation,
            onSelectedIndexChange = { update(settings.copy(displayOrientation = it)) },
        )

        TextField(
            value = settings.videoResolution,
            onValueChange = { update(settings.copy(videoResolution = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "最大边长（留空不限制）",
        )

        TextField(
            value = settings.videoBitrate,
            onValueChange = { update(settings.copy(videoBitrate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "视频码率（如 8M）",
        )

        TextField(
            value = settings.frameRate,
            onValueChange = { update(settings.copy(frameRate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "最大帧率（如 60）",
        )

        Text("声音", style = textStyles.title2, color = colorScheme.onSurface)

        SwitchPreference(
            title = "仅转发音频",
            summary = "不投屏，只转发设备声音",
            checked = settings.disableVideoForwarding,
            onCheckedChange = { update(settings.copy(disableVideoForwarding = it)) },
        )

        MiuixSpinnerPreference(
            title = "音频输出",
            summary = AUDIO_OUTPUT_LABELS[settings.audioOutputMode.ordinal],
            items = AUDIO_OUTPUT_LABELS,
            selectedIndex = settings.audioOutputMode.ordinal,
            onSelectedIndexChange = { index ->
                update(settings.copy(audioOutputMode = ScrcpyAudioOutputMode.entries[index]))
            },
        )

        MiuixSpinnerPreference(
            title = "音频编解码",
            summary = ScrcpyOptions.audioCodecs[settings.audioCodec].display,
            items = ScrcpyOptions.audioCodecs.map { it.display },
            selectedIndex = settings.audioCodec,
            onSelectedIndexChange = { update(settings.copy(audioCodec = it)) },
        )

        TextField(
            value = settings.audioBitrate,
            onValueChange = { update(settings.copy(audioBitrate = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "音频码率（如 128K）",
        )

        Text("高级", style = textStyles.title2, color = colorScheme.onSurface)

        TextField(
            value = settings.customArguments,
            onValueChange = { update(settings.copy(customArguments = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "自定义参数（原样追加）",
        )

        SwitchPreference(
            title = "启用虚拟显示",
            summary = "仅在启动应用（--start-app）时生效；关闭则复用主显示",
            checked = settings.virtualDisplayEnabled,
            onCheckedChange = { update(settings.copy(virtualDisplayEnabled = it)) },
        )

        TextField(
            value = settings.virtualDisplaySize,
            onValueChange = { update(settings.copy(virtualDisplaySize = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = "虚拟显示尺寸（如 1920x1080，留空用主显示尺寸）",
        )

        Text("连接（按设备）", style = textStyles.title2, color = colorScheme.onSurface)

        if (deviceUuid == null) {
            Text(
                text = "连接方式按设备保存，请先在设备列表中选择一台设备",
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
            )
        }

        MiuixSpinnerPreference(
            title = "目标设备选择",
            summary = DEVICE_PREFERENCE_LABELS[settings.devicePreference.ordinal],
            items = DEVICE_PREFERENCE_LABELS,
            selectedIndex = settings.devicePreference.ordinal,
            onSelectedIndexChange = { index ->
                update(settings.copy(devicePreference = ScrcpyDevicePreference.entries[index]))
            },
        )

        SwitchPreference(
            title = "允许启用无线调试",
            summary = "无可用 adb 连接时，对 USB 设备启用 tcpip 后重连（会重启对端 adbd）",
            checked = settings.adbTcpipModeEnabled,
            onCheckedChange = { update(settings.copy(adbTcpipModeEnabled = it)) },
        )

        Text("解锁（按设备）", style = textStyles.title2, color = colorScheme.onSurface)

        if (deviceUuid == null) {
            Text(
                text = "解锁配置按设备保存，请先在设备列表中选择一台设备",
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
            )
        }

        SwitchPreference(
            title = "启动前解锁设备",
            summary = "仅在设备锁屏时执行下列命令",
            checked = settings.unlockBeforeLaunch,
            onCheckedChange = { update(settings.copy(unlockBeforeLaunch = it)) },
        )

        TextField(
            value = settings.unlockTimeout.toString(),
            onValueChange = { raw ->
                raw.toIntOrNull()?.let { update(settings.copy(unlockTimeout = it)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "密码缓存分钟数（0 表示每次输入）",
        )

        TextField(
            value = settings.unlockCommands,
            onValueChange = { update(settings.copy(unlockCommands = it)) },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            label = "解锁命令（每行一条，%pwd% 为密码占位符）",
        )

        Text(
            text = "示例：\u000Ainput keyevent 26\u000Ainput keyevent 66\u000Ainput text %pwd%",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )
    }
}
