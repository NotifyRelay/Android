package notifyrelay.shared.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.base.log.Logger
import notifyrelay.domain.feature.media.MediaMessageReceiveMode
import notifyrelay.shared.ui.common.MiuixSpinnerPreference
import notifyrelay.shared.ui.port.LocalPreferencePort
import notifyrelay.shared.ui.port.LocalToastPort
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import notifyrelay.shared.ui.viewmodel.AudioRelayViewModel
import notifyrelay.shared.ui.viewmodel.MediaControlViewModel
import notifyrelay.shared.ui.viewmodel.audioRelayViewModel
import notifyrelay.shared.ui.viewmodel.mediaControlViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 音乐控制功能页面
 */
@Composable
fun MusicControlPage(
    audioViewModel: AudioRelayViewModel = audioRelayViewModel(),
    mediaViewModel: MediaControlViewModel = mediaControlViewModel(),
) {
    val prefPort = LocalPreferencePort.current ?: error("LocalPreferencePort 未提供")

    val toastPort = LocalToastPort.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    // 响应全局设备选中状态
    val selectedDeviceObj by GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceObj

    // 滚动状态
    val scrollState = rememberScrollState()

    var mediaMessageReceiveMode by remember {
        mutableStateOf(mediaViewModel.receiveMode())
    }

    // 胶囊歌词开关状态
    var capsuleLyricsEnabled by remember { mutableStateOf(prefPort.getBoolean("capsule_lyrics_enabled")) }

    // 发送媒体通知到对端
    var sendMediaNotificationsEnabled by remember { mutableStateOf(prefPort.getBoolean("send_media_notifications_enabled", true)) }

    // 音频转发方式（0: scrcpy, 1: 中继）
    var audioRelayMode by remember { mutableStateOf(prefPort.getInt("audio_relay_mode", 0)) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val toastPort = LocalToastPort.current
        // 标题
        Text(
            text = "音乐控制",
            style = textStyles.title1,
            color = colorScheme.onSurface,
        )

        // 说明文本
        Text(
            text = "管理设备间的音频转发和媒体控制功能",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        // 显示当前选中的设备
        Text(
            text = "当前选中设备: ${selectedDevice?.displayName ?: "本机"}",
            style = textStyles.body1,
            color = colorScheme.onSurface,
        )

        // 音频转发标题
        Text(
            text = "音频转发",
            style = textStyles.title2,
            color = colorScheme.onSurface,
        )

        // 音频转发说明
        Text(
            text = "与选中设备进行音频转发",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        // 音频转发按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 接收对端音频按钮
            Button(
                onClick = {
                    if (selectedDevice == null) {
                        toastPort.short("当前选中的是本机，无法接收本机音频")
                        return@Button
                    }
                    toastPort.short(audioViewModel.requestSendAudio(selectedDevice))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("发送本端音频到对端")
            }

            // 播放对端音频按钮
            Button(
                onClick = {
                    if (selectedDevice == null) {
                        toastPort.short("当前选中的是本机，无法播放本机音频")
                        return@Button
                    }
                    toastPort.short(audioViewModel.requestReceiveAudio(selectedDevice))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("播放对端音频")
            }
        }

        // 停止音频转发按钮
        Button(
            onClick = {
                audioViewModel.stopAudio()
                toastPort.short("已停止音频转发")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("停止音频转发")
        }

        // 音频转发方式
        MiuixSpinnerPreference(
            title = "音频转发方式",
            summary = "选择音频转发方式：scrcpy（经 adb 转发）或中继（直接音频流），同时控制发送与接收",
            items =
                listOf(
                    "scrcpy（默认）",
                    "中继",
                ),
            selectedIndex = audioRelayMode,
            onSelectedIndexChange = { index ->
                audioRelayMode = index
                prefPort.putInt("audio_relay_mode", index)
            },
        )

        MiuixSpinnerPreference(
            title = "接收媒体消息",
            summary = "接收远端设备媒体播放信息并以超级岛形式显示",
            items =
                listOf(
                    "开",
                    "关",
                    "仅音频时开",
                ),
            selectedIndex =
                when (mediaMessageReceiveMode) {
                    MediaMessageReceiveMode.On -> 0
                    MediaMessageReceiveMode.Off -> 1
                    MediaMessageReceiveMode.AudioOnly -> 2
                },
            onSelectedIndexChange = { index ->
                val mode =
                    when (index) {
                        1 -> MediaMessageReceiveMode.Off
                        2 -> MediaMessageReceiveMode.AudioOnly
                        else -> MediaMessageReceiveMode.On
                    }
                mediaMessageReceiveMode = mode
                mediaViewModel.setReceiveMode(mode)
            },
        )

        // 胶囊歌词开关
        SwitchPreference(
            title = "启用胶囊歌词",
            summary = "接收本机媒体播放信息并以超级岛形式显示",
            checked = capsuleLyricsEnabled,
            onCheckedChange = { enabled ->
                capsuleLyricsEnabled = enabled
                prefPort.putBoolean("capsule_lyrics_enabled", enabled)
            },
        )

        // 发送媒体通知到对端
        SwitchPreference(
            title = "发送媒体通知到它端",
            summary = "关闭后不再发送媒体通知到对端",
            checked = sendMediaNotificationsEnabled,
            onCheckedChange = { enabled ->
                sendMediaNotificationsEnabled = enabled
                prefPort.putBoolean("send_media_notifications_enabled", enabled)
            },
        )

        // 歌词分割模式设置
        var lyricsSplitMode by remember { mutableStateOf(prefPort.getInt("lyrics_split_mode", 0)) }

        MiuixSpinnerPreference(
            title = "歌词分割模式",
            summary = "默认：平板时不分割，手机时分割",
            items =
                listOf(
                    "默认",
                    "分割",
                    "不分割",
                ),
            selectedIndex = lyricsSplitMode,
            onSelectedIndexChange = { index ->
                lyricsSplitMode = index
                prefPort.putInt("lyrics_split_mode", index)
            },
        )

        // 媒体控制标题
        Text(
            text = "媒体控制",
            style = textStyles.title2,
            color = colorScheme.onSurface,
        )

        // 媒体控制说明文本
        Text(
            text = "控制当前选中设备的媒体播放",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        // 媒体控制按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 上一首按钮
            Button(
                onClick = {
                    try {
                        if (selectedDevice == null) {
                            mediaViewModel.previousLocal()
                            toastPort.short("已发送上一首指令到本机")
                        } else {
                            if (mediaViewModel.sendMediaCommand(selectedDevice, "previous")) {
                                toastPort.short("已发送上一首指令到${selectedDevice.displayName}")
                            } else {
                                toastPort.short("发送上一首指令失败")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("NotifyRelay", "发送上一首指令失败", e)
                        toastPort.short("发送上一首指令失败: ${e.message}")
                    }
                },
                modifier = Modifier.width(100.dp),
            ) {
                Text("上一首")
            }

            // 播放/暂停按钮
            Button(
                onClick = {
                    try {
                        if (selectedDevice == null) {
                            mediaViewModel.playPauseLocal()
                            toastPort.short("已发送播放/暂停指令到本机")
                        } else {
                            if (mediaViewModel.sendMediaCommand(selectedDevice, "playPause")) {
                                toastPort.short("已发送播放/暂停指令到${selectedDevice.displayName}")
                            } else {
                                toastPort.short("发送播放/暂停指令失败")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("NotifyRelay", "发送播放/暂停指令失败", e)
                        toastPort.short("发送播放/暂停指令失败: ${e.message}")
                    }
                },
                modifier = Modifier.width(100.dp),
            ) {
                Text("播放\n暂停")
            }

            // 下一首按钮
            Button(
                onClick = {
                    try {
                        if (selectedDevice == null) {
                            mediaViewModel.nextLocal()
                            toastPort.short("已发送下一首指令到本机")
                        } else {
                            if (mediaViewModel.sendMediaCommand(selectedDevice, "next")) {
                                toastPort.short("已发送下一首指令到${selectedDevice.displayName}")
                            } else {
                                toastPort.short("发送下一首指令失败")
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("NotifyRelay", "发送下一首指令失败", e)
                        toastPort.short("发送下一首指令失败: ${e.message}")
                    }
                },
                modifier = Modifier.width(100.dp),
            ) {
                Text("下一首")
            }
        }

        // 提示信息
        Text(
            text = "注意：",
            style = textStyles.body1,
            color = colorScheme.onSurface,
        )

        Text(
            text =
                "1. 请确保目标设备已连接且在线\n" +
                    "2. 音频转发功能需要目标设备开启 ADB 调试\n" +
                    "3. 目标设备需要先完成 ADB 配对\n" +
                    "4. 媒体控制功能支持播放/暂停、上一首、下一首操作\n",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
