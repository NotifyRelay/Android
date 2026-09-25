package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.net.toUri
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingComposeContainer
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowLifecycleOwner
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.list.FloatingReplicaListModeManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.pipeline.SuperIslandDisplayPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import java.lang.ref.WeakReference

object FloatingReplicaWindowManager {
    private const val TAG = "超级岛浮窗管理"
    private const val FIXED_WIDTH_DP = 320

    private val floatingWindowManager =
        FloatingWindowManager().apply {
            onEntriesEmpty = {
                removeOverlayContainer()
                NotificationGenerator.clearAllReplicaNotifications(overlayView?.get()?.context)
            }
            onEntryRemoved = { key, reason ->
                if (reason != FloatingWindowManager.RemovalReason.HIDDEN) {
                    val context = overlayView?.get()?.context
                    if (context != null) {
                        NotificationGenerator.cancelReplicaNotification(context, key)
                    } else {
                        ReplicaStateStore.removeNotificationId(key)
                    }
                    // 浮窗自身的自动移除（12s/45s）不经过 dismissBySourceInternal，
                    // 必须在此同步移除展示内容缓存，否则切换通道时会把已消失的条目重新展示出来
                    ReplicaDisplayCache.remove(key)
                }

                val sourceIdsToBlock = ReplicaStateStore.removeSourceIdMapping(key)
                sourceIdsToBlock?.forEach { sourceId ->
                    ReplicaNotificationCloser.handleRemovalReason(sourceId, reason)
                }
            }
        }

    private var overlayLifecycleOwner: FloatingWindowLifecycleOwner? = null

    private var overlayView: WeakReference<View>? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var windowManager: WeakReference<WindowManager>? = null

    init {
        ReplicaStateStore.setOverlayView(null)
    }

    fun getFloatingWindowManager(): FloatingWindowManager = floatingWindowManager

    private fun isFloatingWindowEnabled(context: Context): Boolean = SuperIslandConfigUtils.isFloatingWindowEnabled(context)

    fun canShowOverlay(context: Context): Boolean = PermissionHelper.checkOverlayPermission(context)

    fun requestOverlayPermission(context: Context) {
        runReplicaCatching(TAG, "请求悬浮窗权限") {
            val intent = IntentUtils.createImplicitIntent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = "package:${context.packageName}".toUri()
            IntentUtils.startActivity(context, intent, true)
        }
    }

    fun showFloatingInternal(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
        isLocked: Boolean = false,
        isRestoring: Boolean = false,
    ) {
        runReplicaCatching(TAG, "显示浮窗") {
            if (!isFloatingWindowEnabled(context)) {
                Logger.i(TAG, "超级岛: 浮窗功能已关闭，不创建浮窗, sourceId=$sourceId")
                return@runReplicaCatching
            }

            if (!isRestoring && sourceId.isNotBlank() && ReplicaTtlRegistry.isInstanceBlocked(sourceId)) {
                Logger.i(TAG, "超级岛: instanceId=$sourceId 已在本轮会话中被屏蔽，忽略展示")
                return@runReplicaCatching
            }

            if (!canShowOverlay(context)) {
                Logger.i(TAG, "超级岛: 无悬浮窗权限，尝试请求权限")
                requestOverlayPermission(context)
                return@runReplicaCatching
            }

            CoroutineScope(Dispatchers.Main).launch {
                runReplicaCatchingSuspend(TAG, "显示浮窗(协程)") {
                    // 与原实现一致：overlay 生命周期所有者在协程起点（图片入库之前）即备好
                    if (overlayLifecycleOwner == null) {
                        overlayLifecycleOwner = FloatingWindowLifecycleOwner()
                    }

                    // 展示管线（三通道共享）：本通道需额外保证「条目此前已存在」才允许跳过通知刷新
                    SuperIslandDisplayPipeline.dispatch(
                        context = context,
                        request =
                            SuperIslandDisplayPipeline.DisplayRequest(
                                sourceId = sourceId,
                                title = title,
                                text = text,
                                paramV2Raw = paramV2Raw,
                                picMap = picMap,
                                appName = appName,
                                isLocked = isLocked,
                                channel = SuperIslandDisplayPipeline.Channel.FLOATING,
                                tag = TAG,
                                isRestoring = isRestoring,
                                requireEntryExistedBefore = true,
                                titleFallback = null,
                                textFallback = null,
                                // 竞态守卫：协程 nextVersion 可能在 dismissBySource 的 removeSourceIdMappings 之后执行，
                                // 导致版本被 computeIfAbsent 重建、isLatestVersion 误判通过。
                                // 此处复检 isSourceRecentlyClosed（dismissBySource 已 markSourceClosed），命中即中止。
                                extraGuard = { ReplicaTtlRegistry.isSourceRecentlyClosed(sourceId) },
                                abortMessage = "在异步发送期间被关闭，中止显示",
                                skipRefreshMessage = "内容无变更，跳过系统通知刷新，仅重置内部撤回计时器",
                                onContentReady = { content ->
                                    // 记录更新前浮窗条目是否已存在：存在说明系统通知已发出过，保活包无变更时可跳过通知刷新
                                    val entryExistedBefore = floatingWindowManager.getEntry(sourceId) != null

                                    floatingWindowManager.addOrUpdateEntry(
                                        key = sourceId,
                                        paramV2 = content.paramV2,
                                        paramV2Raw = content.formattedData.paramV2Raw,
                                        picMap = content.formattedData.resolvedPicMap,
                                        isExpanded = if (isLocked) false else !content.summaryOnly,
                                        summaryOnly = content.summaryOnly,
                                        business = content.paramV2?.business,
                                        title = content.displayTitle,
                                        text = content.displayText,
                                        appName = appName,
                                    )

                                    ReplicaStateStore.addSourceIdMapping(sourceId, sourceId)

                                    addOrUpdateEntry(context, sourceId, content.summaryOnly)

                                    entryExistedBefore
                                },
                                // 浮窗通道的 entry 映射已在 onContentReady 内登记
                                registerMappingBeforeSend = false,
                                registerMappingOnSendFailure = true,
                                registerLiveUpdateMappingRegardlessOfSuccess = true,
                            ),
                    )
                }
            }
        }
    }

    fun toggleFloating(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
    ) {
        if (!isFloatingWindowEnabled(context)) {
            if (SuperIslandConfigUtils.isNotificationListMode(context)) {
                FloatingReplicaListModeManager.switchNotificationInList(context.applicationContext)
            } else {
                Logger.i(TAG, "超级岛: 浮窗功能已关闭，不处理浮窗状态切换, sourceId=$sourceId")
            }
            return
        }

        runReplicaCatching(TAG, "切换浮窗状态") {
            val entryKeys = ReplicaStateStore.getSourceIdEntryKeys(sourceId)
            val isShowing = entryKeys?.any { floatingWindowManager.getEntry(it) != null } == true

            if (isShowing) {
                val entry = floatingWindowManager.getEntry(sourceId)
                if (entry != null) {
                    ReplicaStateStore.saveHiddenEntry(sourceId, entry)
                }

                FloatingReplicaManager.dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.HIDDEN)
            } else {
                ReplicaTtlRegistry.removeBlockedInstance(sourceId)

                val existingEntry = ReplicaStateStore.getHiddenEntry(sourceId)
                if (existingEntry != null) {
                    showFloatingInternal(
                        context,
                        sourceId,
                        existingEntry.title,
                        existingEntry.text,
                        existingEntry.paramV2Raw,
                        existingEntry.picMap,
                        existingEntry.appName,
                        isLocked = false,
                        isRestoring = true,
                    )
                    ReplicaStateStore.removeHiddenEntry(sourceId)
                } else {
                    showFloatingInternal(
                        context,
                        sourceId,
                        title,
                        text,
                        paramV2Raw,
                        picMap,
                        appName,
                        isLocked = false,
                        isRestoring = true,
                    )
                }
            }
        }
    }

    /**
     * 移除某 sourceId 的全部浮窗条目（**纯窗口侧操作**，不含通道判定与通知关闭）。
     *
     * 通道判定与通知关闭已上移至 [FloatingReplicaManager.dismissBySourceInternal]（P2-7），
     * 本类自此只负责 overlay 窗口宿主与条目登记。
     *
     * @param removeMappings 条目移除后是否同时清空该 sourceId 的映射
     *   （原实现在「有 entryKeys」分支清映射、「无 entryKeys」分支不清，故由调用方决定）。
     */
    fun removeFloatingEntries(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason,
        entryKeys: List<String>?,
        removeMappings: Boolean,
    ) {
        if (entryKeys != null) {
            entryKeys.forEach { entryKey ->
                floatingWindowManager.removeEntry(entryKey, reason)
            }
            if (removeMappings) {
                ReplicaStateStore.removeSourceIdMappings(sourceId)
            }
        } else {
            floatingWindowManager.removeEntry(sourceId, reason)
        }
    }

    fun removeOverlayContainer() {
        runReplicaCatching(TAG, "移除浮窗容器") {
            val view = overlayView?.get()
            val wm = windowManager?.get()
            val lp = overlayLayoutParams

            if (view != null && wm != null && lp != null) {
                wm.removeView(view)

                overlayView = null
                overlayLayoutParams = null
                windowManager = null

                overlayLifecycleOwner?.let {
                    try {
                        it.onHide()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        overlayView = null
        overlayLayoutParams = null
        windowManager = null
        ReplicaStateStore.setOverlayView(null)
    }

    private fun addOrUpdateEntry(
        context: Context,
        key: String,
        summaryOnly: Boolean,
    ) {
        runReplicaCatching(TAG, "addOrUpdateEntry") {
            if (overlayView?.get() == null || windowManager?.get() == null || overlayLayoutParams == null) {
                runReplicaCatching(TAG, "创建浮窗容器") {
                    val appCtx = context.applicationContext
                    val wm =
                        appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                            ?: return@runReplicaCatching

                    val lifecycleOwner =
                        overlayLifecycleOwner ?: FloatingWindowLifecycleOwner().also {
                            overlayLifecycleOwner = it
                        }
                    try {
                        lifecycleOwner.onShow()
                    } catch (_: Exception) {
                    }

                    val density = context.resources.displayMetrics.density
                    val layoutParams =
                        WindowManager
                            .LayoutParams(
                                (FIXED_WIDTH_DP * density).toInt(),
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                PixelFormat.TRANSLUCENT,
                            ).apply {
                                gravity = Gravity.START or Gravity.TOP
                                x = ((context.resources.displayMetrics.widthPixels - (FIXED_WIDTH_DP * density).toInt()) / 2).coerceAtLeast(0)
                                y = 100
                            }

                    val composeContainer =
                        FloatingComposeContainer(context).apply {
                            val padding = (12 * density).toInt()
                            setPadding(padding, padding, padding, padding)
                            this.floatingWindowManager = this@FloatingReplicaWindowManager.floatingWindowManager
                            this.lifecycleOwner = lifecycleOwner
                            this.windowManager = wm
                            this.windowLayoutParams = layoutParams
                            this.onEntryClick = { entryKey -> onEntryClicked(entryKey) }
                            this.onContainerDragging = { }
                        }

                    var added = false
                    runReplicaCatching(TAG, "addView") {
                        wm.addView(composeContainer, layoutParams)
                        added = true
                    }
                    if (added) {
                        overlayView = WeakReference(composeContainer)
                        overlayLayoutParams = layoutParams
                        windowManager = WeakReference(wm)
                        ReplicaStateStore.setOverlayView(composeContainer)
                    }
                }
            }
        }
    }

    private fun onEntryClicked(key: String) {
        val entry = floatingWindowManager.getEntry(key)
        if (entry == null) {
            return
        }
        floatingWindowManager.toggleEntryExpanded(key)
    }
}
