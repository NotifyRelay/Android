package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import github.xzynine.superislandui.floating.smallisland.left.aPicKey
import github.xzynine.superislandui.floating.smallisland.right.BComponent
import github.xzynine.superislandui.floating.smallisland.right.BSameWidthDigitInfo
import github.xzynine.superislandui.floating.smallisland.right.bPicKey
import github.xzynine.superislandui.model.core.ParamV2
import notifyrelay.base.util.Logger

/**
 * 非媒体类型复刻通知构建。
 *
 * 负责：计时器判定与标题改写、chronometer 设置、进度类型分流。
 * 进度+LiveUpdates 分支走内联图标下载（不带胶囊字段注入），其余分支走
 * [ReplicaSmallIconInjector.buildCapsuleCompatibleNotificationWithIconInjection]（带胶囊字段注入）。
 * 两路图标逻辑重复但不可随意统一。
 */
internal object GeneralReplicaNotifier {
    private const val TAG = "超级岛通知生成"

    /**
     * 是否为「计时器通知」：小岛 B 区携带 timerInfo，且整条数据**不含进度组件**。
     *
     * 进度组件（progressInfo / multiProgressInfo）优先：像「多节点进度」这类样本会在
     * `param_island.bigIslandArea.sameWidthDigitInfo` 里同时带 timerInfo，
     * 若只用 B 区判定，整条通知会被当成计时器（标题被改写成「正计时中」、启用 chronometer），
     * 实际应以进度组件为主。
     */
    fun isTimerNotification(
        paramV2: ParamV2?,
        bComponent: BComponent?,
    ): Boolean =
        !SuperIslandDataFormatter.isProgressType(paramV2) &&
            bComponent is BSameWidthDigitInfo &&
            bComponent.timer != null

    suspend fun build(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String?,
        text: String?,
        appName: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        paramV2: ParamV2?,
        key: String,
    ): Notification {
        // 使用已解析的 paramV2 中的组件数据，避免重复解析
        val bigIslandArea = paramV2?.paramIsland?.bigIslandArea
        val aComponent = bigIslandArea?.aComponent
        val bComponent = bigIslandArea?.bComponent

        // 判断是否为计时器类型（包括运行中和暂停状态）；进度组件优先，
        // 避免小岛 B 区带 timerInfo 的进度数据（如「多节点进度」样本）被当作计时器处理。
        val isTimerType = isTimerNotification(paramV2, bComponent)
        val timerInfo = if (isTimerType && bComponent is BSameWidthDigitInfo) bComponent.timer else null

        // 计时器通知的标题和内容设置
        // 标题显示状态，内容显示应用名，时间流逝由chronometer自动处理
        var timerTitle: String = title ?: appName ?: "超级岛通知"
        var timerContent: String = text ?: ""
        timerInfo?.let {
            when (it.timerType) {
                -2 -> {
                    timerTitle = "暂停"
                    timerContent = appName ?: "计时器"
                }

                -1 -> {
                    timerTitle = "倒计时中"
                    timerContent = appName ?: "计时器"
                }

                1 -> {
                    timerTitle = "正计时中"
                    timerContent = appName ?: "秒表"
                }

                2 -> {
                    timerTitle = "暂停"
                    timerContent = appName ?: "秒表"
                }

                else -> {
                    timerTitle = title ?: appName ?: "超级岛通知"
                    timerContent = text ?: ""
                }
            }
        }

        // 判断是否为正在运行的计时器类型（用于chronometer自动更新）
        val isRunningTimer =
            timerInfo != null && (timerInfo.timerType == -1 || timerInfo.timerType == 1)

        // 构建基础通知，调整属性使其更接近实际超级岛通知
        builder
            .setContentTitle(timerTitle)
            .setContentText(timerContent)
            .setSmallIcon(android.R.drawable.stat_notify_more) // 使用系统默认图标
            // 调整为与实际超级岛通知一致的属性
            .setOngoing(true) // 实际通知通常是持续的
            // 提高优先级到最高，与原始通知一致
            .setPriority(NotificationCompat.PRIORITY_MAX)
            // 计时器需要显示时间以支持chronometer自动流更新
            .setShowWhen(isRunningTimer)
            // 计时器需要使用chronometer功能
            .setUsesChronometer(isRunningTimer)
            .setWhen(System.currentTimeMillis()) // 设置时间
            .setOnlyAlertOnce(true) // 只提示一次
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 公开可见

        // 对于计时器类通知，添加计时器相关字段
        if (title?.contains("计时") == true || title?.contains("秒表") == true) {
            if (bComponent is BSameWidthDigitInfo && bComponent.timer != null) {
                // 根据timerType设置计时模式
                val timer = bComponent.timer
                val timerType = timer?.timerType
                // timerType: -2倒计时暂停，-1倒计时开始，0默认正计时开始，2正计时暂停
                // 只对正在进行中的计时器启用自动流更新
                if (timerType == -1 || timerType == 1) {
                    val isCountDown = timerType < 0

// 使用NotificationCompat的计时器功能
                    if (isCountDown) {
                        // 倒计时：计算剩余时间并设置chronometer自动倒计时
                        val now = System.currentTimeMillis()
                        val remaining = timer.timerWhen - now
                        remaining.let {
                            if (it > 0) {
                                // 对于倒计时，设置chronometer自动倒计时
                                builder.setUsesChronometer(true)
                                builder.setChronometerCountDown(true)
                                builder.setShowWhen(true) // 确保显示时间
                                // 设置倒计时的终点时间
                                timer.let { builder.setWhen(it.timerWhen) }
                            }
                        }
                    } else {
                        // 正计时：使用timerWhen作为起点
                        builder.setUsesChronometer(true)
                        builder.setChronometerCountDown(false)
                        builder.setShowWhen(true) // 确保显示时间
                        // 设置正计时的起点时间
                        timer.let { builder.setWhen(it.timerWhen) }
                    }
                }
            }
        }

        // 检查是否为进度类型通知，如果是，则可能已经通过 LiveUpdatesNotificationManager 处理
        val isProgressType = SuperIslandDataFormatter.isProgressType(paramV2)
        // 注入模式：本方法即「超级岛通道」构建器；仅当进度类型确实由 Live Updates 处理时才跳过胶囊注入。
        // 超级岛模式下，即便含 progressInfo 也走到这里，必须正常添加胶囊兼容字段与图标注入。
        val liveUpdatesMode = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)

        // 构建通知
        val notification =
            if (!isProgressType || !liveUpdatesMode) {
                // 非进度类型通知，或超级岛模式下（含进度类型）的通知：添加胶囊兼容字段并注入图标
                val builtNotification =
                    ReplicaSmallIconInjector.buildCapsuleCompatibleNotificationWithIconInjection(
                        context,
                        builder,
                        title,
                        text,
                        appName,
                        picMap,
                        paramV2Raw,
                        aComponent,
                        bComponent,
                        isTimerType,
                    )
                builtNotification
            } else {
                // 进度类型通知，已经通过 LiveUpdatesNotificationManager 处理，不重复添加胶囊兼容字段
                Logger.i(TAG, "超级岛 进度类型通知，已通过 LiveUpdatesNotificationManager 处理，不重复添加胶囊兼容字段")
                // 构建通知
                val builtNotification = builder.build()
                // 尝试从A/B 区数据中获取图标或生成位图
                var smallIconBitmap: android.graphics.Bitmap? = null

                // 提取 A/B 区数据（使用已解析的组件）
                val aPicKey = aComponent.aPicKey
                val bPicKey = bComponent.bPicKey

                // 处理图标
                // 优先使用 A 区图标或B区图标
                val picKeyToUse = aPicKey ?: bPicKey
                if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
                    val picUrl = picMap[picKeyToUse]
                    if (!picUrl.isNullOrBlank()) {
                        // 异步下载图标
                        val bitmap = ReplicaSmallIconInjector.downloadBitmap(context, picUrl)
                        if (bitmap != null) {
                            smallIconBitmap = bitmap
                        }
                    }
                }

                // 如果没有 A 区图标或B区图标，再使用应用图标
                if (smallIconBitmap == null) {
                    val appIconKey = "miui.focus.pic_app_icon"
                    if (!picMap.isNullOrEmpty() && picMap.containsKey(appIconKey)) {
                        val appIconUrl = picMap[appIconKey]
                        if (!appIconUrl.isNullOrBlank()) {
                            // 同步下载应用图标
                            val bitmap = ReplicaSmallIconInjector.downloadBitmap(context, appIconUrl)
                            if (bitmap != null) {
                                smallIconBitmap = bitmap
                            }
                        }
                    }
                }

                // 注入小图标
                if (smallIconBitmap != null) {
                    ReplicaSmallIconInjector.injectSmallIconWithoutCache(builtNotification, smallIconBitmap)
                }

                builtNotification
            }

        return notification
    }
}
