package com.xzyht.notifyrelay.ui.devtools

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.ui.pages.history.NotificationCard
import kotlinx.coroutines.launch
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 长按单项通知时执行的行为（由页面下拉框选择）。
 *
 * @param label 下拉框显示文本，同时用于状态栏提示。
 * @param summary 下拉框摘要。
 */
private enum class LongPressAction(
    val label: String,
    val summary: String,
) {
    COPY("复制全部字段", "长按卡片：把该通知的全部字段写入剪贴板"),
    SHARE("分享 JSON", "长按卡片：导出 JSON 到缓存目录并拉起系统分享面板"),
}

/** 下拉框选项；顺序即 [LongPressAction.ordinal]，`selectedIndex` 可直接互转。 */
private val LONG_PRESS_ACTIONS = LongPressAction.entries.toList()

/**
 * 通知字段转储页（开发者选项 → 通知字段转储）。
 *
 * 临时调试页：进入时抓取**系统通知栏当前活跃通知**的全部字段，列表用通知历史页的卡片样式展示
 * （额外外显原始包名），**长按单项**执行当前选定的行为（复制字段 / 分享 JSON）。
 *
 * 数据契约（与需求一致）：
 * - **仅内存留存**：字段树只存在于 [NotificationFieldDumpViewModel] 的 StateFlow 中，
 *   页面/进程结束即丢失；不写数据库。
 * - **仅进入页面时载入一次**：不注册通知监听、不自动轮询；重复进入由 [LaunchedEffect] 触发一次刷新。
 * - **长按行为由下拉框决定**（复制 / 分享），两者**都受「包含二进制内容」开关控制**。
 * - **分享而非另存为**：导出文件集中写入 [NotificationDumpCache] 的外部缓存目录
 *   （文件管理器可定位），再拉起系统分享面板；目录内容由「清理导出缓存」按钮显式清理。
 */
@Composable
fun NotificationFieldDumpPage() {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val scope = rememberCoroutineScope()

    val viewModel: NotificationFieldDumpViewModel =
        viewModel(
            factory = NotificationFieldDumpViewModel.Factory(context.applicationContext as Application),
        )
    val uiState by viewModel.uiState.collectAsState()

    // 进入页面时载入一次当前活跃通知（仅内存）
    LaunchedEffect(Unit) {
        viewModel.loadActiveNotifications()
    }

    var includeBinary by remember { mutableStateOf(false) }
    var longPressActionIndex by remember { mutableStateOf(LONG_PRESS_ACTIONS.indexOf(LongPressAction.COPY)) }
    // 导出缓存占用（进入页面与每次清理后刷新）
    var cacheSummary by remember { mutableStateOf("") }

    suspend fun refreshCacheSummary() {
        val size = NotificationDumpCache.sizeBytes(context)
        val count = NotificationDumpCache.fileCount(context)
        cacheSummary = "$count 个文件 · ${NotificationDumpCache.formatSize(size)}"
    }

    LaunchedEffect(Unit) {
        refreshCacheSummary()
    }

    Surface(color = colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            WindowDropdownPreference(
                title = "长按行为",
                summary = LONG_PRESS_ACTIONS[longPressActionIndex].summary,
                items = LONG_PRESS_ACTIONS.map { it.label },
                selectedIndex = longPressActionIndex,
                onSelectedIndexChange = { longPressActionIndex = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            SwitchPreference(
                title = "包含二进制内容",
                summary =
                    if (includeBinary) {
                        "复制 / 分享均内联 Bitmap / Icon / byte[] 的 data URL（体积可能很大）"
                    } else {
                        "复制 / 分享均只保留二进制的类型与尺寸摘要"
                    },
                checked = includeBinary,
                onCheckedChange = { includeBinary = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            ArrowPreference(
                title = "清理导出缓存",
                summary = if (cacheSummary.isBlank()) "统计中…" else "当前占用：$cacheSummary",
                onClick = {
                    scope.launch {
                        ToastUtils.showShortToast(context, viewModel.clearExportCache(context))
                        refreshCacheSummary()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.loadActiveNotifications() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重新载入")
                }
            }

            Text(
                text = "当前活跃通知 ${uiState.entries.size} 条 · 长按单项${LONG_PRESS_ACTIONS[longPressActionIndex].label}",
                style = textStyles.body2.copy(color = colorScheme.onSurfaceSecondary),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = textStyles.body2.copy(color = colorScheme.error),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (uiState.entries.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // weight(1f) 而非 fillMaxSize()：父级是 Column，fillMaxSize 会按整列高度测量而溢出
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = uiState.entries.size,
                        key = { index -> uiState.entries[index].record.key },
                    ) { index ->
                        val entry = uiState.entries[index]
                        NotificationCard(
                            record = entry.record,
                            appIcon = entry.appIcon,
                            context = context,
                            getCachedAppInfo = viewModel::getCachedAppInfo,
                            cardColor = colorScheme.surface,
                            contentColor = colorScheme.onSurface,
                            installedPackages = uiState.installedPackages,
                            // 点击不启动应用（本页只做结构转储），长按执行选定行为
                            enableClick = false,
                            showPackageName = true,
                            onLongClick = {
                                when (LONG_PRESS_ACTIONS[longPressActionIndex]) {
                                    LongPressAction.COPY ->
                                        scope.launch {
                                            viewModel.copyDumpToClipboard(context, index, includeBinary)
                                        }
                                    LongPressAction.SHARE ->
                                        scope.launch {
                                            // 返回 null 表示分享面板已拉起，无需再提示
                                            viewModel.exportAndShare(context, index, includeBinary)?.let {
                                                ToastUtils.showShortToast(context, it)
                                            }
                                            refreshCacheSummary()
                                        }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
