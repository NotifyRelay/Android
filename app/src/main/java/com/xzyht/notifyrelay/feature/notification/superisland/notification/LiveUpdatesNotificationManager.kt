package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.HtmlCompat
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.data.SuperIslandStructuredDataHelper
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.FormattedSuperIslandData
import com.xzyht.notifyrelay.feature.notification.superisland.formatter.SuperIslandDataFormatter
import com.xzyht.notifyrelay.feature.notification.superisland.intent.NotificationIntentFactory
import github.xzynine.superislandui.model.core.ParamV2
import notifyrelay.base.util.Logger

object LiveUpdatesNotificationManager {
    internal const val TAG = "超级岛进度类型"

    /** 通知渠道 ID，唯一来源见 [NotificationGenerator.NOTIFICATION_CHANNEL_ID]。 */
    const val CHANNEL_ID = NotificationGenerator.NOTIFICATION_CHANNEL_ID
    private const val CHANNEL_NAME = "超级岛复刻"

    /**
     * 耦合逻辑说明
     * 1. 浮窗功能与通知点击事件的耦合
     *    - 当浮窗功能开启时，为通知设置点击意图和删除意图
     *    - 点击意图.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING
     *    - 删除意图.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION
     *    - 这些意图会触发 NotificationBroadcastReceiver 中的相应处理逻辑
     *
     * 2. 通知与浮窗的去耦合
     *    - 通过 SUPER_ISLAND_FLOATING_WINDOW_KEY 开关控制浮窗功能
     *    - 浮窗功能关闭时，不设置与浮窗关联的通知点击和关闭意图
     *    - 浮窗功能关闭时，仅创建基础通知，不添加与浮窗相关的功能
     */

    internal lateinit var notificationManager: NotificationManager
        private set
    internal lateinit var appContext: Context
        private set

    /**
     * 清空图标缓存（供公平运行内存回调使用）。
     */
    fun clearIconCache() {
        LiveUpdatesIconLoader.clearIconCache()
    }

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return
        }
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            )
        notificationManager.createNotificationChannel(channel)
    }

    fun showLiveUpdate(
        sourceId: String,
        title: String?,
        text: String?,
        appName: String?,
        formattedData: FormattedSuperIslandData,
        overrideNotificationId: Int? = null,
    ): Boolean {
        // 版本门控 + 开关校验（与原实现一致，位于 try 之外，异常继续向上传播）
        if (!checkEligibility()) {
            return false
        }

        try {
            val notificationId = resolveNotificationId(sourceId, overrideNotificationId)

            val paramV2 = formattedData.paramV2

            // 调试picMap内容
            if (formattedData.resolvedPicMap.isNotEmpty()) {
                Logger.d(TAG, "收到picMap，包含 ${formattedData.resolvedPicMap.size} 个图标资源 ${formattedData.resolvedPicMap.keys}")
            } else {
                Logger.d(TAG, "picMap为空或null")
            }

            // 仅处理进度类型通知
            if (!SuperIslandDataFormatter.isProgressType(paramV2)) {
                Logger.i(TAG, "非进度类型通知，跳过处理 $sourceId")
                return false
            }

            val notification =
                buildInitialNotification(
                    sourceId = sourceId,
                    title = title,
                    text = text,
                    appName = appName,
                    formattedData = formattedData,
                    paramV2 = paramV2,
                    notificationId = notificationId,
                )

            postNotification(
                sourceId = sourceId,
                notificationId = notificationId,
                paramV2 = paramV2,
                picMap = formattedData.resolvedPicMap,
                notification = notification,
            )
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "发送Live Update通知失败: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 计算通知 ID：优先使用调用方指定的 ID，否则由 sourceId 推导。
     *
     * ID 推导统一委托 [SuperIslandNotificationIds.liveUpdates]，其基址与复刻通道保持不重叠
     * （原实现 `hash+10000` 与复刻通道 `hash+20000` 因 16 位哈希区间达 65535 而实际相交）。
     */
    private fun resolveNotificationId(
        sourceId: String,
        overrideNotificationId: Int?,
    ): Int = overrideNotificationId ?: SuperIslandNotificationIds.liveUpdates(sourceId)

    /**
     * 版本门控与注入开关校验。
     *
     * 只包含原实现中位于 `try` 之外的两条语句，故本函数同样在 `try` 之外调用，
     * 异常继续向上传播（与原实现一致）。
     *
     * 注意：`canUseLiveUpdates()` 为 false 时**只记录日志、不中断流程**
     * （与原实现一致：即使不可用也继续尝试发送通知）。
     */
    private fun checkEligibility(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return false
        }

        // 验证规范信息注入开关状态，确保至少有一种开启
        SuperIslandConfigUtils.validateSpecInjectionSwitches(appContext)

        // 检查是否可以使用Live Updates，但即使不可用也继续尝试发送通知
        if (!canUseLiveUpdates()) {
            Logger.w(TAG, "Live Updates不可用 - 请检查权限和设置，但仍尝试发送通知")
        }

        return true
    }

    /**
     * 构建首次展示的通知（基础样式 + 缓存图标 + 关键文本 + 操作按钮 + 进度样式 + 结构化数据）。
     */
    private fun buildInitialNotification(
        sourceId: String,
        title: String?,
        text: String?,
        appName: String?,
        formattedData: FormattedSuperIslandData,
        paramV2: ParamV2?,
        notificationId: Int,
    ): Notification? {
        // 检查浮窗功能是否开启；列表模式（浮窗关闭时）也需要点击意图用于切换。
        // 判定统一走 SuperIslandConfigUtils.needClickIntent（D3：唯一判定处）
        val needClickIntent = SuperIslandConfigUtils.needClickIntent(appContext)

        // 创建删除意图，用于处理用户移除通知时关闭浮窗
        val deleteIntent =
            if (needClickIntent) {
                NotificationIntentFactory.createDeleteIntent(appContext, notificationId)
            } else {
                null
            }

        // 创建点击意图，用于处理用户点击通知时切换浮窗或切换列表
        val contentIntent =
            if (needClickIntent) {
                NotificationIntentFactory.createPendingContentIntent(
                    context = appContext,
                    notificationId = notificationId,
                    sourceId = sourceId,
                    title = title,
                    text = text,
                    appName = appName,
                    paramV2Raw = formattedData.paramV2Raw,
                )
            } else {
                null
            }

        // 构建基础通知
        val notificationBuilder =
            buildBaseNotification()
                .setContentTitle(title ?: appName ?: "超级岛通知")
                .setContentText(text ?: "未知")
                .setSmallIcon(R.drawable.stat_notify_more)

        // 在浮窗或列表模式下设置删除意图和点击意图
        if (needClickIntent) {
            notificationBuilder
                .setDeleteIntent(deleteIntent)
                .setContentIntent(contentIntent)
        }

        // 尝试使用前进指示器图标作为小图标
        if (formattedData.resolvedPicMap.isNotEmpty()) {
            // 找到有效的前进图标
            val possibleIconKeys =
                listOf(
                    paramV2?.progressInfo?.picForward,
                    paramV2?.multiProgressInfo?.picForward,
                    paramV2?.multiProgressInfo?.picForwardBox,
                    paramV2?.progressInfo?.picMiddle,
                    paramV2?.multiProgressInfo?.picMiddle,
                )

            val iconKey =
                possibleIconKeys.firstOrNull { key ->
                    key != null && formattedData.resolvedPicMap.containsKey(key)
                }

            if (iconKey != null) {
                val iconUrl = formattedData.resolvedPicMap[iconKey]
                if (iconUrl != null) {
                    // 尝试从缓存加载图标
                    val cachedBitmap = LiveUpdatesIconLoader.iconCache.get(iconUrl)
                    if (cachedBitmap != null) {
                        notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(cachedBitmap))
                    }
                }
            }
        }

        // 直接设置状态栏关键文本，不再使用反解析后的标题和内容
        // 使用处理后的标题和内容
        val (processedTitle, processedContent) =
            processHtmlText(
                paramV2?.baseInfo?.title,
                paramV2?.baseInfo?.content,
            )

        // 设置状态栏关键文本，优先使用处理后的标题（预计时间），然后是处理后的内容
        val shortText =
            when {
                processedTitle.isNotEmpty() -> processedTitle
                processedContent.isNotEmpty() -> processedContent
                title?.isNotEmpty() == true -> title
                appName?.isNotEmpty() == true -> appName
                else -> " "
            }
        notificationBuilder.setShortCriticalText(shortText)

        // 添加操作按钮（如果有）
        paramV2?.actions?.let {
            for (action in it) {
                try {
                    notificationBuilder.addAction(
                        NotificationCompat.Action
                            .Builder(
                                null,
                                action.actionTitle ?: "操作",
                                null,
                            ).build(),
                    )
                } catch (e: Exception) {
                    Logger.w(TAG, "添加操作按钮失败: ${e.message}")
                }
            }
        }

        // 构建最终通知 - 仅处理进度类通知
        // 其他类型的通知（如聊天、系统等）将直接使用基础通知
        val finalBuilder =
            paramV2?.let { LiveUpdatesProgressStyleBuilder.buildProgressStyleNotification(notificationBuilder, it, formattedData.resolvedPicMap) }

        // 添加超级岛相关的结构化数据
        finalBuilder?.let { addSuperIslandStructuredData(it, paramV2, formattedData.paramV2Raw, formattedData.resolvedPicMap) }

        return finalBuilder?.build()
    }

    /**
     * 校验可提升特性后发送通知，并触发异步图标加载以获得二次更新。
     *
     * 注意：`hasPromotableCharacteristics()` 的校验**只记日志、不影响流程**
     * （与原实现一致，不要改成提前 return）。
     */
    private fun postNotification(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2?,
        picMap: Map<String, String>,
        notification: Notification?,
    ) {
        // 验证通知是否具有可提升特性
        try {
            val hasPromotable = notification?.hasPromotableCharacteristics()
            hasPromotable?.let {
                if (!it) {
                    Logger.w(TAG, "通知不具有可提升特性 - Live Updates可能无法正常显示")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "检查通知可提升特性失败 ${e.message}")
        }

        // 发送通知
        notificationManager.notify(notificationId, notification)

        // 异步加载图标并更新通知，确保图标正确显示
        LiveUpdatesIconLoader.loadIconsAndUpdateNotification(sourceId, notificationId, paramV2, picMap)
    }

    internal fun buildBaseNotification(): NotificationCompat.Builder {
        val builder =
            NotificationCompat
                .Builder(appContext, CHANNEL_ID)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                // 添加Live Updates所需的属性，参照参考文档
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                // 直接调用setRequestPromotedOngoing，不再使用回调
                .setRequestPromotedOngoing(true)

        return builder
    }

    /**
     * **Live Updates 通知撤回的唯一入口**（D5/D7 收敛）。
     *
     * 原先「撤回 Live Updates」散落在 4 处，其中两处（`closeNotificationsBySourceId`、
     * `migrateInjectionModeIfChanged`）还在调用本方法之外**再直接 `notificationManager.cancel(id)`**
     * 一次——本方法取消的正是同一个推导 id（[SuperIslandNotificationIds.liveUpdates]），
     * 属重复操作。现全部收敛到此处：调方只需调用 [dismiss]，不再自行 cancel 该 id。
     *
     * 行为与拆分前逐字一致：版本门控 →（未初始化则尝试初始化）→ 取消推导 id；
     * 失败只记日志、不抛出。
     *
     * @param context 可选的用于初始化 [notificationManager] 的 Context；
     *   传入时先 [initialize]（幂等）。不传则沿用已初始化的单例状态。
     * @return true 表示已实际执行取消（或无需取消），false 表示版本不满足而跳过。
     */
    fun dismiss(
        sourceId: String,
        context: Context? = null,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return false
        }
        try {
            if (context != null) {
                // initialize 内部同样做版本门控；此处已在上方确认版本满足
                initialize(context)
            } else if (!::notificationManager.isInitialized) {
                // 检查notificationManager是否已初始化，如果没有则尝试初始化
                Logger.w(TAG, "LiveUpdatesNotificationManager未初始化，尝试初始化")
                // 如果有appContext，则使用appContext初始化
                if (::appContext.isInitialized) {
                    initialize(appContext)
                } else {
                    Logger.w(TAG, "无法初始化LiveUpdatesNotificationManager，缺少上下文")
                    return false
                }
            }
            val notificationId = SuperIslandNotificationIds.liveUpdates(sourceId)
            notificationManager.cancel(notificationId)
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "取消Live Update通知失败: ${e.message}")
            return false
        }
    }

    fun canUseLiveUpdates(): Boolean {
        // 检查设备是否支持Live Updates
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "canUseLiveUpdates: SDK版本 ${Build.VERSION.SDK_INT} < BAKLAVA")
            return false
        }

        // 检查notificationManager是否已初始化
        if (!::notificationManager.isInitialized) {
            Logger.e(TAG, "canUseLiveUpdates: notificationManager未初始化")
            return false
        }

        // 检查是否可以发布提升通知 - 使用反射避免编译错误
        try {
            // 先检查方法是否存在
            val canPostPromotedNotificationsMethod = notificationManager.javaClass.getMethod("canPostPromotedNotifications")
            val result = canPostPromotedNotificationsMethod.invoke(notificationManager) as Boolean
            return result
        } catch (e: NoSuchMethodException) {
            Logger.w(TAG, "canUseLiveUpdates: 未找到canPostPromotedNotifications方法 - 设备可能不支持Live Updates")
            // 方法不存在，可能设备不支持，返回false
            return false
        } catch (e: SecurityException) {
            Logger.w(TAG, "canUseLiveUpdates: 检查canPostPromotedNotifications时发生SecurityException: ${e.message}")
            return false
        } catch (e: IllegalAccessException) {
            Logger.w(TAG, "canUseLiveUpdates: 检查canPostPromotedNotifications时发生IllegalAccessException: ${e.message}")
            return false
        } catch (e: Exception) {
            Logger.w(TAG, "canUseLiveUpdates: 检查canPostPromotedNotifications时发生意外错误: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * 辅助方法：处理HTML标题和内容，返回纯文本对
     */
    internal fun processHtmlText(
        title: String?,
        content: String?,
    ): Pair<String, String> {
        val processedTitle = HtmlCompat.fromHtml(title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        val processedContent = HtmlCompat.fromHtml(content ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        return Pair(processedTitle, processedContent)
    }

    private fun addSuperIslandStructuredData(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
    ) {
        // picMap 已在调用方通过 SuperIslandDataFormatter 解析，直接使用
        // 使用公共工具类添加超级岛结构化数据
        SuperIslandStructuredDataHelper.addSuperIslandStructuredData(
            builder = builder,
            context = appContext,
            paramV2Raw = paramV2Raw,
            picMap = picMap,
            title = paramV2?.baseInfo?.title,
            text = paramV2?.baseInfo?.content,
            isSuperIslandSpecInjectionEnabled = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(appContext),
        )
    }
}
