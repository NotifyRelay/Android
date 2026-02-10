package com.xzyht.notifyrelay.feature.notification.ui.filter

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.BuildConfig
import com.xzyht.notifyrelay.common.core.notification.servers.NotifyRelayNotificationListenerService
import com.xzyht.notifyrelay.common.core.sync.ProtocolSender
import notifyrelay.core.util.Logger
import com.xzyht.notifyrelay.common.core.util.MediaControlUtil
import notifyrelay.core.util.ToastUtils
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.ui.GlobalSelectedDeviceHolder
import com.xzyht.notifyrelay.feature.notification.superisland.RemoteMediaSessionManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设备互联功能页面，提供设备间的各种互联功能
 * 包括音频转发、媒体控制、剪贴板同步等功能
 */
@Composable
fun DeviceInterconnect() {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    
    // 响应全局设备选中状态
    val selectedDeviceObj by GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceObj
    
    // 监听当前设备的通知历史变化
    val notifications by NotificationRepository.notificationHistoryFlow.collectAsState()
    
    // 滚动状态
    val scrollState = rememberScrollState()

    // 移除媒体会话相关状态
    var islandEnabled by remember { mutableStateOf(RemoteMediaSessionManager.isEnabled(context)) }
    
    // 无障碍服务状态
    var accessibilityEnabled by remember {
        mutableStateOf(ClipboardSyncManager.isAccessibilityServiceEnabled(context))
    }
    
    // 日志监控状态
    var logMonitoringEnabled by remember {
        mutableStateOf(com.xzyht.notifyrelay.feature.clipboard.ClipboardLogDetector.isMonitoring())
    }
    
    // 手动刷新权限状态的函数
    fun refreshPermissionStatus() {
        accessibilityEnabled = ClipboardSyncManager.isAccessibilityServiceEnabled(context)
        logMonitoringEnabled = com.xzyht.notifyrelay.feature.clipboard.ClipboardLogDetector.isMonitoring()
    }
    
    // 初始检查一次权限状态
    androidx.compose.runtime.LaunchedEffect(Unit) {
        refreshPermissionStatus()
    }

    // 检查是否有READ_LOGS权限
    val hasReadLogsPermission = context.checkSelfPermission(android.Manifest.permission.READ_LOGS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    // 检查是否有可用的剪贴板同步方案
    val hasSyncSolution = accessibilityEnabled || (logMonitoringEnabled && hasReadLogsPermission)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "设备互联",
            style = textStyles.title1,
            color = colorScheme.onSurface
        )
        
        // 说明文本
        Text(
            text = "管理设备间的互联功能，包括音频转发、媒体控制和剪贴板同步",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )
        
        // 显示当前选中的设备
        Text(
            text = if (BuildConfig.DEBUG) {
                "当前选中设备: ${selectedDevice?.displayName ?: "本机"}${selectedDevice?.uuid?.let { " ($it)" } ?: ""}"
            } else {
                "当前选中设备: ${selectedDevice?.displayName ?: "本机"}"
            },
            style = textStyles.body1,
            color = colorScheme.onSurface
        )
        
        // 剪贴板同步功能组
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "剪贴板同步",
                style = textStyles.title2,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // 总体状态
            Text(
                text = when {
                    accessibilityEnabled -> "已启用无障碍服务 - 剪贴板同步功能正常运行中"
                    logMonitoringEnabled && hasReadLogsPermission -> "已启用日志监控 - 剪贴板同步功能正常运行中"
                    else -> "未启用 - 剪贴板同步需要无障碍服务或日志监控支持"
                },
                style = textStyles.body2,
                color = if (hasSyncSolution) colorScheme.primary else colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // 无障碍服务选项 - 使用 SuperArrow
            SuperArrow(
                title = "无障碍服务",
                summary = if (accessibilityEnabled) "已启用" else "未启用",
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                        // 延迟刷新状态，让用户有时间从设置返回
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(1000)
                            refreshPermissionStatus()
                        }
                    } catch (e: Exception) {
                        Logger.e("DeviceInterconnect", "打开无障碍设置失败", e)
                        ToastUtils.showShortToast(context, "打开设置失败，请手动前往设置")
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // 日志监控选项 - 使用 SuperArrow
            SuperArrow(
                title = "日志监控",
                summary = when {
                    logMonitoringEnabled && hasReadLogsPermission -> "已启用 - 作为无障碍服务的替代方案"
                    !hasReadLogsPermission -> "未授权 - 需要 READ_LOGS 权限"
                    else -> "未启用 - 作为无障碍服务的替代方案"
                },
                onClick = {
                    // 尝试启动日志监控
                    ClipboardSyncManager.startLogMonitoring(context)
                    // 刷新状态
                    refreshPermissionStatus()
                }
            )
        }
        
        // 添加分割线，分隔剪贴板块与其他部分
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        
        // 音频转发按钮
        Button(
            onClick = {
                if (selectedDevice == null) {
                    ToastUtils.showShortToast(context, "当前选中的是本机，无法转发音频到本机")
                    return@Button
                }
                
                try {
                    val deviceManager = DeviceConnectionManager.getInstance(context)
                    val success = deviceManager.requestAudioForwarding(selectedDevice)
                    
                    if (success) {
                        ToastUtils.showShortToast(context, "音频转发请求已发送")
                    } else {
                        ToastUtils.showShortToast(context, "音频转发请求发送失败")
                    }
                } catch (e: Exception) {
                    Logger.e("NotifyRelay", "音频转发请求发送异常", e)
                    ToastUtils.showShortToast(context, "音频转发请求发送异常: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始音频转发")
        }
        
        // 远端媒体超级岛显示开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "启用远端媒体超级岛显示",
                    style = textStyles.body1,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "接收远端设备媒体播放信息并以超级岛形式显示",
                    style = textStyles.body2,
                    color = colorScheme.onSurfaceSecondary
                )
            }
            Switch(
                checked = islandEnabled,
                onCheckedChange = { enabled ->
                    islandEnabled = enabled
                    RemoteMediaSessionManager.setEnabled(context, enabled)
                    if (!enabled) {
                        RemoteMediaSessionManager.clearSession()
                    }
                }
            )
        }
        
        // 媒体控制标题
        Text(
            text = "媒体控制",
            style = textStyles.title2,
            color = colorScheme.onSurface
        )
        
        // 媒体控制说明文本
        Text(
            text = "控制当前选中设备的媒体播放",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )
        
        // 媒体控制按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 上一首按钮
            Button(
                onClick = {
                    try {
                        if (selectedDevice == null) {
                            val sbn = NotifyRelayNotificationListenerService.latestMediaSbn
                            if (sbn != null) {
                                MediaControlUtil.triggerPreviousFromNotification(sbn)
                                ToastUtils.showShortToast(context, "已发送上一首指令到本机")
                            } else {
                                ToastUtils.showShortToast(context, "未找到媒体通知，请启用通知监听服务或使用 PendingIntent")
                            }
                        } else {
                            val deviceManager = DeviceConnectionManager.getInstance(context)
                            val request = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"previous\"}"
                            ProtocolSender.sendEncrypted(deviceManager, selectedDevice, "DATA_MEDIA_CONTROL", request)
                            ToastUtils.showShortToast(context, "已发送上一首指令到${selectedDevice.displayName}")
                        }
                    } catch (e: Exception) {
                        Logger.e("NotifyRelay", "发送上一首指令失败", e)
                        ToastUtils.showShortToast(context, "发送上一首指令失败: ${e.message}")
                    }
                },
                modifier = Modifier.width(100.dp)
            ) {
                Text("上一首")
            }
            
            // 播放/暂停按钮
            Button(
                onClick = {
                    try {
                        if (selectedDevice == null) {
                            val sbn = NotifyRelayNotificationListenerService.latestMediaSbn
                            if (sbn != null) {
                                MediaControlUtil.triggerPlayPauseFromNotification(sbn)
                                ToastUtils.showShortToast(context, "已发送播放/暂停指令到本机")
                            } else {
                                ToastUtils.showShortToast(context, "未找到媒体通知，请启用通知监听服务或使用 PendingIntent")
                            }
                        } else {
                            val deviceManager = DeviceConnectionManager.getInstance(context)
                            val request = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"playPause\"}"
                            ProtocolSender.sendEncrypted(deviceManager, selectedDevice, "DATA_MEDIA_CONTROL", request)
                            ToastUtils.showShortToast(context, "已发送播放/暂停指令到${selectedDevice.displayName}")
                        }
                    } catch (e: Exception) {
                        Logger.e("NotifyRelay", "发送播放/暂停指令失败", e)
                        ToastUtils.showShortToast(context, "发送播放/暂停指令失败: ${e.message}")
                    }
                },
                modifier = Modifier.width(100.dp)
            ) {
                Text("播放\n暂停")
            }
            
            // 下一首按钮
            Button(
                onClick = {
                    try {
                        if (selectedDevice == null) {
                            val sbn = NotifyRelayNotificationListenerService.latestMediaSbn
                            if (sbn != null) {
                                MediaControlUtil.triggerNextFromNotification(sbn)
                                ToastUtils.showShortToast(context, "已发送下一首指令到本机")
                            } else {
                                ToastUtils.showShortToast(context, "未找到媒体通知，请启用通知监听服务或使用 PendingIntent")
                            }
                        } else {
                            val deviceManager = DeviceConnectionManager.getInstance(context)
                            val request = "{\"type\":\"MEDIA_CONTROL\",\"action\":\"next\"}"
                            ProtocolSender.sendEncrypted(deviceManager, selectedDevice, "DATA_MEDIA_CONTROL", request)
                            ToastUtils.showShortToast(context, "已发送下一首指令到${selectedDevice.displayName}")
                        }
                    } catch (e: Exception) {
                        Logger.e("NotifyRelay", "发送下一首指令失败", e)
                        ToastUtils.showShortToast(context, "发送下一首指令失败: ${e.message}")
                    }
                },
                modifier = Modifier.width(100.dp)
            ) {
                Text("下一首")
            }
        }
        
        // 提示信息
        Text(
            text = "注意：",
            style = textStyles.body1,
            color = colorScheme.onSurface
        )
        
        Text(
            text = "1. 请确保目标设备已连接且在线\n" +
                    "2. 音频转发功能需要目标设备支持\n" +
                    "3. 目标设备暂时只能是pc,且需要adb调试开启,因为转发利用的是scrcpy\n" +
                    "4. 媒体控制功能支持播放/暂停、上一首、下一首操作\n" +
                    "5. 剪贴板同步：\n" +
                    "   - 启用无障碍服务后自动同步\n" +
                    "   - 或启用日志监控（作为无障碍服务的替代方案）\n" +
                    "   - 否则可手动点击通知栏按钮同步",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
