package com.xzyht.notifyrelay.feature.notification.superisland.pipeline

import android.content.Context
import android.os.Build
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.FormattedSuperIslandData
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.NotificationGenerator
import com.xzyht.notifyrelay.feature.notification.superisland.notification.SuperIslandNotificationIds
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.replica.ReplicaNotificationCloser
import com.xzyht.notifyrelay.feature.notification.superisland.replica.ReplicaStateStore
import com.xzyht.notifyrelay.feature.notification.superisland.replica.runReplicaCatchingSuspend
import github.xzynine.superislandui.model.core.ParamV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger

/**
 * 超级岛三通道共享的「展示一跳」。
 *
 * **背景**：浮窗（原 `FloatingReplicaWindowManager.showFloatingInternal`）、
 * 普通通知（原 `FloatingReplicaNotificationManager.sendNotification`）、列表模式
 * （原 `FloatingReplicaListModeManager.sendListModeNotification`）原先各有一份约 100 行、
 * 逐段重复的展示管线（版本守卫 → 图片入库 → 竞态双守卫 → formatForDisplay → 标题三级回退
 * → 注入模式三连读 → migrateInjectionMode → 指纹 → canSkipRefresh → LU/复刻二路发送 → 记指纹）。
 *
 * **合一原则**：只把**逐段相同**的部分上移；三份实现之间的差异全部参数化为
 * [DisplayRequest] 的字段与钩子，语义逐段保留。通道各自的**前置守卫**与**超时调度方**
 * 仍留在各自薄壳内。
 *
 * 时序契约（`REFACTOR.md` §3.2）：
 * - 竞态双守卫（版本守卫 + 关闭复检）不可删减；
 * - 指纹**只在发送成功后**记录（失败留空，以便保活包重试）。
 */
internal object SuperIslandDisplayPipeline {
    /** 列表模式聚合通知的固定 ID（原 `FloatingReplicaListModeManager.LIST_MODE_NOTIFICATION_ID`）。 */
    const val LIST_MODE_NOTIFICATION_ID = 30_000

    /** 展示通道。仅用于推导 overrideNotificationId（超时调度仍由调用方薄壳负责）。 */
    enum class Channel {
        FLOATING,
        LIST,
        NOTIFICATION,
    }

    /** 通道推导的通知 ID 覆盖值：列表模式用固定聚合 ID，其余为 null（由 sourceId 推导）。 */
    fun overrideNotificationId(channel: Channel): Int? = if (channel == Channel.LIST) LIST_MODE_NOTIFICATION_ID else null

    /** 规范化后的展示内容，交给 [DisplayRequest.onContentReady] 钩子。 */
    class DisplayContent(
        val sourceId: String,
        val formattedData: FormattedSuperIslandData,
        val paramV2: ParamV2?,
        val displayTitle: String?,
        val displayText: String?,
        val summaryOnly: Boolean,
    )

    /**
     * 一次展示请求。默认值即「普通通知通道」的行为；各通道的差异点见各字段注释。
     *
     * @param tag 调用方薄壳的日志 TAG，保证各通道日志前缀与拆分前一致。
     * @param forceRefresh 是否强制刷新通知（列表模式切换/移除后展示下一条时为 true）。
     * @param isRestoring 恢复展示（浮窗隐藏后恢复）：整段系统通知分发被跳过（原 `else {}` 空分支）。
     * @param requireEntryExistedBefore canSkipRefresh 是否额外要求「浮窗条目此前已存在」（仅浮窗通道）。
     * @param titleFallback 标题三级回退的兜底值：普通通知为 `"未知"`，浮窗与列表为 null。
     * @param textFallback 正文三级回退的兜底值：同上。
     * @param preGuard 图片入库**前后**各调一次的前置守卫，返回 false 则中止
     *   （列表模式的「active 条目仍是本条目」判定，等价于原实现的两次 active 检查）。
     * @param extraGuard 竞态复检守卫，返回 true 则中止。
     *   浮窗/普通通知通道为 `ReplicaTtlRegistry.isSourceRecentlyClosed`（修复 nextVersion 被
     *   `computeIfAbsent` 重建导致的版本误判）；列表通道无此守卫（传 null，与原实现一致）。
     * @param onContentReady 内容就绪后、分发前的钩子（浮窗通道借此登记条目、创建 overlay
     *   并登记映射）；返回值为 `entryExistedBefore`，供 [requireEntryExistedBefore] 判定。
     * @param registerMappingBeforeSend 分发前是否登记 entryKey 映射
     *   （普通通知通道为 true；浮窗由 [onContentReady] 自行登记故为 false；列表为 false）。
     * @param registerMappingOnSendFailure 复刻通知发送失败（返回 null）时是否仍登记 entryKey 映射
     *   （浮窗/普通通知为 true——原实现在这条分支上也无条件调用 `addSourceIdMapping`；列表为 false）。
     * @param registerLiveUpdateMappingRegardlessOfSuccess Live Updates 发送结果是否**不影响**映射登记
     *   （浮窗/普通通知为 true：原实现无条件 `putNotificationId` + `addSourceIdMapping`；
     *   列表为 false：仅在成功时登记，失败则清指纹）。
     */
    class DisplayRequest(
        val sourceId: String,
        val title: String?,
        val text: String?,
        val paramV2Raw: String?,
        val picMap: Map<String, String>?,
        val appName: String?,
        val isLocked: Boolean = false,
        val channel: Channel,
        val tag: String,
        val forceRefresh: Boolean = false,
        val isRestoring: Boolean = false,
        val requireEntryExistedBefore: Boolean = false,
        val titleFallback: String? = "未知",
        val textFallback: String? = "未知",
        val preGuard: (() -> Boolean)? = null,
        val extraGuard: (suspend () -> Boolean)? = null,
        val onContentReady: (suspend (DisplayContent) -> Boolean)? = null,
        val registerMappingBeforeSend: Boolean = true,
        val registerMappingOnSendFailure: Boolean = true,
        val registerLiveUpdateMappingRegardlessOfSuccess: Boolean = true,
        /** canSkipRefresh 命中时的日志文案（三通道原文案不同，逐字保留）。 */
        val skipRefreshMessage: String = "内容无变更，跳过系统通知刷新",
        /** 竞态复检命中时的日志文案（浮窗「中止显示」/ 普通通知「中止发送」）。 */
        val abortMessage: String = "在异步发送期间被关闭，中止发送",
        /**
         * Live Updates 分支的异常是否向外传播。
         *
         * 浮窗 / 普通通知通道原实现以 `runReplicaCatchingSuspend(tag, "发送Live Updates复合通知")`
         * 包裹（吞异常，动作名为上方文案）；列表模式通道原实现**直接调用、不包裹**，
         * 异常会冒泡到外层的 `runReplicaCatchingSuspend(tag, "发送列表模式通知")`，
         * 从而跳过其后的超时任务调度。此开关逐字保留该差异。
         */
        val liveUpdateErrorsPropagate: Boolean = false,
        /** 吞异常时 Live Updates 分支的动作名（浮窗/普通通知均为该文案，与拆分前一致）。 */
        val liveUpdateActionName: String = "发送Live Updates复合通知",
    )

    /**
     * 执行一次展示。
     *
     * @return true 表示已越过全部守卫并完成内容规范化（调用方据此调度各自超时任务）。
     */
    internal suspend fun dispatch(
        context: Context,
        request: DisplayRequest,
    ): Boolean {
        val sourceId = request.sourceId

        // ① 版本守卫的起点（原三份实现均在此调用 nextVersion）
        val taskVersion = ReplicaStateStore.nextVersion(sourceId)

        // 列表模式的前置守卫之一：active 条目已不是本条目则中止（在图片入库前）
        if (request.preGuard?.invoke() == false) {
            return false
        }

        val internedPicMap =
            withContext(Dispatchers.IO) {
                SuperIslandImageStore.internAll(context, sourceId, request.picMap)
            }

        if (!ReplicaStateStore.isLatestVersion(sourceId, taskVersion)) {
            return false
        }
        // 列表模式的前置守卫之二：与 isLatestVersion 合成 `active != x || !isLatest` 的原判定
        if (request.preGuard?.invoke() == false) {
            return false
        }

        // ② 竞态复检：协程 nextVersion 可能在 dismissBySource 的 removeSourceIdMappings 之后执行，
        // 导致版本被 computeIfAbsent 重建、isLatestVersion 误判通过。
        if (request.extraGuard?.invoke() == true) {
            Logger.i(request.tag, "超级岛: sourceId=$sourceId ${request.abortMessage}")
            return false
        }

        val formattedData = SuperIslandDataFormatter.formatForDisplay(context, request.paramV2Raw, internedPicMap)
        val paramV2 = formattedData.paramV2

        val summaryOnly =
            when {
                paramV2?.business == "miui_flashlight" -> true
                request.paramV2Raw?.contains("miui_flashlight") == true -> true
                else -> false
            }

        // 标题/正文三级回退：入参 → highlightInfo → baseInfo（浮窗/列表无兜底，普通通知为「未知」）
        val displayTitle =
            request.title?.takeIf { it.isNotBlank() }
                ?: paramV2?.highlightInfo?.title?.takeIf { it.isNotBlank() }
                ?: paramV2?.baseInfo?.title?.takeIf { it.isNotBlank() }
                ?: request.titleFallback

        val displayText =
            request.text?.takeIf { it.isNotBlank() }
                ?: paramV2?.highlightInfo?.content?.takeIf { it.isNotBlank() }
                ?: paramV2?.baseInfo?.content?.takeIf { it.isNotBlank() }
                ?: request.textFallback

        val content =
            DisplayContent(
                sourceId = sourceId,
                formattedData = formattedData,
                paramV2 = paramV2,
                displayTitle = displayTitle,
                displayText = displayText,
                summaryOnly = summaryOnly,
            )

        // ③ 钩子：浮窗通道在此登记条目（含 overlay 创建）并登记映射，返回 entryExistedBefore
        val entryExistedBefore = request.onContentReady?.invoke(content) ?: false

        if (request.registerMappingBeforeSend) {
            ReplicaStateStore.addSourceIdMapping(sourceId, sourceId)
        }

        val isProgressType = SuperIslandDataFormatter.isProgressType(paramV2)

        // 注入模式：超级岛模式优先于 Live Updates 模式（对齐媒体类型的既有分流范式）。
        // 超级岛模式下，即便含 progressInfo 也走超级岛通道；
        // 仅在「Live Updates 注入且非超级岛」时保留现有 Live Updates 通道。
        val superIslandMode = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(context)
        val liveUpdatesMode = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)
        val injectionModeOrdinal = SuperIslandConfigUtils.getSpecInjectionMode(context).ordinal

        if (!request.isRestoring) {
            // 注入模式变化时先取消旧通知并清理旧映射，再按新模式发送
            ReplicaNotificationCloser.migrateInjectionModeIfChanged(context, sourceId, injectionModeOrdinal)

            // 内容与上次成功发出的通知一致且通知仍在展示时，跳过系统通知刷新（不调用 notify）。
            // （浮窗通道另由条目 addOrUpdateEntry 重置内部撤回计时器。）
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
                !request.forceRefresh &&
                    (!request.requireEntryExistedBefore || entryExistedBefore) &&
                    !previousNotificationIds.isNullOrEmpty() &&
                    ReplicaStateStore.isAnyNotificationActive(context, previousNotificationIds) &&
                    fingerprint == ReplicaStateStore.getNotificationFingerprint(sourceId)

            val overrideNotificationId = overrideNotificationId(request.channel)

            if (canSkipRefresh) {
                Logger.i(request.tag, "超级岛: ${request.skipRefreshMessage}: sourceId=$sourceId")
            } else if (liveUpdatesMode && !superIslandMode && isProgressType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                // 列表模式通道原实现直接调用（不包裹），异常冒泡到外层 "发送列表模式通知"
                val showLiveUpdateBlock: suspend () -> Unit = {
                    LiveUpdatesNotificationManager.initialize(context)
                    val success =
                        LiveUpdatesNotificationManager.showLiveUpdate(
                            sourceId,
                            displayTitle,
                            displayText,
                            request.appName,
                            formattedData,
                            overrideNotificationId = overrideNotificationId,
                        )
                    val liveUpdateNotificationId = overrideNotificationId ?: SuperIslandNotificationIds.liveUpdates(sourceId)
                    if (success) {
                        ReplicaStateStore.putNotificationId(sourceId, liveUpdateNotificationId)
                        ReplicaStateStore.addSourceIdMapping(sourceId, sourceId, liveUpdateNotificationId)
                        // 仅在确认发出成功后记录指纹，发送异常被吞时留空，避免后续保活包被误跳过
                        ReplicaStateStore.setNotificationFingerprint(sourceId, fingerprint)
                    } else if (request.registerLiveUpdateMappingRegardlessOfSuccess) {
                        ReplicaStateStore.putNotificationId(sourceId, liveUpdateNotificationId)
                        ReplicaStateStore.addSourceIdMapping(sourceId, sourceId, liveUpdateNotificationId)
                    } else {
                        ReplicaStateStore.removeNotificationFingerprint(sourceId)
                    }
                }
                if (request.liveUpdateErrorsPropagate) {
                    showLiveUpdateBlock()
                } else {
                    runReplicaCatchingSuspend(request.tag, request.liveUpdateActionName) {
                        showLiveUpdateBlock()
                    }
                }
            } else {
                val notificationId =
                    NotificationGenerator.sendReplicaNotification(
                        context = context,
                        key = sourceId,
                        title = displayTitle,
                        text = displayText,
                        appName = request.appName,
                        paramV2 = formattedData.paramV2,
                        paramV2Raw = formattedData.paramV2Raw,
                        picMap = formattedData.resolvedPicMap,
                        sourceId = sourceId,
                        floatingWindowManager = FloatingReplicaWindowManager.getFloatingWindowManager(),
                        overrideNotificationId = overrideNotificationId,
                    )
                if (notificationId != null) {
                    ReplicaStateStore.addSourceIdMapping(sourceId, sourceId, notificationId)
                    // 仅在确认发出成功后记录指纹，失败时留空以便下次保活包重试
                    ReplicaStateStore.setNotificationFingerprint(sourceId, fingerprint)
                } else if (request.registerMappingOnSendFailure) {
                    ReplicaStateStore.addSourceIdMapping(sourceId, sourceId, null)
                }
            }
        }

        return true
    }
}
