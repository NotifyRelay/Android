package com.xzyht.notifyrelay.feature.notification.superisland

import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingEntry
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.lifecycle.LiveUpdatesNotificationManager
import notifyrelay.base.util.Logger
import kotlinx.coroutines.Job
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object FloatingReplicaMappingManager {
    private const val TAG = "超级岛映射管理"

    private var appContextRef: android.content.Context? = null

    fun setAppContext(context: android.content.Context?) {
        appContextRef = context?.applicationContext
    }

    fun getAppContext(): android.content.Context? = appContextRef

    private val sourceIdToEntryKeyMap = ConcurrentHashMap<String, MutableList<String>>()

    private val entryKeyToNotificationId = ConcurrentHashMap<String, Int>()

    private val sourceIdToNotificationIds = ConcurrentHashMap<String, MutableList<Int>>()

    private val closedSourceIds = ConcurrentHashMap<String, Long>()

    private val timeoutJobs = ConcurrentHashMap<String, Job>()

    private val hiddenEntries = ConcurrentHashMap<String, FloatingEntry>()

    private val blockedInstanceIds = ConcurrentHashMap<String, Long>()
    private const val BLOCK_EXPIRE_MS = 15_000L

    private var overlayViewRef: WeakReference<android.view.View>? = null

    fun setOverlayView(view: android.view.View?) {
        overlayViewRef = if (view != null) WeakReference(view) else null
    }

    fun addSourceIdMapping(sourceId: String, entryKey: String, notificationId: Int? = null) {
        if (sourceId.isNotBlank()) {
            val entryKeys = sourceIdToEntryKeyMap.getOrPut(sourceId) { mutableListOf() }
            if (!entryKeys.contains(entryKey)) {
                entryKeys.add(entryKey)
            }

            if (notificationId != null) {
                val notificationIds = sourceIdToNotificationIds.getOrPut(sourceId) { mutableListOf() }
                if (!notificationIds.contains(notificationId)) {
                    notificationIds.add(notificationId)
                }
            }
        }
    }

    fun removeSourceIdMapping(key: String): List<String>? {
        val sourceIdsToRemove = mutableListOf<String>()
        val sourceIdsToUpdate = mutableMapOf<String, MutableList<String>>()

        sourceIdToEntryKeyMap.forEach { (sourceId, keys) ->
            if (keys.contains(key)) {
                val updatedKeys = keys.toMutableList()
                updatedKeys.remove(key)
                if (updatedKeys.isEmpty()) {
                    sourceIdsToRemove.add(sourceId)
                    sourceIdToNotificationIds.remove(sourceId)
                } else {
                    sourceIdsToUpdate[sourceId] = updatedKeys
                }
            }
        }

        sourceIdsToUpdate.forEach {
            sourceIdToEntryKeyMap[it.key] = it.value
        }

        sourceIdsToRemove.forEach {
            sourceIdToEntryKeyMap.remove(it)
        }

        return if (sourceIdsToRemove.isNotEmpty()) {
            Logger.i(TAG, "removeSourceIdMapping: 成功移除 sourceIds=$sourceIdsToRemove, key=$key")
            sourceIdsToRemove
        } else {
            Logger.i(TAG, "removeSourceIdMapping: 未找到匹配的 sourceId，key=$key")
            null
        }
    }

    fun getSourceIdEntryKeys(sourceId: String): List<String>? {
        return sourceIdToEntryKeyMap[sourceId]
    }

    fun getNotificationId(entryKey: String): Int? {
        return entryKeyToNotificationId[entryKey]
    }

    fun putNotificationId(entryKey: String, notificationId: Int) {
        entryKeyToNotificationId[entryKey] = notificationId
    }

    fun removeNotificationId(entryKey: String): Int? {
        return entryKeyToNotificationId.remove(entryKey)
    }

    fun getNotificationIdsBySourceId(sourceId: String): List<Int>? {
        return sourceIdToNotificationIds[sourceId]?.toList()
    }

    fun removeNotificationIdsBySourceId(sourceId: String): List<Int>? {
        return sourceIdToNotificationIds.remove(sourceId)
    }

    fun removeSourceIdMappings(sourceId: String) {
        sourceIdToNotificationIds.remove(sourceId)
        val entryKeys = sourceIdToEntryKeyMap.remove(sourceId)
        entryKeys?.forEach { entryKey ->
            entryKeyToNotificationId.remove(entryKey)
        }
    }

    fun handleRemovalReason(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason
    ) {
        if (reason == FloatingWindowManager.RemovalReason.MANUAL || reason == FloatingWindowManager.RemovalReason.HIDDEN) {
            blockInstance(sourceId)
        }

        if (reason != FloatingWindowManager.RemovalReason.HIDDEN && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.BAKLAVA) {
            runWithErrorHandling("关闭Live Updates复合通知") {
                val context = overlayViewRef?.get()?.context
                if (context != null) {
                    LiveUpdatesNotificationManager.initialize(context)
                    LiveUpdatesNotificationManager.dismissLiveUpdateNotification(sourceId)
                    Logger.i(TAG, "关闭Live Updates复合通知: sourceId=$sourceId")
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
    }

    fun removeClosedSource(sourceId: String) {
        closedSourceIds.remove(sourceId)
    }

    fun isSourceRecentlyClosedWithinMinute(sourceId: String): Boolean {
        val lastClosed = closedSourceIds[sourceId]
        return lastClosed != null && (System.currentTimeMillis() - lastClosed) < 60_000L
    }

    fun cancelTimeoutJob(sourceId: String) {
        timeoutJobs.remove(sourceId)?.cancel()
    }

    fun setTimeoutJob(sourceId: String, job: Job) {
        timeoutJobs[sourceId] = job
    }

    fun saveHiddenEntry(sourceId: String, entry: FloatingEntry) {
        hiddenEntries[sourceId] = entry
    }

    fun getHiddenEntry(sourceId: String): FloatingEntry? {
        return hiddenEntries[sourceId]
    }

    fun removeHiddenEntry(sourceId: String) {
        hiddenEntries.remove(sourceId)
    }

    fun getEntryKeyByNotificationId(notificationId: Int): String? {
        return entryKeyToNotificationId.entries.find { it.value == notificationId }?.key
    }

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

    private inline fun runWithErrorHandling(actionName: String, crossinline block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
        }
    }
}