package notifyrelay.shared.ui.screen

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
import androidx.compose.ui.unit.dp
import notifyrelay.base.log.Logger
import notifyrelay.shared.ui.common.DebugFlags
import notifyrelay.shared.ui.port.LocalPreferencePort
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

/** 日志级别偏好键。 */
private const val KEY_LOG_LEVEL = "log_level"

/** 日志级别可选项（显示名与对应级别）。 */
private val LOG_LEVEL_OPTIONS =
    listOf(
        "关闭" to Logger.Level.NONE,
        "[E] 错误" to Logger.Level.ERROR,
        "[W] 警告" to Logger.Level.WARN,
        "[I] 信息" to Logger.Level.INFO,
        "[D] 调试" to Logger.Level.DEBUG,
        "[V] 详细" to Logger.Level.VERBOSE,
    )

/**
 * 开发者选项页。
 *
 * 平台专属入口（引导页分支、通知字段转储）经槽位注入：
 * [onOpenGuideBranch] 为 null 时不显示「调试引导页」，[dumpPageContent] 为 null 时不显示转储入口。
 */
@Composable
fun DeveloperModeScreen(
    onOpenGuideBranch: ((String) -> Unit)? = null,
    onOpenDumpPage: (() -> Unit)? = null,
) {
    val preferencePort = LocalPreferencePort.current

    val logLevel = remember { mutableStateOf(Logger.currentLevel) }
    val selectedLevelIndex =
        remember {
            mutableIntStateOf(LOG_LEVEL_OPTIONS.indexOfFirst { it.second == logLevel.value }.coerceAtLeast(0))
        }

    var debugUiEnabled by DebugFlags.DEBUG_UI_ENABLED
    var filteredNotificationLogEnabled by DebugFlags.FILTERED_NOTIFICATION_LOG_ENABLED

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp),
    ) {
        SwitchPreference(
            title = "浮窗/去重调试",
            summary = "超级岛浮窗说明显示调试信息、智能去重输出调试日志",
            checked = debugUiEnabled,
            onCheckedChange = { enabled ->
                DebugFlags.setDebugUiEnabled(enabled) { key, value -> preferencePort?.putBoolean(key, value) }
            },
        )

        if (onOpenGuideBranch != null) {
            WindowDropdownPreference(
                title = "调试引导页",
                summary = "选择分支进入引导页调试多分支显示",
                entry =
                    DropdownEntry(
                        items =
                            listOf(
                                DropdownItem(text = "完整流程", onClick = { onOpenGuideBranch("full") }),
                                DropdownItem(text = "重授权", onClick = { onOpenGuideBranch("reauth") }),
                                DropdownItem(text = "需重新同意", onClick = { onOpenGuideBranch("consent") }),
                            ),
                    ),
            )
        }

        SwitchPreference(
            title = "过滤通知日志",
            summary = "显示被过滤通知的详细日志",
            checked = filteredNotificationLogEnabled,
            onCheckedChange = { enabled ->
                DebugFlags.setFilteredNotificationLogEnabled(enabled) { key, value -> preferencePort?.putBoolean(key, value) }
            },
        )

        WindowDropdownPreference(
            title = "日志级别",
            summary = "当前级别: " + LOG_LEVEL_OPTIONS[selectedLevelIndex.intValue].first,
            items = LOG_LEVEL_OPTIONS.map { it.first },
            selectedIndex = selectedLevelIndex.intValue,
            onSelectedIndexChange = { index ->
                if (index !in LOG_LEVEL_OPTIONS.indices) return@WindowDropdownPreference
                selectedLevelIndex.intValue = index
                logLevel.value = LOG_LEVEL_OPTIONS[index].second
                Logger.currentLevel = logLevel.value
                preferencePort?.putInt(KEY_LOG_LEVEL, logLevel.value.ordinal)
            },
        )

        if (onOpenDumpPage != null) {
            ArrowPreference(
                title = "通知字段转储",
                summary = "抓取当前通知栏活跃通知的全部字段（仅内存留存），长按单项复制",
                onClick = onOpenDumpPage,
            )
        }
    }
}

/** 从偏好装载日志级别；平台启动时调用。 */
fun loadLogLevel(read: (String, Int) -> Int) {
    val ordinal = read(KEY_LOG_LEVEL, Logger.Level.INFO.ordinal)
    Logger.currentLevel = Logger.Level.entries.getOrElse(ordinal) { Logger.Level.INFO }
}

/** 开发者模式开关的共享状态类型别名，供平台伴生对象转发。 */
typealias DebugUiEnabledState = MutableState<Boolean>
