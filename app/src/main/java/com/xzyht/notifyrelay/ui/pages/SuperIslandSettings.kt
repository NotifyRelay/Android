package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.notification.superisland.lifecyle.NotificationGenerator.SpecInjectionMode
import com.xzyht.notifyrelay.ui.DeveloperModeActivity
import com.xzyht.notifyrelay.ui.dialog.SuperIslandTestDialog
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ToastUtils
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SUPER_ISLAND_KEY = "superisland_enabled"
private const val SUPER_ISLAND_SHOW_KEY = "superisland_show"
private const val SUPER_ISLAND_FLOATING_WINDOW_KEY = "super_island_floating_window"
private const val SPEC_INJECTION_MODE_KEY = "spec_injection_mode"

// 注入方式选项
val specInjectionOptions = listOf(
    "仅超级岛规范信息注入" to SpecInjectionMode.SUPER_ISLAND,
    "仅Live Updates规范信息注入" to SpecInjectionMode.LIVE_UPDATES,
    "两者都注入" to SpecInjectionMode.BOTH
)

@Composable
fun UISuperIslandSettings() {
    val context = LocalContext.current
    
    // 版本比较函数
    fun isVersionGreaterThan(version: String?, target: String?): Boolean {
        if (version == null || target == null) return false
        
        try {
            // 去除OS前缀
            val versionNum = version.replace("OS", "")
            val targetNum = target.replace("OS", "")
            
            // 分割版本号
            val versionParts = versionNum.split(".").mapNotNull { it.toIntOrNull() }
            val targetParts = targetNum.split(".").mapNotNull { it.toIntOrNull() }
            
            // 比较版本号
            for (i in 0 until Math.max(versionParts.size, targetParts.size)) {
                val versionPart = versionParts.getOrElse(i) { 0 }
                val targetPart = targetParts.getOrElse(i) { 0 }
                
                if (versionPart > targetPart) return true
                if (versionPart < targetPart) return false
            }
            
            return false // 版本相同
        } catch (e: Exception) {
            return false
        }
    }
    
    // 计算浮窗兼容的默认值
    val defaultFloatingWindowEnabled = run {
        val detailedOsVersion = PermissionHelper.getDetailedOsVersion()
        val isGreater = isVersionGreaterThan(detailedOsVersion, "OS3.0.300")
        // 版本高于OS3.0.300时默认关闭
        !isGreater
    }
    
    var enabled by remember { mutableStateOf(StorageManager.getBoolean(context, SUPER_ISLAND_KEY, true)) }
    var showSuperIsland by remember { mutableStateOf(StorageManager.getBoolean(context, SUPER_ISLAND_SHOW_KEY, true)) }
    var floatingWindowEnabled by remember { mutableStateOf(StorageManager.getBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, defaultFloatingWindowEnabled)) }
    
    // 读取注入模式，如果不存在则默认为BOTH（两者都注入）
    val savedInjectionModeOrdinal = StorageManager.getInt(context, SPEC_INJECTION_MODE_KEY, SpecInjectionMode.BOTH.ordinal)
    val savedInjectionMode = SpecInjectionMode.values().getOrElse(savedInjectionModeOrdinal) { SpecInjectionMode.BOTH }
    var specInjectionMode by remember { mutableStateOf(savedInjectionMode) }
    
    // 检查是否已有用户设置
    val hasFloatingWindowSetting = StorageManager.getString(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, "") != ""
    
    // 测试对话框状态
    val showTestDialog = remember { mutableStateOf(false) }
    
    // 构建浮窗兼容开关的摘要文本
    val floatingWindowSummary = run {
        val baseSummary = "用于a16的livedata通知api被系统支持前使用浮窗展示超级岛"
        // 获取当前详细OS版本
        val currentOsVersion = PermissionHelper.getDetailedOsVersion() ?: "未知"
        // 版本比较结果
        val versionComparisonResult = isVersionGreaterThan(currentOsVersion, "OS3.0.300")
        // 在debug构建下显示预设默认值和当前版本及比较结果
        if (DeveloperModeActivity.DEBUG_UI_ENABLED.value) {
            "$baseSummary (当前版本: $currentOsVersion, 版本比较: ${if (versionComparisonResult) "高于" else "低于或等于"} OS3.0.300, 有用户设置: ${if (hasFloatingWindowSetting) "是" else "否"}, 预设默认值: ${if (defaultFloatingWindowEnabled) "开启" else "关闭"})"
        } else {
            baseSummary
        }
    }

    MiuixTheme {
        val colorScheme = MiuixTheme.colorScheme
        val textStyles = MiuixTheme.textStyles

        Scaffold(
            popupHost = { }, // 置空，避免与顶层Scaffold冲突
        ) {
            Surface(color = colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(remember { androidx.compose.foundation.ScrollState(0) }),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SwitchPreference(
                        title = "超级岛读取",
                        summary = "控制是否尝试从本机通知中读取小米超级岛数据并转发",
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            StorageManager.putBoolean(context, SUPER_ISLAND_KEY, it)
                        }
                    )

                    SwitchPreference(
                        title = "超级岛显示",
                        summary = "控制是否显示来自远端的超级岛",
                        checked = showSuperIsland,
                        onCheckedChange = {
                            showSuperIsland = it
                            StorageManager.putBoolean(context, SUPER_ISLAND_SHOW_KEY, it)
                            ToastUtils.showShortToast(context, "功能开发中")
                        }
                    )

                    SwitchPreference(
                        title = "浮窗兼容",
                        summary = floatingWindowSummary,
                        checked = floatingWindowEnabled,
                        onCheckedChange = {
                            floatingWindowEnabled = it
                            StorageManager.putBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, it)
                        }
                    )

                    WindowDropdownPreference(
                        title = "规范信息注入方式",
                        summary = "控制通知中注入的规范信息类型",
                        items = specInjectionOptions.map { it.first },
                        selectedIndex = specInjectionOptions.indexOfFirst { it.second == specInjectionMode },
                        onSelectedIndexChange = { index ->
                            if (index in specInjectionOptions.indices) {
                                specInjectionMode = specInjectionOptions[index].second
                                StorageManager.putInt(context, SPEC_INJECTION_MODE_KEY, specInjectionMode.ordinal)
                            }
                        }
                    )

                    ArrowPreference(
                        title = "测试超级岛分支",
                        onClick = {
                            showTestDialog.value = true
                        }
                    )

                }
            }
        }

        // 显示测试对话框
        SuperIslandTestDialog(showTestDialog, context)
    }
}
