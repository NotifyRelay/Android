package com.xzyht.notifyrelay.ui.pages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.BuildConfig
import kotlinx.coroutines.launch
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Date

/**
 * 关于页面组件
 * 显示应用版本信息并提供检测更新功能
 */
@Composable
fun UIAbout(onDeveloperModeTriggered: () -> Unit = {}) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // 开发者模式触发相关状态
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }
    var isDeveloperModeEnabled by remember {
        mutableStateOf(StorageManager.getBoolean(context, "developer_mode_enabled", false))
    }
    
    // 检测更新相关状态
    var isCheckingUpdate by remember { mutableStateOf(false) }

    MiuixTheme {
        val colorScheme = MiuixTheme.colorScheme
        val textStyles = MiuixTheme.textStyles

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            // 应用名称
            Text(
                text = "Notify Relay",
                style = textStyles.title1,
                color = colorScheme.primary,
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 16.dp)
                    .align(Alignment.CenterHorizontally)
            )

            // 版本信息 - 使用SuperArrow
            SuperArrow(
                title = "版本信息",
                summary = "主版本: ${BuildConfig.VERSION_NAME}\n内部版本: ${BuildConfig.VERSION_CODE}",
                onClick = {
                    val currentTime = Date().time
                    // 检查是否在3秒内点击
                    if (currentTime - lastClickTime < 3000) {
                        clickCount++
                    } else {
                        clickCount = 1
                    }
                    lastClickTime = currentTime
                    
                    // 提供点击反馈
                    if (clickCount >= 3 && clickCount < 5) {
                        Toast.makeText(context, "再点击 ${5 - clickCount} 次进入开发者模式", Toast.LENGTH_SHORT).show()
                    } else if (clickCount >= 5) {
                        // 激活开发者模式
                        Toast.makeText(context, "开发者模式已激活", Toast.LENGTH_SHORT).show()
                        isDeveloperModeEnabled = true
                        // 保存到 StorageManager
                        StorageManager.putBoolean(context, "developer_mode_enabled", true)
                        // 重置计数器
                        clickCount = 0
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 检测更新 - 使用SuperArrow
            SuperArrow(
                title = "检测更新",
                summary = if (isCheckingUpdate) "检查中..." else "点击检查是否有新版本",
                onClick = {
                    if (isCheckingUpdate) return@SuperArrow
                    
                    isCheckingUpdate = true
                    Toast.makeText(context, "正在检查更新...", Toast.LENGTH_SHORT).show()
                    
                    // 模拟检测更新的网络请求
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(1500)
                        // 模拟检查结果
                        Toast.makeText(context, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        isCheckingUpdate = false
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 开发者模式 - 使用SuperArrow（仅当激活后显示）
            if (isDeveloperModeEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                SuperArrow(
                    title = "开发者模式",
                    summary = "点击进入开发者模式设置",
                    onClick = {
                        onDeveloperModeTriggered()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 分割线
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 版权信息
            Text(
                text = "© 2026 Notify Relay",
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }
}
