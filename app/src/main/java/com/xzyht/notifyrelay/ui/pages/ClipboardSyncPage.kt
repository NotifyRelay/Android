package com.xzyht.notifyrelay.ui.pages

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.xzyht.notifyrelay.servers.clipboard.ClipboardSyncManager
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.WindowDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class ClipboardSyncMode(val displayName: String) {
    OFF("关闭"),
    ACCESSIBILITY("无障碍服务")
}

@Composable
fun ClipboardSyncPage() {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    var accessibilityEnabled by remember {
        mutableStateOf(ClipboardSyncManager.isAccessibilityServiceEnabled(context))
    }
    
    var selectedMode by remember {
        mutableIntStateOf(
            if (accessibilityEnabled) ClipboardSyncMode.ACCESSIBILITY.ordinal
            else ClipboardSyncMode.OFF.ordinal
        )
    }
    
    fun refreshPermissionStatus() {
        accessibilityEnabled = ClipboardSyncManager.isAccessibilityServiceEnabled(context)
    }
    
    LaunchedEffect(Unit) {
        refreshPermissionStatus()
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(accessibilityEnabled) {
        selectedMode = if (accessibilityEnabled) ClipboardSyncMode.ACCESSIBILITY.ordinal
        else ClipboardSyncMode.OFF.ordinal
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "剪贴板同步",
            style = textStyles.title1,
            color = colorScheme.onSurface
        )
        
        Text(
            text = "管理设备间的剪贴板同步功能",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )
        
        Text(
            text = when (selectedMode) {
                ClipboardSyncMode.ACCESSIBILITY.ordinal -> if (accessibilityEnabled) "已启用 - 剪贴板同步功能正常运行中" else "未启用 - 请在设置中开启无障碍服务"
                else -> "已关闭 - 可手动点击通知栏按钮同步"
            },
            style = textStyles.body2,
            color = when (selectedMode) {
                ClipboardSyncMode.ACCESSIBILITY.ordinal -> if (accessibilityEnabled) colorScheme.primary else colorScheme.error
                else -> colorScheme.onSurfaceSecondary
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        WindowDropdown(
            title = "同步模式",
            summary = if (accessibilityEnabled) "无障碍服务 - 已启用"
            else if (selectedMode == ClipboardSyncMode.ACCESSIBILITY.ordinal) "无障碍服务 - 点击前往设置"
            else "关闭",
            items = ClipboardSyncMode.entries.map { it.displayName },
            selectedIndex = selectedMode,
            onSelectedIndexChange = { index ->
                when (index) {
                    ClipboardSyncMode.OFF.ordinal -> {
                        selectedMode = ClipboardSyncMode.OFF.ordinal
                        ToastUtils.showShortToast(context, "已关闭剪贴板同步")
                    }
                    ClipboardSyncMode.ACCESSIBILITY.ordinal -> {
                        selectedMode = ClipboardSyncMode.ACCESSIBILITY.ordinal
                        if (!accessibilityEnabled) {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                                ToastUtils.showShortToast(context, "请在设置中启用无障碍服务")
                            } catch (e: Exception) {
                                Logger.e("ClipboardSyncPage", "打开无障碍设置失败", e)
                                ToastUtils.showShortToast(context, "打开设置失败，请手动前往设置")
                            }
                        }
                    }
                }
            }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text(
            text = "注意：",
            style = textStyles.body1,
            color = colorScheme.onSurface
        )
        
        Text(
            text = "1. 剪贴板同步：\n" +
                    "   - 启用无障碍服务后自动同步\n" +
                    "   - 关闭后可手动点击通知栏按钮同步",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
