package com.xzyht.notifyrelay.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.common.NotifyRelayTheme
import com.xzyht.notifyrelay.ui.common.ProvideNavigationEventDispatcherOwner
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.WindowDropdown
import top.yukonga.miuix.kmp.theme.MiuixTheme

class DeveloperModeActivity : AppCompatActivity() {

    companion object {
        private const val KEY_LOG_ENABLED = "log_enabled"
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_DEBUG_UI_ENABLED = "debug_ui_enabled"

        val DEBUG_UI_ENABLED: MutableState<Boolean> = mutableStateOf(false)

        fun initLogConfig(context: Context) {
            val logLevelOrdinal = StorageManager.getInt(context, KEY_LOG_LEVEL, Logger.Level.INFO.ordinal)
            Logger.CURRENT_LEVEL = Logger.Level.values().getOrElse(logLevelOrdinal) { Logger.Level.INFO }
        }

        fun initDebugUiConfig(context: Context) {
            DEBUG_UI_ENABLED.value = StorageManager.getBoolean(context, KEY_DEBUG_UI_ENABLED, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先设置沉浸式虚拟键和状态栏
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this.window, false)
        this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            ProvideNavigationEventDispatcherOwner {
                val isDarkTheme = isSystemInDarkTheme()
                // 使用统一的主题
                NotifyRelayTheme(darkTheme = isDarkTheme) {
                    val colorScheme = MiuixTheme.colorScheme
                    // 设置系统栏外观
                    SetupSystemBars(isDarkTheme)
                    // 根布局加 systemBarsPadding，避免内容被遮挡
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                    ) {
                        DeveloperModeScreen()
                    }
                }
            }
        }
    }

    @Composable
    fun DeveloperModeScreen() {
        val context = LocalContext.current

        // 日志级别状态
        val logLevel = remember {
            mutableStateOf(Logger.CURRENT_LEVEL)
        }

        // 日志级别选项，添加[e].[i]等蓝色文本
        val logLevelOptions = listOf(
            "关闭" to Logger.Level.NONE,
            "[E] 错误" to Logger.Level.ERROR,
            "[W] 警告" to Logger.Level.WARN,
            "[I] 信息" to Logger.Level.INFO,
            "[D] 调试" to Logger.Level.DEBUG,
            "[V] 详细" to Logger.Level.VERBOSE
        )

        // 当前选中的日志级别索引
        val selectedLevelIndex = remember {
            mutableStateOf(logLevelOptions.indexOfFirst { it.second == logLevel.value })
        }

        val textStyles = MiuixTheme.textStyles

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, start = 16.dp, end = 16.dp)
        ) {
            // 添加标题行
            top.yukonga.miuix.kmp.basic.Text(
                text = "开发者选项",
                style = textStyles.title1,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                var debugUiEnabled by DEBUG_UI_ENABLED

                SuperSwitch(
                    title = "调试UI显示",
                    summary = "显示调试信息、测试按钮等开发调试元素",
                    checked = debugUiEnabled,
                    onCheckedChange = {
                        debugUiEnabled = it
                        StorageManager.putBoolean(context, KEY_DEBUG_UI_ENABLED, it)
                    }
                )

                WindowDropdown(
                    title = "日志级别",
                    summary = "当前级别: ${logLevelOptions[selectedLevelIndex.value].first}",
                    items = logLevelOptions.map { it.first },
                    selectedIndex = selectedLevelIndex.value,
                    onSelectedIndexChange = {
                        selectedLevelIndex.value = it
                        logLevel.value = logLevelOptions[it].second
                        Logger.CURRENT_LEVEL = logLevel.value
                        StorageManager.putInt(context, KEY_LOG_LEVEL, logLevel.value.ordinal)
                    }
                )
            }
        }
    }
}