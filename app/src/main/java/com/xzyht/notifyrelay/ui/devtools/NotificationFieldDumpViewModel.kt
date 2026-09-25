package com.xzyht.notifyrelay.ui.devtools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.device.model.NotificationTextReader
import com.xzyht.notifyrelay.feature.notification.service.NotifyRelayNotificationListenerService
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.ClipboardUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils

/**
 * 通知字段转储页的内存状态（**不持久化**）。
 *
 * @param record 供通知历史页卡片展示的记录。
 * @param sbn 原始系统通知引用；字段树在复制 / 分享时按需反射构建。
 * @param ranking 系统侧排序/渠道信息（决定是否渲染），可为 null。
 * @param appIcon 应用图标（可能为 null）。
 */
internal data class NotificationDumpEntry(
    val record: NotificationRecord,
    val sbn: StatusBarNotification,
    val ranking: NotificationListenerService.Ranking?,
    val appIcon: Bitmap?,
)

internal data class NotificationDumpUiState(
    val entries: List<NotificationDumpEntry> = emptyList(),
    val installedPackages: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * 通知字段转储页 ViewModel。
 *
 * **数据仅内存留存**：状态只放在 [uiState] 中，进程结束即丢失；不写数据库、不写文件
 * （下载模式由用户通过系统文件选择器显式指定保存位置）。
 */
internal class NotificationFieldDumpViewModel(
    private val application: Application,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationDumpUiState())
    val uiState: StateFlow<NotificationDumpUiState> = _uiState.asStateFlow()

    /** 包名 → (应用名, 图标)；仅内存，随本次载入重建。 */
    private val iconCache = mutableMapOf<String, Pair<String, Bitmap?>>()

    /**
     * 载入当前通知栏的活跃通知（仅内存）。
     *
     * 数据源是通知监听服务的 `activeNotifications`；服务未连接时给出错误提示而不是静默空列表。
     */
    fun loadActiveNotifications() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val listener = NotifyRelayNotificationListenerService.instance
                if (listener == null) {
                    iconCache.clear()
                    _uiState.update {
                        it.copy(
                            entries = emptyList(),
                            installedPackages = emptySet(),
                            isLoading = false,
                            error = "通知监听服务未连接，无法读取活跃通知",
                        )
                    }
                    return@launch
                }
                val actives = listener.activeNotifications ?: emptyArray()
                val installed = AppRepository.getInstalledPackageNamesSync(application)
                iconCache.clear()
                // 系统侧排序/渠道信息（importance / rank / suppressedVisualEffects / channel 等）
                // 不在 sbn 与 notification 里，只能经 RankingMap 按 key 取；这些字段决定是否渲染
                val rankingMap = listener.currentRanking
                val entries =
                    actives.map { sbn ->
                        NotificationDumpEntry(
                            record = buildRecord(sbn),
                            sbn = sbn,
                            ranking = resolveRanking(rankingMap, sbn),
                            appIcon = loadAppIcon(sbn.packageName),
                        )
                    }
                _uiState.update {
                    it.copy(
                        entries = entries,
                        installedPackages = installed,
                        isLoading = false,
                        error = if (actives.isEmpty()) "当前没有活跃通知" else null,
                    )
                }
                Logger.i(TAG, "载入活跃通知 ${entries.size} 条")
            } catch (e: Exception) {
                Logger.e(TAG, "载入活跃通知失败", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "载入失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 把第 [index] 条通知的完整字段复制到剪贴板。
     *
     * @param includeBinary 是否内联二进制内容（Bitmap / Icon / byte[]）。
     */
    fun copyDumpToClipboard(
        context: Context,
        index: Int,
        includeBinary: Boolean,
    ) {
        viewModelScope.launch {
            val entry = _uiState.value.entries.getOrNull(index) ?: return@launch
            val (text, truncated) =
                withContext(Dispatchers.IO) {
                    val json = NotificationFieldDump.buildDumpJson(application, entry.sbn, includeBinary, entry.ranking)
                    NotificationFieldDump.renderDumpText(json) to json.optBoolean("truncated", false)
                }
            if (text.isBlank()) {
                Logger.w(TAG, "该通知无可复制内容: pkg=${entry.record.packageName}")
                ToastUtils.showShortToast(context, "该通知无可复制内容")
                return@launch
            }
            if (ClipboardUtils.copyText(context, "notification_field_dump", text)) {
                if (truncated) {
                    Logger.w(TAG, "已复制但内容被截断: pkg=${entry.record.packageName}")
                    ToastUtils.showLongToast(context, "已复制（内容被截断，详见开头警告）")
                } else {
                    ToastUtils.showShortToast(context, "已复制通知全部字段到剪贴板")
                }
            } else {
                Logger.w(TAG, "复制通知字段失败（剪贴板写入返回 false）: pkg=${entry.record.packageName}")
                ToastUtils.showShortToast(context, "复制失败")
            }
        }
    }

    /**
     * 把第 [index] 条通知的完整字段导出到**集中缓存目录**并拉起系统分享面板。
     *
     * 不提供「另存为」：文件先落到 [NotificationDumpCache] 的固定目录（外部缓存，
     * 文件管理器可定位），再由用户在分享面板里决定目标。
     *
     * @return 面向用户的提示文案；返回 `null` 表示分享面板已成功拉起（无需 Toast）。
     */
    suspend fun exportAndShare(
        context: Context,
        index: Int,
        includeBinary: Boolean,
    ): String? {
        val entry = _uiState.value.entries.getOrNull(index) ?: return "未找到该通知"
        return try {
            val (payload, truncated) =
                withContext(Dispatchers.IO) {
                    val json = NotificationFieldDump.buildDumpJson(application, entry.sbn, includeBinary, entry.ranking)
                    json.toString(2) to json.optBoolean("truncated", false)
                }
            val fileName = "notification_dump_${entry.record.packageName.replace('.', '_')}.json"
            val file = NotificationDumpCache.write(application, fileName, payload)
            val shareIntent =
                withContext(Dispatchers.Main) {
                    NotificationDumpCache.buildShareIntent(context, file)
                }
            if (shareIntent == null) {
                Logger.w(TAG, "无法构造分享 Intent，文件已导出: ${file.absolutePath}")
                "已导出到 ${NotificationDumpCache.DIR_NAME}/，但无法拉起分享"
            } else {
                withContext(Dispatchers.Main) {
                    val chooser =
                        Intent.createChooser(shareIntent, "分享通知字段 JSON").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    context.startActivity(chooser)
                }
                // 截断信息已在 JSON 的 truncated / truncationSummary / truncations 中声明
                if (truncated) "已导出（内容被截断，见 JSON 的 truncations）" else null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "导出并分享通知字段失败", e)
            "导出失败: ${e.message}"
        }
    }

    /** 清理导出缓存目录；返回清理后的提示文案。 */
    suspend fun clearExportCache(context: Context): String {
        val before = NotificationDumpCache.fileCount(context)
        val ok = NotificationDumpCache.clearAll(context)
        return when {
            before == 0 -> "导出缓存已是空的"
            ok -> "已清理 $before 个导出文件"
            else -> "部分导出文件清理失败，详见日志"
        }
    }

    /** 供 [com.xzyht.notifyrelay.ui.pages.history.NotificationCard] 读取「包名 → (应用名, 图标)」。 */
    fun getCachedAppInfo(packageName: String?): Pair<String, Bitmap?> {
        if (packageName.isNullOrBlank()) return "" to null
        return iconCache[packageName] ?: (packageName to null)
    }

    /** 从 [NotificationListenerService.RankingMap] 按 sbn.key 取出该通知的系统排序信息。 */
    private fun resolveRanking(
        rankingMap: NotificationListenerService.RankingMap?,
        sbn: StatusBarNotification,
    ): NotificationListenerService.Ranking? {
        if (rankingMap == null) return null
        val key = sbn.key ?: return null
        return try {
            val ranking = NotificationListenerService.Ranking()
            if (rankingMap.getRanking(key, ranking)) ranking else null
        } catch (e: Exception) {
            Logger.w(TAG, "读取 Ranking 失败: key=$key, ${e.message}")
            null
        }
    }

    private fun buildRecord(sbn: StatusBarNotification): NotificationRecord =
        NotificationRecord(
            key = sbn.key ?: (sbn.id.toString() + sbn.packageName),
            packageName = sbn.packageName,
            appName = getAppLabel(sbn.packageName),
            title = NotificationTextReader.getStringCompat(sbn.notification.extras, android.app.Notification.EXTRA_TITLE),
            text = NotificationTextReader.getNotificationTextWithVerifyCode(sbn),
            time = sbn.postTime,
            device = "本机",
        )

    private suspend fun loadAppIcon(packageName: String): Bitmap? =
        try {
            val icon = AppRepository.getAppIconAsync(application, packageName)
            iconCache[packageName] = getAppLabel(packageName) to icon
            icon
        } catch (e: Exception) {
            Logger.w(TAG, "读取应用图标失败: $packageName", e)
            null
        }

    private fun getAppLabel(packageName: String): String =
        try {
            val pm = application.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NotificationFieldDumpViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NotificationFieldDumpViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private companion object {
        const val TAG = "NotificationFieldDump"
    }
}
