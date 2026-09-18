package com.xzyht.notifyrelay.feature.notification.filter

import android.content.Context
import com.sun.jna.Pointer
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import com.xzyht.notifyrelay.ui.activity.DeveloperModeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import notifyrelay.base.util.Logger
import org.json.JSONObject

/**
 * 后端接收通知过滤器
 * 处理从远程设备接收的通知的过滤逻辑
 * 过滤决策委托给 Rust NativeCore
 *
 * 拆分后本 object 仅作为外部调用门面（facade）：
 * - 去重缓存 → [RemoteFilterDedupCache]
 * - 延迟复刻占位队列 → [RemoteFilterPlaceholderQueue]
 * - 待撤回监控队列与超时协程 → [RemoteFilterPendingMonitor]
 * 内部数据类与逻辑一字未改，仅搬迁至对应文件。
 */
object BackendRemoteFilter {
    /** Rust 上下文指针，由 DeviceConnectionManager 创建时设置 */
    var rustContext: Pointer? = null

    // 结构化协程作用域，替代 GlobalScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 延迟去重缓存（10秒内）- 用于智能去重机制
    private val dedupCache = RemoteFilterDedupCache

    // 延迟复刻占位队列（用于锁屏延迟复刻的占位，15s 可被本机入队取消）
    private val placeholders = RemoteFilterPlaceholderQueue()

    // 待监控的通知撤回队列 + 超时监控协程
    private val pendingMonitor =
        RemoteFilterPendingMonitor(
            scope = scope,
            onDedupCache = { title, text -> dedupCache.add(title, text) },
            onClearExpiredPlaceholders = { placeholders.clearExpired() },
        )

    /**
     * 清空待处理列表（供公平运行内存回调使用）。
     */
    fun clearPending() {
        dedupCache.clear()
        pendingMonitor.clear()
        placeholders.clear()
    }

    /**
     * 远程通知过滤结果
     */
    data class FilterResult(
        val shouldShow: Boolean,
        val mappedPkg: String,
        val title: String,
        val text: String,
        val rawData: String,
        val needsDelay: Boolean = false, // 是否需要延迟验证（先发送后监控）
    )

    /**
     * 过滤远程通知
     * 包含包名映射（Rust）、智能去重、黑白名单/对等模式（Rust）
     *
     * 同步契约：被消息处理链（NotificationProcessor.process，非 suspend）调用，
     * 无法直接挂起。配置加载通过 loadBlocking 兜底（仅首次加载，后续 isLoaded 短路）。
     */
    fun filterRemoteNotification(
        data: String,
        context: Context,
    ): FilterResult {
        // 确保配置已加载（只加载一次）
        val configResult = ensureConfigLoaded(context, data)
        if (configResult != null) return configResult

        try {
            val json = JSONObject(data)
            val pkg = json.optString("packageName")
            val title = json.optString("title")
            val text = json.optString("text")
            val isLocked = json.optBoolean("isLocked", false)

            val installedPkgs = AppRepository.getInstalledPackageNamesSync(context)
            val mappedPkg = RemoteFilterConfig.mapToLocalPackage(pkg, installedPkgs)

            // 对等/黑白名单/锁屏(1) 过滤
            val modeResult = passesModeFilters(mappedPkg, title, text, installedPkgs, isLocked, data)
            if (modeResult != null) return modeResult

            // 智能去重检查 - 优化性能和逻辑
            if (RemoteFilterConfig.enableDeduplication) {
                val dedupResult = runDeduplication(title, text, pkg, mappedPkg, installedPkgs, isLocked, data)
                if (dedupResult != null) return dedupResult
            }

            // 锁屏通知过滤（2）
            if (RemoteFilterConfig.enableLockScreenOnly && !isLocked) {
                // Logger.d("NotifyRelay(狂鼠)", "filterRemoteNotification: 锁屏过滤 - 非锁屏通知被过滤")
                return FilterResult(false, mappedPkg, title, text, data)
            }

            // Logger.d("NotifyRelay(狂鼠)", "filterRemoteNotification: 直接通过 - mappedPkg=$mappedPkg title=$title text=$text")
            return FilterResult(true, mappedPkg, title, text, data)
        } catch (e: Exception) {
            Logger.e("NotifyRelay(狂鼠)", "filterRemoteNotification: 解析异常 - data=$data", e)
            return FilterResult(true, "", "", "", data)
        }
    }

    /**
     * 确保配置已加载（只加载一次）。返回非 null 表示加载失败需提前返回的结果，null 表示继续。
     */
    private fun ensureConfigLoaded(
        context: Context,
        data: String,
    ): FilterResult? {
        synchronized(RemoteFilterConfig) {
            if (!RemoteFilterConfig.isLoaded) {
                try {
                    RemoteFilterConfig.loadBlocking(context)
                    RemoteFilterConfig.isLoaded = true
                    rustContext?.let { ctx ->
                        val installedPkgs = AppRepository.getInstalledPackageNamesSync(context)
                        RemoteFilterConfig.syncToRust(ctx, installedPkgs)
                    }
                } catch (e: Exception) {
                    Logger.e("NotifyRelay(狂鼠)", "远程过滤配置加载失败", e)
                    return FilterResult(true, "", "", "", data)
                }
            }
        }
        return null
    }

    /**
     * 对等模式 / 黑白名单 / 锁屏(1) 过滤。
     * 返回非 null 表示命中过滤需提前返回的结果，null 表示继续（进入去重与锁屏(2)）。
     */
    private fun passesModeFilters(
        mappedPkg: String,
        title: String,
        text: String,
        installedPkgs: Set<String>,
        isLocked: Boolean,
        data: String,
    ): FilterResult? {
        // 对等模式过滤（保持 Kotlin 实现）
        if (RemoteFilterConfig.enablePeerMode) {
            if (mappedPkg !in installedPkgs) {
                return FilterResult(false, mappedPkg, title, text, data)
            }
        }

        // 黑白名单过滤 — 委托给 Rust Core（含关键词匹配）
        val filterMode = RemoteFilterConfig.filterMode
        if (filterMode == "black" || filterMode == "white") {
            val pass = RemoteFilterConfig.checkFilterWithRust(mappedPkg, title, text)
            if (!pass) {
                return FilterResult(false, mappedPkg, title, text, data)
            }
        }

        // 锁屏通知过滤
        if (RemoteFilterConfig.enableLockScreenOnly && !isLocked) {
            // Logger.d("NotifyRelay(狂鼠)", "filterRemoteNotification: 锁屏过滤开启 - 非锁屏通知被过滤")
            return FilterResult(false, mappedPkg, title, text, data)
        }

        return null
    }

    /**
     * 智能去重检查（先发送后监控机制）。
     * 返回非 null 表示已作出去重决策需提前返回的结果，null 表示跳过去重（继续走锁屏(2)/通过）。
     *
     * 契约（与 DeviceConnectionManager 的隐式约定，语义不可改）：
     *  - 命中缓存/内存重复 → shouldShow=false
     *  - 本机锁屏且无重复 → shouldShow=false, needsDelay=false（避免先发再撤回）
     *  - 无重复 → shouldShow=true, needsDelay=true（先发送后由监控撤回）
     */
    private fun runDeduplication(
        title: String,
        text: String,
        pkg: String,
        mappedPkg: String,
        installedPkgs: Set<String>,
        isLocked: Boolean,
        data: String,
    ): FilterResult? {
        // 性能优化：仅在满足以下情况时跳过去重：
        //  - 开启了包名等价组映射
        //  - 远端包名不属于任何等价组
        //  - 且映射到的本地包未安装
        // 对于本机已安装映射包（包括 mappedPkg == pkg 的同包名场景）仍然执行去重。
        val pkgInGroups =
            if (RemoteFilterConfig.enablePackageGroupMapping) {
                RemoteFilterConfig.packageGroups.any { pkg in it }
            } else {
                true
            }

        val shouldSkipDedup = RemoteFilterConfig.enablePackageGroupMapping && !pkgInGroups && (mappedPkg !in installedPkgs)

        if (shouldSkipDedup) {
            // Logger.d("智能去重", "跳过去重：包名不属于等价组且本机未安装映射包，包名=$pkg, mappedPkg=$mappedPkg")
            // 跳过去重，继续走后续流程（如锁屏过滤和最终通过）
            return null
        }

        // 1. 快速缓存检查（10秒内）
        if (dedupCache.containsRecent(title, text)) {
            // 撤回匹配的待监控通知（按原始标题/文本匹配，与缓存写入口径一致）。
            // 保持基线语义：命中缓存只撤回，不刷新去重窗口。
            pendingMonitor.cancelMatchingRaw(title, text)
            // Logger.d("智能去重", "命中10秒缓存并撤回之前的通知 - 包名:$pkg, 标题:$title, 内容:$text")
            return FilterResult(false, mappedPkg, title, text, data)
        }

        // 2. 历史重复检查优化
        try {
            // 获取内存历史数据
            val localList =
                NotificationRepository
                    .getNotificationsByDevice("本机")
            val memoryDup = checkDuplicateInMemory(localList, title, text)

            // 如果内存中有重复，直接过滤
            if (memoryDup) {
                // Logger.d("智能去重", "命中内存历史重复")
                return FilterResult(false, mappedPkg, title, text, data)
            }

            // 内存无重复，默认情况下标记为需要延迟验证（先发送后监控机制）。
            // 但如果该远端通知接受到时本机锁屏，则避免先发送再撤回，改为不立即展示，
            // 由上层在超期后再次检查并决定是否复刻（见 DeviceConnectionManager 的处理）。
            if (isLocked) {
                // Logger.d("NotifyRelay(狂鼠)", "本机锁屏：内存无重复，改为不立即展示，等待超期后再复刻")
                return FilterResult(false, mappedPkg, title, text, data, needsDelay = false)
            }

            // Logger.d("NotifyRelay(狂鼠)", "无历史重复，标记延迟验证")
            return FilterResult(true, mappedPkg, title, text, data, needsDelay = true)
        } catch (_: Exception) {
            // Logger.e("智能去重", "历史检查异常", e)
            // 异常情况下默认延迟验证
            return FilterResult(true, mappedPkg, title, text, data, needsDelay = true)
        }
    }

    /**
     * 检查内存中的重复通知 — 使用 Rust shouldDeduplicate 比较文本相似度
     */
    private fun checkDuplicateInMemory(
        localList: List<NotificationRecord>,
        title: String,
        text: String,
    ): Boolean {
        var hasDuplicate = false

        for (notification in localList) {
            try {
                if (notification.device != "本机") continue
                val oldTitle = normalizeTitle(notification.title ?: "")
                val oldText = notification.text ?: ""
                val newTitle = normalizeTitle(title)
                if (NativeCore.shouldDeduplicate(newTitle, text, oldTitle, oldText)) {
                    hasDuplicate = true
                }
            } catch (e: Exception) {
                Logger.e("智能去重", "内存检查异常", e)
            }
        }

        return hasDuplicate
    }

    /**
     * 添加到去重缓存
     */
    fun addToDedupCache(
        title: String,
        text: String,
    ) {
        dedupCache.add(title, text)
    }

    /**
     * 添加占位（用于锁屏延迟复刻场景）。
     */
    fun addPlaceholder(
        title: String,
        text: String,
        packageName: String,
        ttl: Long = 15_000L,
    ) {
        if (!RemoteFilterConfig.enableDeduplication) return
        placeholders.add(title, text, packageName, ttl)
    }

    /**
     * 移除匹配的占位（通常由本机入队触发），返回是否有移除项。
     * 保持基线语义：只移除占位、不写去重缓存（写缓存仅在 [onLocalNotificationEnqueued] 路径）。
     */
    fun removePlaceholderMatching(
        title: String?,
        text: String?,
        packageName: String,
    ): Boolean = placeholders.removeMatching(title, text, packageName)

    /**
     * 检查占位是否仍然存在（并清理过期项）
     */
    fun isPlaceholderPresent(
        title: String?,
        text: String?,
        packageName: String,
    ): Boolean = placeholders.isPresent(title, text, packageName)

    /**
     * 被动匹配：当本机通知入队（已完成本地过滤并写入历史/内存）时调用。
     * 如果与待撤回队列命中，则立即撤回对应通知并移除待监控项，进入被动撤回模式，减少轮询与IO。
     */
    @Suppress("UNUSED_PARAMETER")
    fun onLocalNotificationEnqueued(
        title: String?,
        text: String?,
        packageName: String,
        time: Long,
        context: Context,
    ) {
        if (!RemoteFilterConfig.enableDeduplication) return
        val normalizedPendingTitle = normalizeTitle(title ?: "")
        val pendingText = text ?: ""
        // 先处理占位匹配（用于延迟复刻的占位）——在单独的锁上操作以避免并发问题
        placeholders.removeMatching(title, text, packageName) { ph ->
            // 命中占位（阻止延迟复刻）：将其写入去重缓存
            dedupCache.add(ph.title, ph.text)
        }

        // 再处理已发送但在可撤回期的通知
        val matches = pendingMonitor.cancelMatching(normalizedPendingTitle, pendingText, packageName)
        if (matches.isNotEmpty() && DeveloperModeActivity.DEBUG_UI_ENABLED.value) {
            val titlePreview = if ((title?.length ?: 0) > 10) "${title?.take(10)}..." else (title ?: "")
            Logger.d("智能去重", "被动命中待撤回通知 - 包名:$packageName, 标题预览:$titlePreview, 匹配数量:${matches.size}")
        }
        // 对于命中的通知，也可以把本地这条记录记入去重缓存，避免短时间内再次复刻
        matches.forEach { dedupCache.add(it.title, it.text) }
    }

    /**
     * 添加待监控的通知
     */
    fun addPendingNotification(
        notifyId: Int,
        title: String,
        text: String,
        packageName: String,
        context: Context,
    ) {
        // 只有在去重开关开启时才添加监控
        if (!RemoteFilterConfig.enableDeduplication) {
            return
        }
        pendingMonitor.addPendingNotification(notifyId, title, text, packageName, context)
    }
}
