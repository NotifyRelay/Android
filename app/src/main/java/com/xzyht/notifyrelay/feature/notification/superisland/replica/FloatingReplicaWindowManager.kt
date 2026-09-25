package com.xzyht.notifyrelay.feature.notification.superisland.replica

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.net.toUri
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingComposeContainer
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowLifecycleOwner
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.list.FloatingReplicaListModeManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandNotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun isFloatingWindowEnabled(context: Context): Boolean = SuperIslandConfigUtils.isFloatingWindowEnabled(context)

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
                    val taskVersion = ReplicaStateStore.nextVersion(sourceId)

                    if (overlayLifecycleOwner == null) {
                        overlayLifecycleOwner = FloatingWindowLifecycleOwner()
                    }

                    val internedPicMap =
                        withContext(Dispatchers.IO) {
                            SuperIslandImageStore.internAll(context, sourceId, picMap)
                        }

                    if (!ReplicaStateStore.isLatestVersion(sourceId, taskVersion)) {
                        return@runReplicaCatchingSuspend
                    }

                    // 竞态守卫：协程 nextVersion 可能在 dismissBySource 的 removeSourceIdMappings 之后执行，
                    // 导致版本被 computeIfAbsent 重建、isLatestVersion 误判通过。
                    // 此处复检 isSourceRecentlyClosed（dismissBySource 已 markSourceClosed），命中即中止。
                    if (ReplicaTtlRegistry.isSourceRecentlyClosed(sourceId)) {
                        Logger.i(TAG, "超级岛: sourceId=$sourceId 在异步发送期间被关闭，中止显示")
                        return@runReplicaCatchingSuspend
                    }

                    val formattedData = SuperIslandDataFormatter.formatForDisplay(context, paramV2Raw, internedPicMap)
                    val paramV2 = formattedData.paramV2

                    val summaryOnly =
                        when {
                            paramV2?.business == "miui_flashlight" -> true
                            paramV2Raw?.contains("miui_flashlight") == true -> true
                            else -> false
                        }

                    val entryKey = sourceId

                    val displayTitle =
                        title?.takeIf { it.isNotBlank() }
                            ?: paramV2?.highlightInfo?.title?.takeIf { it.isNotBlank() }
                            ?: paramV2?.baseInfo?.title?.takeIf { it.isNotBlank() }

                    val displayText =
                        text?.takeIf { it.isNotBlank() }
                            ?: paramV2?.highlightInfo?.content?.takeIf { it.isNotBlank() }
                            ?: paramV2?.baseInfo?.content?.takeIf { it.isNotBlank() }

                    // 记录更新前浮窗条目是否已存在：存在说明系统通知已发出过，保活包无变更时可跳过通知刷新
                    val entryExistedBefore = floatingWindowManager.getEntry(entryKey) != null

                    floatingWindowManager.addOrUpdateEntry(
                        key = entryKey,
                        paramV2 = paramV2,
                        paramV2Raw = formattedData.paramV2Raw,
                        picMap = formattedData.resolvedPicMap,
                        isExpanded = if (isLocked) false else !summaryOnly,
                        summaryOnly = summaryOnly,
                        business = paramV2?.business,
                        title = displayTitle,
                        text = displayText,
                        appName = appName,
                    )

                    ReplicaStateStore.addSourceIdMapping(sourceId, entryKey)

                    addOrUpdateEntry(context, entryKey, summaryOnly)

                    val isProgressType = SuperIslandDataFormatter.isProgressType(paramV2)

                    // 注入模式：超级岛模式优先于 Live Updates 模式（对齐媒体类型的既有分流范式）。
                    // 超级岛模式下，即便含 progressInfo 也走超级岛通道；
                    // 仅在「Live Updates 注入且非超级岛」时保留现有 Live Updates 通道。
                    val superIslandMode = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(context)
                    val liveUpdatesMode = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)
                    val injectionModeOrdinal = SuperIslandConfigUtils.getSpecInjectionMode(context).ordinal

                    if (!isRestoring) {
                        // 注入模式变化时先取消旧通知并清理旧映射，再按新模式发送
                        ReplicaNotificationCloser.migrateInjectionModeIfChanged(context, sourceId, injectionModeOrdinal)

                        // 内容与上次成功发出的通知一致且通知仍在展示时，跳过系统通知刷新（不调用 notify），
                        // 仅保留上方 addOrUpdateEntry 对内部撤回计时器（autoDismiss）的重置。
                        // 指纹包含注入模式：模式变化时指纹随之变化，不会被误判为「内容无变更」。
                        val fingerprint =
                            ReplicaStateStore.computeNotificationFingerprint(
                                displayTitle,
                                displayText,
                                formattedData.paramV2Raw,
                                formattedData.resolvedPicMap,
                                injectionModeOrdinal,
                            )
                        val previousNotificationIds = ReplicaStateStore.getNotificationIdsBySourceId(sourceId)
                        val canSkipRefresh =
                            entryExistedBefore &&
                                !previousNotificationIds.isNullOrEmpty() &&
                                ReplicaStateStore.isAnyNotificationActive(context, previousNotificationIds) &&
                                fingerprint == ReplicaStateStore.getNotificationFingerprint(sourceId)

                        if (canSkipRefresh) {
                            Logger.i(TAG, "超级岛: 内容无变更，跳过系统通知刷新，仅重置内部撤回计时器: sourceId=$sourceId")
                        } else if (liveUpdatesMode && !superIslandMode && isProgressType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                            runReplicaCatchingSuspend(TAG, "发送Live Updates复合通知") {
                                LiveUpdatesNotificationManager.initialize(context)
                                val success =
                                    LiveUpdatesNotificationManager.showLiveUpdate(
                                        sourceId,
                                        displayTitle,
                                        displayText,
                                        appName,
                                        formattedData,
                                    )
                                val liveUpdateNotificationId = SuperIslandNotificationIds.liveUpdates(sourceId)
                                ReplicaStateStore.putNotificationId(entryKey, liveUpdateNotificationId)
                                ReplicaStateStore.addSourceIdMapping(sourceId, entryKey, liveUpdateNotificationId)
                                // 仅在确认发出成功后记录指纹，发送异常被吞时留空，避免后续保活包被误跳过
                                if (success) {
                                    ReplicaStateStore.setNotificationFingerprint(sourceId, fingerprint)
                                }
                            }
                        } else {
                            val notificationId = NotificationGenerator.sendReplicaNotification(context, entryKey, displayTitle, displayText, appName, formattedData.paramV2, formattedData.paramV2Raw, formattedData.resolvedPicMap, sourceId, floatingWindowManager)
                            ReplicaStateStore.addSourceIdMapping(sourceId, entryKey, notificationId)
                            if (notificationId != null) {
                                ReplicaStateStore.setNotificationFingerprint(sourceId, fingerprint)
                            }
                        }
                    }
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

                dismissBySourceInternal(sourceId, FloatingWindowManager.RemovalReason.HIDDEN)
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

    fun dismissBySourceInternal(
        sourceId: String,
        reason: FloatingWindowManager.RemovalReason = FloatingWindowManager.RemovalReason.REMOTE,
    ) {
        runReplicaCatching(TAG, "按来源关闭浮窗") {
            if (ReplicaTtlRegistry.isSourceRecentlyClosedWithinMinute(sourceId)) {
                return@runReplicaCatching
            }

            if (reason != FloatingWindowManager.RemovalReason.HIDDEN) {
                ReplicaTtlRegistry.markSourceClosed(sourceId)
                // HIDDEN 保留缓存：隐藏是可恢复的临时状态，内容仍然活跃、切换通道时仍应展示
                ReplicaDisplayCache.remove(sourceId)
            }

            ReplicaStateStore.cancelTimeoutJob(sourceId)

            NotificationGenerator.stopScrollUpdate(sourceId)

            val ctx = ReplicaStateStore.getAppContext()
            if (ctx != null && !isFloatingWindowEnabled(ctx) && SuperIslandConfigUtils.isNotificationListMode(ctx)) {
                FloatingReplicaListModeManager.dismissFromList(ctx, sourceId)
                if (reason == FloatingWindowManager.RemovalReason.REMOTE || reason == FloatingWindowManager.RemovalReason.TIMEOUT) {
                    ReplicaTtlRegistry.removeBlockedInstance(sourceId)
                }
                return@runReplicaCatching
            }

            val floatingEnabled = if (ctx != null) isFloatingWindowEnabled(ctx) else true

            val notificationIdsBefore = ReplicaStateStore.getNotificationIdsBySourceId(sourceId)
            val entryKeys = ReplicaStateStore.getSourceIdEntryKeys(sourceId)

            if (floatingEnabled) {
                if (entryKeys != null) {
                    entryKeys.forEach { entryKey ->
                        floatingWindowManager.removeEntry(entryKey, reason)
                    }
                    ReplicaStateStore.removeSourceIdMappings(sourceId)
                } else {
                    floatingWindowManager.removeEntry(sourceId, reason)
                }
            }

            FloatingReplicaNotificationManager.closeNotificationsBySourceId(sourceId, reason, notificationIdsBefore, entryKeys, ctx)
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
