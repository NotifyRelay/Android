package com.xzyht.notifyrelay.feature.notification.filter

import android.content.Context
import com.xzyht.notifyrelay.feature.appslist.InstalledAppsRepository
import com.xzyht.notifyrelay.nativecore.NativeCore
import com.xzyht.notifyrelay.nativecore.device.appsync.InboundNotificationProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import notifyrelay.base.log.Logger
import notifyrelay.base.util.TextUtils
import notifyrelay.domain.feature.notification.filter.RemoteFilterPlaceholderQueue
import notifyrelay.shared.ui.common.DebugFlags
import org.json.JSONObject

/**
 * 后端接收通知过滤器
 * 处理从远程设备接收的通知的过滤逻辑
 * 过滤决策委托给 Rust NativeCore
 *
 * 拆分后本 object 仅作为外部调用门面（facade）：
 * - 延迟复刻占位队列 → [RemoteFilterPlaceholderQueue]
 * - 待撤回监控队列与超时协程 → [RemoteFilterPendingMonitor]
 * 内部数据类与逻辑一字未改，仅搬迁至对应文件。
 *
 * 「已入库检定」不在本类：唯一判定处为 [InboundNotificationProcessor.isDuplicate]，
 * 由 [com.xzyht.notifyrelay.sync.notification.NotificationProcessor] 在入口调用。
 */
object BackendRemoteFilter {
    // 结构化协程作用域，替代 GlobalScope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 延迟复刻占位队列（用于锁屏延迟复刻的占位，15s 可被本机入队取消）
    private val placeholders = RemoteFilterPlaceholderQueue()

    // 待监控的通知撤回队列 + 超时监控协程
    private val pendingMonitor =
        RemoteFilterPendingMonitor(
            scope = scope,
            onDedupCache = { title, text -> onTimeoutSeen(title, text) },
            onClearExpiredPlaceholders = { placeholders.clearExpired() },
        )

    /**
     * 清空待处理列表（供公平运行内存回调使用）。
     */
    fun clearPending() {
        InboundNotificationProcessor.clearContentWindow()
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
     * 包含包名映射（Rust）、黑白名单/对等模式/锁屏过滤
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

            val installedPkgs = InstalledAppsRepository.getInstalledPackageNamesSync(context)
            val mappedPkg = port().mapToLocalPackage(pkg, installedPkgs)

            // 对等/黑白名单/锁屏过滤
            val modeResult = passesModeFilters(mappedPkg, title, text, installedPkgs, isLocked, data)
            if (modeResult != null) return modeResult

            // 本机锁屏时不立即展示，由上层在超期后再次检查并决定是否复刻
            if (port().enableDedup && isLocked) {
                return FilterResult(false, mappedPkg, title, text, data, needsDelay = false)
            }

            // 无重复时标记为延迟验证：先发送，由监控在 15 秒内按本机入队情况撤回
            return FilterResult(true, mappedPkg, title, text, data, needsDelay = true)
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
        val filterPort = port()
        synchronized(filterPort) {
            if (!filterPort.isLoaded) {
                try {
                    runBlocking { filterPort.load() }
                    filterPort.isLoaded = true
                    if (NativeCore.isReady()) {
                        val installedPkgs = InstalledAppsRepository.getInstalledPackageNamesSync(context)
                        filterPort.let { (it as? AndroidRemoteFilterPort)?.syncToRust(installedPkgs) }
                    }
                } catch (e: Exception) {
                    Logger.e("NotifyRelay(狂鼠)", "远程过滤配置加载失败", e)
                    return FilterResult(true, "", "", "", data)
                }
            }
        }
        return null
    }

    /** 远端过滤端口：实现由应用启动时经注册点注入。 */
    private fun port(): notifyrelay.domain.feature.notification.filter.RemoteFilterPort =
        notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry.current
            ?: error("RemoteFilterPort 未注册")

    /**
     * 对等模式 / 黑白名单 / 锁屏(1) 过滤。
     * 返回非 null 表示命中过滤需提前返回的结果，null 表示继续。
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
        if (port().enablePeerMode) {
            if (mappedPkg !in installedPkgs) {
                return FilterResult(false, mappedPkg, title, text, data)
            }
        }

        // 黑白名单过滤 — 委托给 Rust Core（含关键词匹配）
        val filterMode = port().filterMode
        if (filterMode == "black" || filterMode == "white") {
            val pass = port().checkFilterAccepted(mappedPkg, title, text)
            if (!pass) {
                return FilterResult(false, mappedPkg, title, text, data)
            }
        }

        // 锁屏通知过滤
        if (port().enableLockScreenOnly && !isLocked) {
            return FilterResult(false, mappedPkg, title, text, data)
        }

        return null
    }

    /**
     * 待撤回通知超时后标记内容已见（core 10 秒窗口）。
     */
    private fun onTimeoutSeen(
        title: String,
        text: String,
    ) {
        InboundNotificationProcessor.markContentSeen("", title, text)
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
        if (!port().enableDedup) return
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
        if (!port().enableDedup) return
        val normalizedPendingTitle = TextUtils.normalizeTitle(title ?: "")
        val pendingText = text ?: ""
        // 先处理占位匹配（用于延迟复刻的占位）——在单独的锁上操作以避免并发问题
        placeholders.removeMatching(title, text, packageName) { ph ->
            // 命中占位（阻止延迟复刻）：将其标记为内容已见
            InboundNotificationProcessor.markContentSeen("", ph.title, ph.text)
        }

        // 再处理已发送但在可撤回期的通知
        val matches = pendingMonitor.cancelMatching(normalizedPendingTitle, pendingText, packageName)
        if (matches.isNotEmpty() && DebugFlags.DEBUG_UI_ENABLED.value) {
            val titlePreview = if ((title?.length ?: 0) > 10) "${title?.take(10)}..." else (title ?: "")
            Logger.d("智能去重", "被动命中待撤回通知 - 包名:$packageName, 标题预览:$titlePreview, 匹配数量:${matches.size}")
        }
        // 对于命中的通知，把本地这条记录记入内容窗口，避免短时间内再次复刻
        matches.forEach { InboundNotificationProcessor.markContentSeen("", it.title, it.text) }
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
        if (!port().enableDedup) {
            return
        }
        pendingMonitor.addPendingNotification(notifyId, title, text, packageName, context)
    }
}
