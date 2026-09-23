package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.view.View
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingEntry
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandNotificationIds
import kotlinx.coroutines.Job
import notifyrelay.base.util.Logger
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.iterator

object FloatingReplicaMappingManager {
    private const val TAG = "超级岛映射管理"

    private var appContextRef: Context? = null

    fun setAppContext(context: Context?) {
        appContextRef = context?.applicationContext
    }

    fun getAppContext(): Context? = appContextRef

    private val sourceIdToEntryKeyMap = ConcurrentHashMap<String, MutableSet<String>>()

    private val entryKeyToNotificationId = ConcurrentHashMap<String, Int>()

    private val sourceIdToNotificationIds = ConcurrentHashMap<String, MutableSet<Int>>()

    private val closedSourceIds = ConcurrentHashMap<String, Long>()
    private val closedSourceVersions = ConcurrentHashMap<String, Long>()

    private val timeoutJobs = ConcurrentHashMap<String, Job>()

    private val hiddenEntries = ConcurrentHashMap<String, FloatingEntry>()

    private val sourceVersions = ConcurrentHashMap<String, AtomicLong>()

    private val blockedInstanceIds = ConcurrentHashMap<String, Long>()
    private const val BLOCK_EXPIRE_MS = 15_000L

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

    fun getNotificationId(entryKey: String): Int? = entryKeyToNotificationId[entryKey]

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

    /**
     * 判断注入模式是否真的发生了变化。
     *
     * 首次发送（无记录）不算「变化」——此时没有旧通知需要迁移，
     * 返回 false 让调用方直接按当前模式发送即可。
     */
    fun hasInjectionModeChanged(
        sourceId: String,
        currentModeOrdinal: Int,
    ): Boolean {
        val previous = sourceIdToInjectionMode[sourceId] ?: return false
        return previous != currentModeOrdinal
    }

    /** 记录本次发送所用的注入模式（发送成功后调用） */
    fun setInjectionMode(
        sourceId: String,
        modeOrdinal: Int,
    ) {
        sourceIdToInjectionMode[sourceId] = modeOrdinal
    }

    fun removeInjectionMode(sourceId: String) {
        sourceIdToInjectionMode.remove(sourceId)
    }

    /**
     * 注入模式变化时的共享迁移逻辑：先取消该 sourceId 的旧通知并移除旧映射，
     * 再允许调用方按新注入模式发送通知。
     *
     * 为什么必须迁移：超级岛通道与 Live Updates 通道使用**不同的 notificationId**
     * （由 `SuperIslandNotificationIds` 按通道基址 + 16 位哈希推导，两通道基址间距大于哈希空间，
     * 区间互不重叠；列表模式为固定 30000），
     * 且渲染方式不同；若仅在旧通知上叠加，会出现旧通知残留、两条通知并存或旧模式内容不更新。
     *
     * 同时清理内容指纹：指纹只描述内容，不含模式，模式变化后若沿用旧指纹，
     * 后续保活包会被 canSkipRefresh 误判为「无变更」而永不重发。
     *
     * @return 实际取消的旧通知数量
     */
    fun migrateInjectionModeIfChanged(
        context: Context,
        sourceId: String,
        currentModeOrdinal: Int,
    ): Int {
        val previous = sourceIdToInjectionMode[sourceId]
        if (previous == null || previous == currentModeOrdinal) {
            // 首次发送或模式未变：仅确保记录存在
            sourceIdToInjectionMode[sourceId] = currentModeOrdinal
            return 0
        }

        // 取消旧通知：先按映射取实际通知 id，再兜底取消两个通道的推导 id，
        // 避免映射缺失时旧通知残留在通知栏
        var cancelled = 0
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mappedIds = removeNotificationIdsBySourceId(sourceId)
        // 兜底取消两个通道的推导 id，避免映射缺失时旧通知残留在通知栏。
        // 统一走 SuperIslandNotificationIds，保证与发送侧使用完全相同的推导公式。
        val fallbackIds =
            listOf(
                SuperIslandNotificationIds.liveUpdates(sourceId), // Live Updates 通道
                SuperIslandNotificationIds.replica(sourceId), // 复刻通道路径
            )
        (mappedIds.orEmpty() + fallbackIds).distinct().forEach { id ->
            try {
                notificationManager.cancel(id)
                cancelled++
            } catch (e: Exception) {
                Logger.w(TAG, "切换注入模式时取消旧通知失败: sourceId=$sourceId, id=$id, ${e.message}")
            }
        }

        // 移除旧映射与旧指纹，保证后续按新模式重新建立映射、且不会被指纹跳过
        removeSourceIdMappings(sourceId)
        removeNotificationFingerprint(sourceId)
        sourceIdToInjectionMode[sourceId] = currentModeOrdinal

        Logger.i(TAG, "注入模式变化($previous→$currentModeOrdinal)，已取消旧通知 $cancelled 条并清理旧映射: sourceId=$sourceId")
        return cancelled
    }

    fun clearAllInjectionModes() {
        sourceIdToInjectionMode.clear()
    }

    fun clearAllNotificationFingerprints() {
        lastNotificationFingerprints.clear()
    }

    fun nextVersion(sourceId: String): Long = sourceVersions.computeIfAbsent(sourceId) { AtomicLong(0) }.incrementAndGet()

    fun isLatestVersion(
        sourceId: String,
        version: Long,
    ): Boolean = sourceVersions[sourceId]?.get() == version

    fun handleRemovalReason(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason,
    ) {
        if (reason == FloatingWindowManager.RemovalReason.MANUAL || reason == FloatingWindowManager.RemovalReason.HIDDEN) {
            blockInstance(sourceId)
        }

        if (reason != FloatingWindowManager.RemovalReason.HIDDEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runReplicaCatching(TAG, "关闭Live Updates复合通知") {
                val context = overlayViewRef?.get()?.context
                if (context != null) {
                    LiveUpdatesNotificationManager.initialize(context)
                    LiveUpdatesNotificationManager.dismissLiveUpdateNotification(sourceId)
                } else {
                    Logger.w(TAG, "无法关闭Live Updates复合通知，上下文为空")
                }
            }
        }
    }

    fun isInstanceBlocked(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val ts = blockedInstanceIds[instanceId] ?: return false
        if (now - ts > BLOCK_EXPIRE_MS) {
            blockedInstanceIds.remove(instanceId)
            Logger.i(TAG, "超级岛: 屏蔽过期，自动移除 instanceId=$instanceId")
            return false
        }
        blockedInstanceIds[instanceId] = now
        return true
    }

    fun blockInstance(instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        blockedInstanceIds[instanceId] = System.currentTimeMillis()
        Logger.i(TAG, "超级岛: 会话级屏蔽 instanceId=$instanceId")
    }

    fun removeBlockedInstance(instanceId: String) {
        blockedInstanceIds.remove(instanceId)
    }

    fun isSourceRecentlyClosed(sourceId: String): Boolean {
        val lastClosed = closedSourceIds[sourceId]
        return lastClosed != null && (System.currentTimeMillis() - lastClosed) < 30_000L
    }

    fun markSourceClosed(sourceId: String) {
        closedSourceIds[sourceId] = System.currentTimeMillis()
        sourceVersions[sourceId]?.get()?.let { closedSourceVersions[sourceId] = it }
    }

    fun removeClosedSource(sourceId: String) {
        closedSourceIds.remove(sourceId)
    }

    fun isSourceRecentlyClosedWithinMinute(sourceId: String): Boolean {
        val lastClosed = closedSourceIds[sourceId] ?: return false
        if (System.currentTimeMillis() - lastClosed >= 60_000L) return false
        val closedVersion = closedSourceVersions[sourceId] ?: return false
        val currentVersion = sourceVersions[sourceId]?.get() ?: return false
        return currentVersion == closedVersion
    }

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

    fun findSourceIdByEntryKey(entryKey: String): String? {
        for ((sourceId, keys) in sourceIdToEntryKeyMap) {
            if (keys.contains(entryKey)) {
                return sourceId
            }
        }
        return null
    }
}
