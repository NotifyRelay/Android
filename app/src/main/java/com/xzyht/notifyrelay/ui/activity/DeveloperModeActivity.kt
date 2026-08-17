package com.xzyht.notifyrelay.ui.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.common.NotifyRelayTheme
import com.xzyht.notifyrelay.ui.common.ProvideNavigationEventDispatcherOwner
import android.content.Intent
import com.xzyht.notifyrelay.ui.activity.GuideActivity
import com.xzyht.notifyrelay.ui.common.ScrollableTopAppBarPage
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

class DeveloperModeActivity : AppCompatActivity() {
    companion object {
        private const val KEY_LOG_LEVEL = "log_level"
        private const val KEY_DEBUG_UI_ENABLED = "debug_ui_enabled"
        private const val KEY_FILTERED_NOTIFICATION_LOG_ENABLED = "filtered_notification_log_enabled"

        val DEBUG_UI_ENABLED: MutableState<Boolean> = mutableStateOf(false)
        val FILTERED_NOTIFICATION_LOG_ENABLED: MutableState<Boolean> = mutableStateOf(true)

        fun initLogConfig(context: Context) {
            val logLevelOrdinal = StorageManager.getInt(context, KEY_LOG_LEVEL, Logger.Level.INFO.ordinal)
            Logger.currentLevel = Logger.Level.entries.getOrElse(logLevelOrdinal) { Logger.Level.INFO }
        }

        fun initDebugUiConfig(context: Context) {
            DEBUG_UI_ENABLED.value = StorageManager.getBoolean(context, KEY_DEBUG_UI_ENABLED, false)
            FILTERED_NOTIFICATION_LOG_ENABLED.value = StorageManager.getBoolean(context, KEY_FILTERED_NOTIFICATION_LOG_ENABLED, true)
            Logger.enableFilteredNotificationLog = FILTERED_NOTIFICATION_LOG_ENABLED.value
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先设置沉浸式虚拟键和状态栏
        androidx.core.view.WindowCompat
            .setDecorFitsSystemWindows(this.window, false)
        this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            ProvideNavigationEventDispatcherOwner {
                val isDarkTheme = isSystemInDarkTheme()
                // 使用统一的主题
                NotifyRelayTheme(darkTheme = isDarkTheme) {
                    // 设置系统栏外观
                    SetupSystemBars(isDarkTheme)
                    // 使用标准 TopAppBar 页面容器（内容区 colorScheme.background）
                    ScrollableTopAppBarPage(
                        title = "开发者选项",
                        onBack = { finish() },
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
        val logLevel =
            remember {
                mutableStateOf(Logger.currentLevel)
            }

        // 日志级别选项，添加[e].[i]等蓝色文本
        val logLevelOptions =
            listOf(
                "关闭" to Logger.Level.NONE,
                "[E] 错误" to Logger.Level.ERROR,
                "[W] 警告" to Logger.Level.WARN,
                "[I] 信息" to Logger.Level.INFO,
                "[D] 调试" to Logger.Level.DEBUG,
                "[V] 详细" to Logger.Level.VERBOSE,
            )

        // 当前选中的日志级别索引
        val selectedLevelIndex =
            remember {
                mutableIntStateOf(logLevelOptions.indexOfFirst { it.second == logLevel.value })
            }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                var debugUiEnabled by DEBUG_UI_ENABLED
                var filteredNotificationLogEnabled by FILTERED_NOTIFICATION_LOG_ENABLED

                SwitchPreference(
                    title = "浮窗/去重调试",
                    summary = "超级岛浮窗说明显示调试信息、智能去重输出调试日志",
                    checked = debugUiEnabled,
                    onCheckedChange = {
                        debugUiEnabled = it
                        StorageManager.putBoolean(context, KEY_DEBUG_UI_ENABLED, it)
                    },
                )

                WindowDropdownPreference(
                    title = "调试引导页",
                    summary = "选择分支进入引导页调试多分支显示",
                    entry = DropdownEntry(
                        items =
                            listOf(
                                DropdownItem(
                                    text = "完整流程",
                                    onClick = {
                                        val intent = IntentUtils.createIntent(context, GuideActivity::class.java)
                                        intent.putExtra("fromInternal", true)
                                        intent.putExtra("forceBranch", "full")
                                        IntentUtils.startActivity(context, intent, true)
                                    },
                                ),
                                DropdownItem(
                                    text = "重授权",
                                    onClick = {
                                        val intent = IntentUtils.createIntent(context, GuideActivity::class.java)
                                        intent.putExtra("fromInternal", true)
                                        intent.putExtra("forceBranch", "reauth")
                                        IntentUtils.startActivity(context, intent, true)
                                    },
                                ),
                                DropdownItem(
                                    text = "需重新同意",
                                    onClick = {
                                        val intent = IntentUtils.createIntent(context, GuideActivity::class.java)
                                        intent.putExtra("fromInternal", true)
                                        intent.putExtra("forceBranch", "consent")
                                        IntentUtils.startActivity(context, intent, true)
                                    },
                                ),
                            ),
                    ),
                )

                SwitchPreference(
                    title = "过滤通知日志",
                    summary = "显示被过滤通知的详细日志",
                    checked = filteredNotificationLogEnabled,
                    onCheckedChange = {
                        filteredNotificationLogEnabled = it
                        Logger.enableFilteredNotificationLog = it
                        StorageManager.putBoolean(context, KEY_FILTERED_NOTIFICATION_LOG_ENABLED, it)
                    },
                )

                WindowDropdownPreference(
                    title = "日志级别",
                    summary = "当前级别: ${logLevelOptions[selectedLevelIndex.intValue].first}",
                    items = logLevelOptions.map { it.first },
                    selectedIndex = selectedLevelIndex.intValue,
                    onSelectedIndexChange = {
                        selectedLevelIndex.intValue = it
                        logLevel.value = logLevelOptions[it].second
                        Logger.currentLevel = logLevel.value
                        StorageManager.putInt(context, KEY_LOG_LEVEL, logLevel.value.ordinal)
                    },
                )
            }
        }
    }
}
