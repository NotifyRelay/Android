package com.xzyht.notifyrelay.feature.notification.filter

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import com.xzyht.notifyrelay.feature.device.model.NotificationTextReader
import kotlinx.coroutines.runBlocking
import notifyrelay.base.log.Logger
import notifyrelay.data.filter.LocalFilterRepository
import notifyrelay.domain.feature.notification.filter.FilterEntry

/**
 * 本机通知过滤判定（Android 侧）。
 *
 * 条目与开关的读写位于公共端 [LocalFilterRepository]；本类只保留依赖通知对象的判定，
 * 以及为通知监听服务的同步回调提供的阻塞兜底。
 */
object BackendLocalFilter {
    /** 过滤本应用通知。 */
    var filterSelf: Boolean
        get() = LocalFilterRepository.filterSelf
        set(value) {
            LocalFilterRepository.filterSelf = value
        }

    /** 过滤持久化（常驻）通知。 */
    var filterOngoing: Boolean
        get() = LocalFilterRepository.filterOngoing
        set(value) {
            LocalFilterRepository.filterOngoing = value
        }

    /** 过滤无标题或无内容通知。 */
    var filterNoTitleOrText: Boolean
        get() = LocalFilterRepository.filterNoTitleOrText
        set(value) {
            LocalFilterRepository.filterNoTitleOrText = value
        }

    /** 过滤重要性为无的通知。 */
    var filterImportanceNone: Boolean
        get() = LocalFilterRepository.filterImportanceNone
        set(value) {
            LocalFilterRepository.filterImportanceNone = value
        }

    /** 内置关键词集合。 */
    fun getBuiltinKeywords(): Set<String> = LocalFilterRepository.getBuiltinKeywords()

    /** 默认包名过滤集合。 */
    fun getDefaultPackageFilters(): Set<String> = LocalFilterRepository.getDefaultPackageFilters()

    /** 全部过滤条目。 */
    suspend fun getFilterEntries(context: Context): Set<FilterEntry> = LocalFilterRepository.getFilterEntries()

    /** 已启用的过滤条目。 */
    suspend fun getEnabledFilterEntries(context: Context): Set<FilterEntry> = LocalFilterRepository.getEnabledFilterEntries()

    /** 设置条目启用状态。 */
    suspend fun setFilterEntryEnabled(
        context: Context,
        entry: FilterEntry,
        enabled: Boolean,
    ) = LocalFilterRepository.setFilterEntryEnabled(entry, enabled)

    /** 新增条目。 */
    suspend fun addFilterEntry(
        context: Context,
        keyword: String,
        packageName: String,
    ) = LocalFilterRepository.addFilterEntry(keyword, packageName)

    /** 删除条目。 */
    suspend fun removeFilterEntry(
        context: Context,
        keyword: String,
        packageName: String,
    ) = LocalFilterRepository.removeFilterEntry(keyword, packageName)

    /**
     * 判断本机通知是否应该被转发（suspend 版本，热路径仅一次查询）。
     *
     * @param isFromPeriodicCheck 是否来自定时检查，避免调试日志刷屏
     */
    suspend fun shouldForward(
        sbn: StatusBarNotification,
        context: Context,
        isFromPeriodicCheck: Boolean = false,
    ): Boolean {
        if (LocalFilterRepository.filterSelf && sbn.packageName == context.packageName) return false

        // 单次查询：基于同一份 rows 派生 enabled 与 all，避免热路径上重复查询
        val rows = LocalFilterRepository.getEnabledFilterEntries()
        val enabledEntries = rows
        val enabledPackageFilters = enabledEntries.filter { it.keyword.isBlank() && it.packageName.isNotBlank() }.map { it.packageName }.toSet()
        if (enabledPackageFilters.contains(sbn.packageName)) return false

        val flags = sbn.notification.flags
        val title = NotificationTextReader.getStringCompat(sbn.notification.extras, "android.title") ?: ""
        // 使用 getNotificationTextWithVerifyCode 读取文本，优先读取 verify_code 字段
        val text = NotificationTextReader.getNotificationTextWithVerifyCode(sbn) ?: ""
        if (!isFromPeriodicCheck) {
            val titlePreview = if (title.length > 10) title.take(10) + "..." else title
            Logger.v("NotifyRelay-Filter", "shouldForward: packageName='" + sbn.packageName + "', titlePreview='" + titlePreview + "'")
        }

        // 持久化/前台服务过滤，包含服务相关关键词
        if (LocalFilterRepository.filterOngoing) {
            val isOngoing = sbn.isOngoing || (flags and Notification.FLAG_ONGOING_EVENT) != 0 || (flags and 0x00000200) != 0
            if (isOngoing) return false
        }

        // 使用统一的启用过滤条目进行匹配：
        // - entry.keyword 非空 且 packageName 为空 -> 只匹配文本（任意应用）
        // - entry.keyword 为空 且 packageName 非空 -> 只匹配包名
        // - 两者均非空 -> 同时匹配才命中
        // 关键字按空格分词后可跨标题/内容匹配：keyword = "米家 手表" 时，
        // title 包含 "米家" 且 text 包含 "手表" 也视为命中。
        fun keywordMatchesAcrossFields(
            keyword: String,
            title: String,
            text: String,
        ): Boolean {
            val kw = keyword.trim()
            if (kw.isEmpty()) return false
            if (!kw.contains(' ')) return title.contains(kw, true) || text.contains(kw, true)

            val tokens = kw.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return false
            for (token in tokens) {
                if (!title.contains(token, true) && !text.contains(token, true)) return false
            }
            return true
        }

        for (entry in enabledEntries) {
            val kw = entry.keyword.trim()
            val pkg = entry.packageName.trim()

            val kwMatches = kw.isNotEmpty() && keywordMatchesAcrossFields(kw, title, text)
            val pkgMatches = pkg.isNotEmpty() && sbn.packageName == pkg

            // 仅关键字
            if (kw.isNotEmpty() && pkg.isEmpty() && kwMatches) return false
            // 仅包名
            if (kw.isEmpty() && pkg.isNotEmpty() && pkgMatches) return false
            // 同时存在 -> 要同时匹配
            if (kw.isNotEmpty() && pkg.isNotEmpty() && kwMatches && pkgMatches) return false
        }

        if (LocalFilterRepository.filterImportanceNone) {
            val channelId = sbn.notification.channelId
            if (channelId != null) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = nm.getNotificationChannel(channelId)
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
            }
        }

        if (LocalFilterRepository.filterNoTitleOrText) {
            if (title.isBlank() || text.isBlank()) return false
        }

        return true
    }

    /**
     * 同步兜底版本：供 NotificationListenerService 的非挂起回调使用。
     *
     * 仅在无法改造为协程的同步入口使用，新代码应优先调用 suspend 版本。
     */
    fun shouldForwardBlocking(
        sbn: StatusBarNotification,
        context: Context,
        isFromPeriodicCheck: Boolean = false,
    ): Boolean = runBlocking { shouldForward(sbn, context, isFromPeriodicCheck) }
}
