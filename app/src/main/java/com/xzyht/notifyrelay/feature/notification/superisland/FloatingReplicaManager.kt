package com.xzyht.notifyrelay.feature.notification.superisland

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.lifecyle.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingComposeContainer
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingEntry
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowLifecycleOwner
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.lifecyle.LifecycleManager
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.lifecyle.LiveUpdatesNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 接收端的超级岛复刻实现骨架。
 * 说明：真正的系统级悬浮窗需要用户授予 "悬浮窗/Display over other apps" 权限（TYPE_APPLICATION_OVERLAY），
 * 如果没有权限则退化为发送高优先级临时通知来提示用户（不会获得和系统超级岛完全一致的视觉效果）。
 */
object FloatingReplicaManager {
    private const val TAG = "超级岛复刻实现骨架"
    private const val FIXED_WIDTH_DP = 320 // 固定悬浮窗宽度，以确保MultiProgressRenderer完整显示

    /**
     * 错误处理包装器，统一处理 try-catch 和日志记录
     * @param actionName 操作名称，用于日志记录
     * @param block 要执行的代码块
     */
    private inline fun runWithErrorHandling(
        actionName: String, 
        crossinline block: () -> Unit
    ) {
        try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
        }
    }

    /**
     * 带返回值的错误处理包装器
     * @param actionName 操作名称，用于日志记录
     * @param default 默认返回值，当执行失败时返回
     * @param block 要执行的代码块，返回类型为 T
     * @return 执行结果，成功返回 block 的返回值，失败返回 default
     */
    private inline fun <T> runWithErrorHandling(
        actionName: String, 
        default: T, 
        crossinline block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
            default
        }
    }

    /**
     * 支持挂起函数的错误处理包装器
     * @param actionName 操作名称，用于日志记录
     * @param block 要执行的挂起代码块
     */
    private suspend inline fun runWithErrorHandlingSuspend(
        actionName: String, 
        crossinline block: suspend () -> Unit
    ) {
        try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
        }
    }

    /**
     * 带返回值的支持挂起函数的错误处理包装器
     * @param actionName 操作名称，用于日志记录
     * @param default 默认返回值，当执行失败时返回
     * @param block 要执行的挂起代码块，返回类型为 T
     * @return 执行结果，成功返回 block 的返回值，失败返回 default
     */
    private suspend inline fun <T> runWithErrorHandlingSuspend(
        actionName: String, 
        default: T, 
        crossinline block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: $actionName 失败: ${e.message}")
            default
        }
    }

    /**
     * 添加 sourceId 到 entryKey 的映射
     * @param sourceId 来源ID，用于区分不同来源的超级岛通知
     * @param entryKey 条目唯一标识
     */
    private fun addSourceIdMapping(sourceId: String, entryKey: String) {
        if (sourceId.isNotBlank()) {
            val entryKeys = sourceIdToEntryKeyMap.getOrPut(sourceId) { mutableListOf() }
            if (!entryKeys.contains(entryKey)) {
                entryKeys.add(entryKey)
            }
        }
    }

    /**
     * 从映射中移除条目并更新列表
     * @param key 要移除的条目唯一标识
     * @return 被移除的 sourceId 列表，如果没有则返回 null
     */
    private fun removeSourceIdMapping(key: String): List<String>? {
        val sourceIdsToRemove = mutableListOf<String>()
        val sourceIdsToUpdate = mutableMapOf<String, MutableList<String>>()
        
        sourceIdToEntryKeyMap.forEach { (sourceId, keys) ->
            if (keys.contains(key)) {
                val updatedKeys = keys.toMutableList()
                updatedKeys.remove(key)
                if (updatedKeys.isEmpty()) {
                    sourceIdsToRemove.add(sourceId)
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
        
        return if (sourceIdsToRemove.isEmpty()) null else sourceIdsToRemove
    }

    /**
     * 根据 sourceId 查找并获取 entryKey 列表
     * @param sourceId 来源ID
     * @return entryKey 列表，如果没有则返回 null
     */
    private fun getSourceIdEntryKeys(sourceId: String): List<String>? {
        return sourceIdToEntryKeyMap[sourceId]
    }

    /**
     * 处理移除原因的逻辑
     * @param sourceId 来源ID
     * @param reason 移除原因
     */
    private fun handleRemovalReason(
        sourceId: String, 
        reason: FloatingWindowManager.RemovalReason
    ) {
        // 仅当用户手动移除通知时，将sourceId添加到黑名单，避免短时间内再次弹出
        // 自动超时关闭或远端移除不应触发黑名单
        // 如果是用户点击隐藏 (HIDDEN)，也触发黑名单，防止远端更新立即重新显示
        if (reason == FloatingWindowManager.RemovalReason.MANUAL || reason == FloatingWindowManager.RemovalReason.HIDDEN) {
            blockInstance(sourceId)
        }

        // 同时关闭对应的Live Updates复合通知
        // 只有在彻底移除时才关闭，HIDDEN 模式下保留
        if (reason != FloatingWindowManager.RemovalReason.HIDDEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            runWithErrorHandling("关闭Live Updates复合通知") {
                val context = overlayView?.get()?.context
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

    // Compose浮窗管理器
    private val floatingWindowManager = FloatingWindowManager().apply {
        // 设置条目为空时的回调
        onEntriesEmpty = { 
            removeOverlayContainer() 
            // 当所有条目为空时，清除所有通知
            NotificationGenerator.clearAllReplicaNotifications(overlayView?.get()?.context, entryKeyToNotificationId)
        }
        // 设置条目移除回调，当单个条目被移除时取消对应的通知
        onEntryRemoved = { key, reason -> 
            // 只有当原因不是 HIDDEN 时，才移除系统通知
            if (reason != FloatingWindowManager.RemovalReason.HIDDEN) {
                val context = overlayView?.get()?.context
                if (context != null) {
                    NotificationGenerator.cancelReplicaNotification(context, key, entryKeyToNotificationId)
                } else {
                    // 如果没有上下文，直接从映射中移除
                    entryKeyToNotificationId.remove(key)
                }
                // 如果不是 HIDDEN，从 hiddenEntries 中移除条目
                hiddenEntries.remove(key)
            } else {
                // 如果是 HIDDEN，我们仍然需要从 FloatingWindowManager 中移除条目（已由 removeEntry 完成）
                // 但我们需要保留 Notification 以便用户再次点击
                // 所以我们不调用 cancelReplicaNotification
                Logger.i(TAG, "超级岛: 条目被隐藏 (HIDDEN)，保留系统通知以便恢复, key=$key")
            }

            // 从sourceId映射中移除，并将sourceId添加到黑名单
            val sourceIdsToBlock = removeSourceIdMapping(key)
            
            // 第三次遍历：处理需要添加到黑名单和关闭Live Updates通知的sourceId
            sourceIdsToBlock?.forEach { sourceId ->
                handleRemovalReason(sourceId, reason)
            }
        }
    }
    // Compose生命周期管理器
    private val lifecycleManager = LifecycleManager()
    // 提供给 Compose 的生命周期所有者（不依赖 ViewTree）
    private var overlayLifecycleOwner: FloatingWindowLifecycleOwner? = null

    // 浮窗容器视图
    private var overlayView: WeakReference<View>? = null
    // 浮窗布局参数
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    // WindowManager实例
    private var windowManager: WeakReference<WindowManager>? = null

    // 会话级屏蔽池：进程结束后自然清空，value 为最后屏蔽时间戳
    private val blockedInstanceIds = ConcurrentHashMap<String, Long>()
    // 会话级屏蔽过期时间（默认 15 秒），避免用户刚刚关闭后立即再次弹出
    private const val BLOCK_EXPIRE_MS = 15_000L

    // 保存sourceId到entryKey列表的映射，以便后续能正确移除条目
    // 一个sourceId可能对应多个条目，所以使用列表保存
    private val sourceIdToEntryKeyMap = ConcurrentHashMap<String, MutableList<String>>()
    
    // 保存entryKey到notificationId的映射，用于管理复刻通知
    private val entryKeyToNotificationId = ConcurrentHashMap<String, Int>()
    
    // 保存被隐藏的条目，以便用户点击通知时恢复
    private val hiddenEntries = ConcurrentHashMap<String, Any>()
    // 通知渠道ID
    private const val NOTIFICATION_CHANNEL_ID = "super_island_replica"
    // 通知ID基础值
    private const val NOTIFICATION_BASE_ID = 20000

    /**
     * 显示超级岛复刻悬浮窗。
     * paramV2Raw: miui.focus.param 中 param_v2 的原始 JSON 字符串（可为 null）
     * picMap: 从 extras 中解析出的图片键->URL 映射（可为 null）
     */
    // sourceId: 用于区分不同来源的超级岛通知（通常传入 superPkg），用于刷新/去重同一来源的浮窗
    fun showFloating(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
        isLocked: Boolean = false
    ) {
        showFloatingInternal(context, sourceId, title, text, paramV2Raw, picMap, appName, isLocked, false)
    }

    /**
     * 内部显示浮窗方法
     */
    private fun showFloatingInternal(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
        isLocked: Boolean = false,
        isRestoring: Boolean = false // 是否是从隐藏状态恢复
    ) {
        runWithErrorHandling("显示浮窗") {
            // 会话级屏蔽检查：同一个 instanceId 在本轮被用户关闭后不再展示
            // 如果是从隐藏状态恢复，则忽略屏蔽检查
            if (!isRestoring && sourceId.isNotBlank() && isInstanceBlocked(sourceId)) {
                Logger.i(TAG, "超级岛: instanceId=$sourceId 已在本轮会话中被屏蔽，忽略展示")
                return@runWithErrorHandling
            }

            if (!canShowOverlay(context)) {
                Logger.i(TAG, "超级岛: 无悬浮窗权限，尝试请求权限")
                requestOverlayPermission(context)
                return@runWithErrorHandling
            }

            CoroutineScope(Dispatchers.Main).launch {
                runWithErrorHandlingSuspend("显示浮窗(协程)") {
                    // 预先准备生命周期所有者，供 Compose 注入 LocalLifecycleOwner 使用
                    if (overlayLifecycleOwner == null) {
                        overlayLifecycleOwner = FloatingWindowLifecycleOwner()
                    }
                    // 通知Compose生命周期管理器浮窗显示
                    lifecycleManager.onShow()
                    // 尝试解析paramV2
                    val paramV2 = NotificationGenerator.parseParamV2Safe(paramV2Raw)

                    // 判断是否为摘要态
                    val summaryOnly = when {
                        paramV2?.business == "miui_flashlight" -> true
                        paramV2Raw?.contains("miui_flashlight") == true -> true
                        else -> false
                    }

                    // 将所有图片 intern 为引用，避免重复保存相同图片 - 移到 IO 线程执行
                    val internedPicMap = withContext(Dispatchers.IO) {
                        SuperIslandImageStore.internAll(context, picMap)
                    }
                    // 生成唯一的entryKey，确保包含sourceId，以便后续能正确移除
                    // 对于同一通知的不同时间更新，应该使用相同的key，所以不能包含时间戳
                    val entryKey = sourceId

                    // 更新Compose浮窗管理器的条目
                    // 锁屏状态下不自动展开，非锁屏状态保持原有逻辑
                    floatingWindowManager.addOrUpdateEntry(
                        key = entryKey,
                        paramV2 = paramV2,
                        paramV2Raw = paramV2Raw,
                        picMap = internedPicMap,
                        isExpanded = if (isLocked) false else !summaryOnly,
                        summaryOnly = summaryOnly,
                        business = paramV2?.business,
                        title = title,
                        text = text,
                        appName = appName
                    )

                    // 保存sourceId到entryKey的映射，以便后续能正确移除
                    addSourceIdMapping(sourceId, entryKey)

                    // 创建或更新浮窗UI
                    addOrUpdateEntry(context, entryKey, summaryOnly)
                    
                    // 对于进度类型，且Live Updates启用时，只发送复合通知作为浮窗的生命周期管理
                    // 否则发送传统复刻通知
                    val isProgressType = paramV2?.progressInfo != null || paramV2?.multiProgressInfo != null
                    
                    // 如果是从隐藏状态恢复，不重新发送通知，使用现有的通知
                    if (!isRestoring) {
                        if (isProgressType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            runWithErrorHandlingSuspend("发送Live Updates复合通知") {
                                // 初始化Live Updates通知管理器
                                LiveUpdatesNotificationManager.initialize(context)
                                // 发送复合通知
                                LiveUpdatesNotificationManager.showLiveUpdate(
                                    sourceId, title, text, paramV2Raw, appName, isLocked, internedPicMap
                                )
                                Logger.i(TAG, "浮窗创建时发送Live Updates复合通知作为生命周期管理: sourceId=$sourceId")
                            }
                        } else {
                            // 非进度类型或Live Updates未启用时，发送传统复刻通知
                            NotificationGenerator.sendReplicaNotification(context, entryKey, title, text, appName, paramV2, internedPicMap, sourceId, floatingWindowManager, entryKeyToNotificationId)
                        }
                    } else {
                        Logger.i(TAG, "浮窗从隐藏状态恢复，不重新发送通知，使用现有的通知: sourceId=$sourceId")
                    }
                }
            }
        }
    }

    /**
     * 切换浮窗显示状态：如果显示则隐藏，如果隐藏则显示
     */
    fun toggleFloating(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null
    ) {
        runWithErrorHandling("切换浮窗状态") {
            // 检查当前是否已显示
            val entryKeys = sourceIdToEntryKeyMap[sourceId]
            val isShowing = entryKeys?.any { floatingWindowManager.getEntry(it) != null } == true

            if (isShowing) {
                // 当前已显示，执行隐藏操作
                // 使用 HIDDEN 原因，这样不会移除系统通知
                Logger.i(TAG, "超级岛: 点击通知切换 - 隐藏浮窗, sourceId=$sourceId")
                
                // 先获取条目数据，然后再移除条目
                val entry = floatingWindowManager.getEntry(sourceId)
                if (entry != null) {
                    // 保存条目数据到 hiddenEntries
                    hiddenEntries[sourceId] = entry
                    Logger.i(TAG, "超级岛: 保存被隐藏的条目到 hiddenEntries, key=$sourceId")
                }
                
                dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.HIDDEN)
            } else {
                // 当前未显示，执行恢复显示操作
                Logger.i(TAG, "超级岛: 点击通知切换 - 恢复浮窗, sourceId=$sourceId")
                // 恢复显示时，移除黑名单，确保可以显示
                blockedInstanceIds.remove(sourceId)
                
                // 尝试从 hiddenEntries 映射中获取已有的条目数据
                val existingEntry = hiddenEntries[sourceId]
                if (existingEntry is FloatingEntry) {
                    // 使用已有的条目数据，确保与通知的数据一致
                    showFloatingInternal(
                        context, sourceId, 
                        existingEntry.title, 
                        existingEntry.text, 
                        existingEntry.paramV2Raw, 
                        existingEntry.picMap, 
                        existingEntry.appName, 
                        isLocked = false, isRestoring = true
                    )
                    // 从 hiddenEntries 中移除条目，因为它已经被恢复
                    hiddenEntries.remove(sourceId)
                    Logger.i(TAG, "超级岛: 从 hiddenEntries 中移除已恢复的条目, key=$sourceId")
                } else {
                    // 如果没有已有的条目数据，使用传递过来的数据
                    showFloatingInternal(
                        context, sourceId, title, text, paramV2Raw, picMap, appName, 
                        isLocked = false, isRestoring = true
                    )
                }
            }
        }
    }

    // ---- 会话级屏蔽工具方法 ----

    private fun isInstanceBlocked(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val ts = blockedInstanceIds[instanceId] ?: return false
        // 检查是否超过过期时间
        if (now - ts > BLOCK_EXPIRE_MS) {
            blockedInstanceIds.remove(instanceId)
            Logger.i(TAG, "超级岛: 屏蔽过期，自动移除 instanceId=$instanceId")
            return false
        }
        // 如果会话仍在活跃（有新请求），更新屏蔽时间，让屏蔽继续保持
        blockedInstanceIds[instanceId] = now
        return true
    }

    private fun blockInstance(instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        blockedInstanceIds[instanceId] = System.currentTimeMillis()
        Logger.i(TAG, "超级岛: 会话级屏蔽 instanceId=$instanceId")
    }

    private fun onEntryClicked(key: String) {
        // 使用FloatingWindowManager管理条目状态
        val entry = floatingWindowManager.getEntry(key)
        if (entry == null) {
            return
        }

        // 切换展开/折叠状态，toggleEntryExpanded内部会处理摘要态的情况
        floatingWindowManager.toggleEntryExpanded(key)
    }
    
    /**
     * 处理容器拖动开始事件
     */
    private fun onContainerDragStarted() {
        // 移除原来的关闭层相关逻辑
    }

    /**
     * 处理容器拖动结束事件
     */
    private fun onContainerDragEnded() {
        // 移除原来的关闭层相关逻辑
    }
    
    /**
     * 根据通知ID关闭对应的浮窗条目
     */
    fun closeByNotificationId(notificationId: Int) {
        runWithErrorHandling("根据通知ID关闭浮窗条目") {
            // 查找对应的entryKey
            var entryKey = entryKeyToNotificationId.entries.find { it.value == notificationId }?.key
            
            // 如果在映射中找不到，尝试处理LiveUpdatesNotificationManager生成的通知ID
            if (entryKey == null) {
                // LiveUpdatesNotificationManager使用的NOTIFICATION_BASE_ID是10000
                // 尝试计算sourceId并查找对应的entryKey
                val potentialSourceIdHash = notificationId - 10000
                if (potentialSourceIdHash > 0) {
                    // 遍历sourceIdToEntryKeyMap，查找可能对应的sourceId
                    for ((sourceId, keys) in sourceIdToEntryKeyMap) {
                        if (sourceId.hashCode().and(0xffff) == potentialSourceIdHash) {
                            // 找到匹配的sourceId，使用第一个对应的entryKey
                            entryKey = keys.firstOrNull()
                            break
                        }
                    }
                }
            }
            
            if (entryKey != null) {
                // 移除浮窗条目，这会触发onEntryRemoved回调，进而取消对应的通知并清理映射
                // 标记为手动移除
                floatingWindowManager.removeEntry(entryKey, FloatingWindowManager.RemovalReason.MANUAL)
                Logger.i(TAG, "超级岛: 根据通知ID关闭浮窗条目成功，notificationId=$notificationId, entryKey=$entryKey")
            } else {
                // 如果仍然找不到，尝试直接使用notificationId作为entryKey
                runWithErrorHandling("根据通知ID直接关闭浮窗条目") {
                    floatingWindowManager.removeEntry(notificationId.toString(), FloatingWindowManager.RemovalReason.MANUAL)
                    Logger.i(TAG, "超级岛: 根据通知ID直接关闭浮窗条目成功，notificationId=$notificationId")
                }
            }
        }
    }
    
    /**
     * 关闭所有浮窗条目
     */
    fun closeAllEntries() {
        runWithErrorHandling("关闭所有浮窗条目") {
            val context = overlayView?.get()?.context
            // 清空所有条目
            floatingWindowManager.clearAllEntries()
            // 取消所有复刻通知
            if (context != null) {
                NotificationGenerator.clearAllReplicaNotifications(context, entryKeyToNotificationId)
            } else {
                // 如果没有上下文，直接清空映射
                entryKeyToNotificationId.clear()
            }
            // 清空映射
            sourceIdToEntryKeyMap.clear()
            Logger.i(TAG, "超级岛: 关闭所有浮窗条目成功")
        }
    }

    // 新增：按来源键立刻移除指定浮窗（用于接收终止事件SI_END时立即消除）
    fun dismissBySource(sourceId: String) {
        dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.REMOTE)
    }

    private fun dismissBySourceInternal(
        sourceId: String, 
        reason: FloatingWindowManager.RemovalReason = FloatingWindowManager.RemovalReason.REMOTE
    ) {
        runWithErrorHandling("按来源关闭浮窗") {
            // 从映射中获取所有对应的entryKey
            val entryKeys = getSourceIdEntryKeys(sourceId)
            if (entryKeys != null) {
                // 移除所有相关条目，这会触发onEntryRemoved回调，进而取消对应的通知并清理映射
                entryKeys.forEach { entryKey ->
                    floatingWindowManager.removeEntry(entryKey, reason)
                }
                // 清理映射关系（如果还有剩余）
                sourceIdToEntryKeyMap.remove(sourceId)
            } else {
                // 如果没有找到映射，尝试直接使用sourceId移除，这会触发onEntryRemoved回调
                floatingWindowManager.removeEntry(sourceId, reason)
            }
            // 如果对应的会话结束（SI_END），同步移除黑名单，允许后续同一通知重新展示
            // 只有在 REMOTE 或 TIMEOUT 移除时才移除黑名单（因为 MANUAL 会加黑名单）
            if (reason == FloatingWindowManager.RemovalReason.REMOTE || reason == FloatingWindowManager.RemovalReason.TIMEOUT) {
                blockedInstanceIds.remove(sourceId)
            }
        }
    }

    /**
     * 移除浮窗容器
     */
    private fun removeOverlayContainer() {
        runWithErrorHandling("移除浮窗容器") {
            val view = overlayView?.get()
            val wm = windowManager?.get()
            val lp = overlayLayoutParams

            if (view != null && wm != null && lp != null) {
                // 移除浮窗容器
                wm.removeView(view)
                Logger.i(TAG, "超级岛: 浮窗容器已移除")

                // 清理资源
                overlayView = null
                overlayLayoutParams = null
                windowManager = null

                // 通知Compose生命周期管理器浮窗隐藏
                lifecycleManager.onHide()

                // 调用生命周期所有者的onHide方法
                overlayLifecycleOwner?.let {
                    try { it.onHide() } catch (_: Exception) {}
                }
            }
        }
        // 即使移除失败，也要清理资源引用，避免内存泄漏
        overlayView = null
        overlayLayoutParams = null
        windowManager = null
    }

    /**
     * 添加或更新浮窗条目
     * @param context 上下文
     * @param key 条目唯一标识
     * @param summaryOnly 是否为摘要态
     */
    private fun addOrUpdateEntry(
        context: Context,
        key: String,
        summaryOnly: Boolean
    ) {
        runWithErrorHandling("addOrUpdateEntry") {
            // 首条条目到来时再创建 Overlay 容器，避免先有空容器
            if (overlayView?.get() == null || windowManager?.get() == null || overlayLayoutParams == null) {
                runWithErrorHandling("创建浮窗容器") {
                    val appCtx = context.applicationContext
                    val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        ?: return@runWithErrorHandling

                    // 确保存在用于 Compose 的生命周期所有者（不依赖 ViewTree）
                    val lifecycleOwner = overlayLifecycleOwner ?: FloatingWindowLifecycleOwner().also {
                        overlayLifecycleOwner = it
                    }
                    // 标记浮窗进入前台生命周期，供 Compose 使用
                    try { lifecycleOwner.onShow() } catch (_: Exception) {}
                    // 通知Compose生命周期管理器浮窗显示
                    lifecycleManager.onShow()

                    val density = context.resources.displayMetrics.density
                    val layoutParams = WindowManager.LayoutParams(
                        (FIXED_WIDTH_DP * density).toInt(),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.LEFT or Gravity.TOP
                        x = ((context.resources.displayMetrics.widthPixels - (FIXED_WIDTH_DP * density).toInt()) / 2).coerceAtLeast(0)
                        y = 100
                    }

                    // 使用Compose容器替代传统的FrameLayout和LinearLayout
                    val composeContainer = FloatingComposeContainer(context).apply {
                        val padding = (12 * density).toInt()
                        setPadding(padding, padding, padding, padding)
                        // 设置浮窗管理器
                        this.floatingWindowManager = this@FloatingReplicaManager.floatingWindowManager
                        // 设置生命周期所有者
                        this.lifecycleOwner = lifecycleOwner
                        // 设置WindowManager和LayoutParams，用于更新浮窗位置
                        this.windowManager = wm
                        this.windowLayoutParams = layoutParams
                        // 设置条目点击回调
                        this.onEntryClick = { entryKey -> onEntryClicked(entryKey) }
                        // 设置容器拖动开始回调
                        this.onContainerDragStart = { onContainerDragStarted() }
                        // 设置容器拖动中回调，移除了关闭区重叠检测逻辑
                        this.onContainerDragging = { }
                        // 设置容器拖动结束回调
                        this.onContainerDragEnd = { onContainerDragEnded() }
                    }

                    var added = false
                    runWithErrorHandling("addView") {
                        wm.addView(composeContainer, layoutParams)
                        added = true
                    }
                    if (added) {
                        overlayView = WeakReference(composeContainer)
                        overlayLayoutParams = layoutParams
                        windowManager = WeakReference(wm)
                        Logger.i(TAG, "超级岛: 浮窗容器已创建(首条条目触发)，x=${layoutParams.x}, y=${layoutParams.y}")
                    }
                }
            }
        }
    }

    private fun canShowOverlay(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    private fun requestOverlayPermission(context: Context) {
        runWithErrorHandling("请求悬浮窗权限") {
            val intent = IntentUtils.createImplicitIntent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            IntentUtils.startActivity(context, intent, true)
        }
    }
}
