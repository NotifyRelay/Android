package com.xzyht.notifyrelay.ui.pages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.servers.clipboard.ClipboardLogDetector
import com.xzyht.notifyrelay.servers.clipboard.ClipboardSyncManager
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
/**
 * 剪贴板同步功能页面，从DeviceInterconnect中提取
 */
@Composable
fun ClipboardSyncPage() {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    
    // 无障碍服务状态
    var accessibilityEnabled by remember {
        mutableStateOf(ClipboardSyncManager.isAccessibilityServiceEnabled(context))
    }
    
    // 日志监控状态
    var logMonitoringEnabled by remember {
        mutableStateOf(ClipboardLogDetector.isMonitoring())
    }
    
    // 手动刷新权限状态的函数
    fun refreshPermissionStatus() {
        accessibilityEnabled = ClipboardSyncManager.isAccessibilityServiceEnabled(context)
        logMonitoringEnabled = ClipboardLogDetector.isMonitoring()
    }
    
    // 初始检查一次权限状态
    LaunchedEffect(Unit) {
        refreshPermissionStatus()
    }

    // 检查是否有READ_LOGS权限
    val hasReadLogsPermission = context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED
    // 检查是否有可用的剪贴板同步方案
    val hasSyncSolution = accessibilityEnabled || (logMonitoringEnabled && hasReadLogsPermission)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "剪贴板同步",
            style = textStyles.title1,
            color = colorScheme.onSurface
        )
        
        // 说明文本
        Text(
            text = "管理设备间的剪贴板同步功能",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )
        
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
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(1000)
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
        
        // 提示信息
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "注意：",
            style = textStyles.body1,
            color = colorScheme.onSurface
        )
        
        Text(
            text = "1. 剪贴板同步：\n" +
                    "   - 启用无障碍服务后自动同步\n" +
                    "   - 或启用日志监控（作为无障碍服务的替代方案）\n" +
                    "   - 否则可手动点击通知栏按钮同步",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
