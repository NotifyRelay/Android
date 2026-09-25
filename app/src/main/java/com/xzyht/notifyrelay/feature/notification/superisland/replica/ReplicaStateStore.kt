package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.view.View
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingEntry
import kotlinx.coroutines.Job
import notifyrelay.base.util.Logger
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 超级岛复刻通道的**纯状态**存储。
 *
 * 由原 `FloatingReplicaMappingManager`（God Object，446 行）拆分而来，本文件只保留状态读写：
 * 三张映射表、内容指纹、注入模式记录、版本号、隐藏条目暂存、超时任务句柄与应用上下文。
 *
 * 拆出的另外两部分：
 * - [ReplicaTtlRegistry]：`closedSourceIds` / `blockedInstanceIds` 两组 TTL；
 * - [ReplicaNotificationCloser]：取消系统通知的**副作用**（注入模式迁移、按来源/按 id 关闭通知族）。
 *
 * 副作用从状态对象中剥离，是本拆分的核心目的：状态对象不再直接持有 Context 去 cancel 通知。
 *
 * 日志 TAG 沿用原值 `超级岛映射管理`，保证拆分前后日志逐字不变。
 */
internal object ReplicaStateStore {
    private const val TAG = "超级岛映射管理"

    private var appContextRef: Context? = null

    fun setAppContext(context: Context?) {
        appContextRef = context?.applicationContext
    }

    fun getAppContext(): Context? = appContextRef

    private val sourceIdToEntryKeyMap = ConcurrentHashMap<String, MutableSet<String>>()

    private val entryKeyToNotificationId = ConcurrentHashMap<String, Int>()

    private val sourceIdToNotificationIds = ConcurrentHashMap<String, MutableSet<Int>>()

    private val timeoutJobs = ConcurrentHashMap<String, Job>()

    private val hiddenEntries = ConcurrentHashMap<String, FloatingEntry>()

    private val sourceVersions = ConcurrentHashMap<String, AtomicLong>()

    // 上次成功发出的系统通知内容指纹（sourceId → 指纹）。
    // 保活包内容无变更时跳过 notify()，仅重置内部撤回计时器；通知撤回时同步清理，保证撤回后会重新发出
    private val lastNotificationFingerprints = ConcurrentHashMap<String, String>()

    // 每个 sourceId 上次发送通知时所用的注入模式（存 SpecInjectionMode.ordinal）。
    // 注入模式决定通知走「超级岛通道」还是「Live Updates 通道」（两者通知 id 与渲染方式均不同），
    // 模式切换后必须重发，否则旧通知会残留并与新通知并存。
    private val sourceIdToInjectionMode = ConcurrentHashMap<String, Int>()

    private var overlayViewRef: WeakReference<View>? = null

    fun setOverlayView(view: View?) {
        overlayViewRef = if (view != null) WeakReference(view) else null
    }

    /**
     * 取浮窗容器 View 的 Context（由 [setOverlayView] 登记的弱引用）。
     *
     * 与 [getAppContext] 不是同一来源：原实现在关闭 Live Updates 通知时用的是浮窗 View 的
     * Context，此处保持该来源不变。
     */
    fun getOverlayContext(): Context? = overlayViewRef?.get()?.context

    fun addSourceIdMapping(
        sourceId: String,
        entryKey: String,
        notificationId: Int? = null,
    ) {
        if (sourceId.isNotBlank()) {
            sourceIdToEntryKeyMap.computeIfAbsent(sourceId) { ConcurrentHashMap.newKeySet() }.add(entryKey)
            if (notificationId != null) {
                sourceIdToNotificationIds.computeIfAbsent(sourceId) { ConcurrentHashMap.newKeySet() }.add(notificationId)
            }
        }
    }

    fun removeSourceIdMapping(key: String): List<String>? {
        val sourceIdsToRemove = mutableListOf<String>()

        sourceIdToEntryKeyMap.forEach { (sourceId, keys) ->
            if (keys.contains(key)) {
                sourceIdToEntryKeyMap.compute(sourceId) { _, currentKeys ->
                    if (currentKeys != null) {
                        currentKeys.remove(key)
                        if (currentKeys.isEmpty()) {
                            sourceIdsToRemove.add(sourceId)
                            null
                        } else {
                            currentKeys
                        }
                    } else {
                        null
                    }
                }
            }
        }

        sourceIdsToRemove.forEach {
            sourceIdToNotificationIds.remove(it)
            lastNotificationFingerprints.remove(it)
        }

        return if (sourceIdsToRemove.isNotEmpty()) {
            sourceIdsToRemove
        } else {
            null
        }
    }

    fun getSourceIdEntryKeys(sourceId: String): List<String>? = sourceIdToEntryKeyMap[sourceId]?.toList()

    fun putNotificationId(
        entryKey: String,
        notificationId: Int,
    ) {
        entryKeyToNotificationId[entryKey] = notificationId
    }

    fun removeNotificationId(entryKey: String): Int? = entryKeyToNotificationId.remove(entryKey)

    fun getAllNotificationIds(): Map<String, Int> = HashMap(entryKeyToNotificationId)

    fun clearAllNotificationIds() {
        entryKeyToNotificationId.clear()
    }

    fun getNotificationIdsBySourceId(sourceId: String): List<Int>? = sourceIdToNotificationIds[sourceId]?.toList()

    /** 当前已登记的全部远端 sourceId（用于按通道批量关闭通知）。 */
    fun getAllSourceIds(): List<String> = sourceIdToEntryKeyMap.keys.toList()

    /**
     * 清空全部映射与派生状态（关闭远端显示 / 列表模式通道切换时使用）。
     *
     * 保留 `closedSourceIds` 与 `blockedInstanceIds`（在 [ReplicaTtlRegistry] 中）：
     * 通道切换不应解除用户刚刚表达过的关闭意图，两者仍按各自 TTL 自然过期。
     */
    fun clearAllMappings() {
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        sourceIdToEntryKeyMap.clear()
        entryKeyToNotificationId.clear()
        sourceIdToNotificationIds.clear()
        sourceVersions.clear()
        lastNotificationFingerprints.clear()
        sourceIdToInjectionMode.clear()
        hiddenEntries.clear()
    }

    /**
     * 指纹命中时用于二次确认：此前发出的通知是否**确实仍在本应用的活动通知中**。
     *
     * 若上次 notify 被系统拦下（焦点通知膨胀失败 / 鉴权未就绪等）或通知已被撤回，
     * 则不应跳过刷新，否则后续保活包与重复触发将永远不再重发，表现为「怎么点都不出」。
     */
    fun isAnyNotificationActive(
        context: Context,
        ids: List<Int>?,
    ): Boolean {
        if (ids.isNullOrEmpty()) return false
        return try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ownPackage = context.packageName
            notificationManager.activeNotifications.any { sbn -> sbn.packageName == ownPackage && sbn.id in ids }
        } catch (e: Exception) {
            Logger.w(TAG, "查询活动通知失败: ${e.message}")
            false
        }
    }

    fun removeNotificationIdsBySourceId(sourceId: String): List<Int>? {
        lastNotificationFingerprints.remove(sourceId)
        return sourceIdToNotificationIds.remove(sourceId)?.toList()
    }

    fun removeSourceIdMappings(sourceId: String) {
        sourceIdToNotificationIds.remove(sourceId)
        val entryKeys = sourceIdToEntryKeyMap.remove(sourceId)
        entryKeys?.forEach { entryKey ->
            entryKeyToNotificationId.remove(entryKey)
        }
        sourceVersions.remove(sourceId)
        lastNotificationFingerprints.remove(sourceId)
        sourceIdToInjectionMode.remove(sourceId)
    }

    /**
     * 计算通知内容指纹：注入模式 + 标题 + 正文 + paramV2Raw + 图片映射。
     *
     * 注入模式必须参与指纹：不同模式走不同通道（超级岛 / Live Updates），
     * 渲染结果与通知 id 均不同；若模式变化而指纹不变，canSkipRefresh 会误判为
     * 「内容无变更」而跳过重发，导致切换注入模式后通知不更新。
     *
     * @param injectionModeOrdinal 当前注入模式序号（[SuperIslandConfigUtils.SpecInjectionMode]）
     */
    fun computeNotificationFingerprint(
        title: String?,
        text: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        injectionModeOrdinal: Int = -1,
    ): String {
        val pics =
            picMap
                ?.entries
                ?.sortedBy { it.key }
                ?.joinToString(",") { "${it.key}=${it.value}" }
                .orEmpty()
        val input =
            injectionModeOrdinal.toString() + "\u0001" +
                title.orEmpty() + "\u0001" + text.orEmpty() + "\u0001" + paramV2Raw.orEmpty() + "\u0001" + pics
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun getNotificationFingerprint(sourceId: String): String? = lastNotificationFingerprints[sourceId]

    fun setNotificationFingerprint(
        sourceId: String,
        fingerprint: String,
    ) {
        lastNotificationFingerprints[sourceId] = fingerprint
    }

    fun removeNotificationFingerprint(sourceId: String) {
        lastNotificationFingerprints.remove(sourceId)
    }

    /** 记录本次发送所用的注入模式（发送成功后调用） */
    fun setInjectionMode(
        sourceId: String,
        modeOrdinal: Int,
    ) {
        sourceIdToInjectionMode[sourceId] = modeOrdinal
    }

    fun getInjectionMode(sourceId: String): Int? = sourceIdToInjectionMode[sourceId]

    fun clearAllNotificationFingerprints() {
        lastNotificationFingerprints.clear()
    }

    fun nextVersion(sourceId: String): Long = sourceVersions.computeIfAbsent(sourceId) { AtomicLong(0) }.incrementAndGet()

    fun isLatestVersion(
        sourceId: String,
        version: Long,
    ): Boolean = sourceVersions[sourceId]?.get() == version

    /** 当前版本号（供 [ReplicaTtlRegistry.markSourceClosed] 快照「关闭时的版本」）。 */
    fun currentVersion(sourceId: String): Long? = sourceVersions[sourceId]?.get()

    fun cancelTimeoutJob(sourceId: String) {
        timeoutJobs.remove(sourceId)?.cancel()
    }

    fun setTimeoutJob(
        sourceId: String,
        job: Job,
    ) {
        timeoutJobs[sourceId] = job
    }

    fun saveHiddenEntry(
        sourceId: String,
        entry: FloatingEntry,
    ) {
        hiddenEntries[sourceId] = entry
    }

    fun getHiddenEntry(sourceId: String): FloatingEntry? = hiddenEntries[sourceId]

    fun removeHiddenEntry(sourceId: String) {
        hiddenEntries.remove(sourceId)
    }

    fun getEntryKeyByNotificationId(notificationId: Int): String? = entryKeyToNotificationId.entries.find { it.value == notificationId }?.key

    fun findSourceIdByNotificationId(notificationId: Int): String? {
        for ((sourceId, notificationIds) in sourceIdToNotificationIds) {
            if (notificationIds.contains(notificationId)) {
                return sourceId
            }
        }
        val entryKey = getEntryKeyByNotificationId(notificationId)
        if (entryKey != null) {
            for ((sourceId, keys) in sourceIdToEntryKeyMap) {
                if (keys.contains(entryKey)) {
                    return sourceId
                }
            }
        }
        return null
    }
}
