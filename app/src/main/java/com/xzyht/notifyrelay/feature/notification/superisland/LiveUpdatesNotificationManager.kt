package com.xzyht.notifyrelay.feature.notification.superisland

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.ProgressStyle
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.parseParamV2

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
object LiveUpdatesNotificationManager {
    private const val TAG = "超级岛-LiveUpdates"
    const val CHANNEL_ID = "live_updates_channel"
    private const val CHANNEL_NAME = "超级岛Live Updates"
    private const val NOTIFICATION_BASE_ID = 10000
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun showLiveUpdate(
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String?,
        appName: String?,
        isLocked: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return
        }

        // 检查是否可以使用Live Updates，但即使不可用也继续尝试发送通知
        if (!canUseLiveUpdates()) {
            Logger.w(TAG, "Live Updates不可用 - 请检查权限和设置，但仍尝试发送通知")
        }

        try {
            val notificationId = sourceId.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            val paramV2 = paramV2Raw?.let { parseParamV2(it) }

            // 构建基础通知
            val notificationBuilder = buildBaseNotification(sourceId)
                .setContentTitle(title ?: appName ?: "超级岛通知")
                .setContentText(text ?: "")
                .setSmallIcon(android.R.drawable.stat_notify_more)

            // 根据paramV2数据构建不同样式的Live Update
            when {
                paramV2?.progressInfo != null -> {
                    // 进度条样式
                    buildProgressStyleNotification(notificationBuilder, paramV2)
                }
                paramV2?.multiProgressInfo != null -> {
                    // 多进度样式
                    buildProgressStyleNotification(notificationBuilder, paramV2)
                }
                paramV2?.highlightInfo != null -> {
                    // 强调样式
                    buildHighlightStyleNotification(notificationBuilder, paramV2)
                }
                paramV2?.hintInfo != null -> {
                    // 提示组件（按钮组件2/3）
                    buildBasicStyleNotification(notificationBuilder, paramV2)
                }
                paramV2?.textButton != null -> {
                    // 文本按钮组件
                    buildBasicStyleNotification(notificationBuilder, paramV2)
                }
                else -> {
                    // 其他基础样式
                    buildBasicStyleNotification(notificationBuilder, paramV2)
                }
            }

            // 设置状态栏关键文本 - 使用反射避免编译错误
            try {
                val setShortCriticalTextMethod = notificationBuilder.javaClass.getMethod("setShortCriticalText", String::class.java)
                val shortText = when {
                    title?.isNotEmpty() == true && title.length <= 7 -> title
                    appName?.isNotEmpty() == true && appName.length <= 7 -> appName
                    paramV2?.baseInfo?.title?.isNotEmpty() == true && paramV2.baseInfo.title.length <= 7 -> paramV2.baseInfo.title
                    else -> "更新"
                }
                setShortCriticalTextMethod.invoke(notificationBuilder, shortText)
            } catch (e: Exception) {
                Logger.w(TAG, "设置状态栏关键文本失败: ${e.message}")
            }

            // 添加操作按钮（如果有）
            paramV2?.actions?.let {
                for (action in it) {
                    try {
                        notificationBuilder.addAction(
                            NotificationCompat.Action.Builder(
                                null, 
                                action.actionTitle ?: "操作", 
                                null
                            ).build()
                        )
                    } catch (e: Exception) {
                        Logger.w(TAG, "添加操作按钮失败: ${e.message}")
                    }
                }
            }

            val notification = notificationBuilder.build()
            
            // 验证通知是否具有可提升特性 - 使用反射避免编译错误
            try {
                val hasPromotableCharacteristicsMethod = notification.javaClass.getMethod("hasPromotableCharacteristics")
                val hasPromotable = hasPromotableCharacteristicsMethod.invoke(notification) as Boolean
                if (!hasPromotable) {
                    Logger.w(TAG, "通知不具有可提升特性 - Live Updates可能无法正常显示")
                }
            } catch (e: Exception) {
                Logger.w(TAG, "检查通知可提升特性失败: ${e.message}")
            }

            notificationManager.notify(notificationId, notification)
            Logger.i(TAG, "发送Live Update通知成功: $sourceId")
        } catch (e: Exception) {
            Logger.e(TAG, "发送Live Update通知失败: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun buildBaseNotification(sourceId: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // 添加Live Updates所需的额外属性
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // 设置RequestPromotedOngoing - 使用反射避免编译错误
        try {
            val setRequestPromotedOngoingMethod = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.java)
            setRequestPromotedOngoingMethod.invoke(builder, true)
        } catch (e: Exception) {
            Logger.w(TAG, "设置请求提升通知失败: ${e.message}")
        }

        return builder
    }

    private fun buildBasicStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2?
    ): NotificationCompat.Builder {
        // 根据paramV2中的不同数据类型，构建不同的基础样式
        paramV2?.let {param ->
            // 处理基础文本组件
            param.baseInfo?.let {baseInfo ->
                return builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(baseInfo.title ?: baseInfo.content ?: "")
                )
            }
            
            // 处理IM图文组件
            param.chatInfo?.let {chatInfo ->
                return builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${chatInfo.title}: ${chatInfo.content}")
                )
            }
            
            // 处理动画文本组件
            param.animTextInfo?.let {animTextInfo ->
                return builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(animTextInfo.title ?: animTextInfo.content ?: "")
                )
            }
            
            // 处理图片识别组件
            param.picInfo?.let {picInfo ->
                return builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(picInfo.title ?: "")
                )
            }
        }
        
        // 默认使用BigTextStyle
        return builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(builder.build().extras.getString(NotificationCompat.EXTRA_TEXT))
        )
    }

    private fun buildProgressStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
    ): NotificationCompat.Builder {
        val progressInfo = paramV2.progressInfo ?: return builder
        val multiProgressInfo = paramV2.multiProgressInfo
        
        try {
            // 直接创建ProgressStyle实例
            val progressStyle = ProgressStyle()
                .setProgress(progressInfo.progress)

            // 创建进度点列表
            val point25 = ProgressStyle.Point(25)
            val point50 = ProgressStyle.Point(50)
            val point75 = ProgressStyle.Point(75)
            val point100 = ProgressStyle.Point(100)
            
            // 创建进度段列表
            val segment25 = ProgressStyle.Segment(25)
            
            // 设置进度点
            when {
                progressInfo.progress < 25 -> {
                    // 初始状态，所有进度点
                    progressStyle.setProgressPoints(listOf(point25, point50, point75, point100))
                }
                progressInfo.progress < 50 -> {
                    // 只显示第一个进度点
                    progressStyle.setProgressPoints(listOf(point25))
                }
                progressInfo.progress < 75 -> {
                    // 显示前两个进度点
                    progressStyle.setProgressPoints(listOf(point25, point50))
                }
                else -> {
                    // 显示前三个进度点
                    progressStyle.setProgressPoints(listOf(point25, point50, point75))
                }
            }

            // 设置进度段
            progressStyle.setProgressSegments(listOf(segment25, segment25, segment25, segment25))

            // 直接调用builder.setStyle方法
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            // 如果ProgressStyle不可用，回退到简单的进度条
            Logger.w(TAG, "使用ProgressStyle失败，回退到简单进度条: ${e.message}")
            return builder.setProgress(
                100,
                progressInfo.progress,
                false
            )
        }
    }

    private fun buildHighlightStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
    ): NotificationCompat.Builder {
        val highlightInfo = paramV2.highlightInfo ?: return builder
        
        // 处理强调图文组件
        try {
            // 直接创建ProgressStyle实例
            val progressStyle = ProgressStyle()
                .setProgress(50) // 强调样式默认显示50%进度

            // 创建进度点列表
            val point25 = ProgressStyle.Point(25)
            val point50 = ProgressStyle.Point(50)
            
            // 创建进度段列表
            val segment25 = ProgressStyle.Segment(25)
            
            // 设置进度点
            progressStyle.setProgressPoints(listOf(point25, point50))

            // 设置进度段
            progressStyle.setProgressSegments(listOf(segment25, segment25, segment25, segment25))

            // 直接调用builder.setStyle方法
            return builder.setStyle(progressStyle)
                .setContentText(highlightInfo.title ?: highlightInfo.content ?: "")
        } catch (e: Exception) {
            // 回退到BigTextStyle
            Logger.w(TAG, "使用ProgressStyle失败，回退到BigTextStyle: ${e.message}")
            return builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(highlightInfo.title ?: highlightInfo.content ?: "")
            )
        }
    }

    fun cancelLiveUpdate(sourceId: String) {
        try {
            val notificationId = sourceId.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            notificationManager.cancel(notificationId)
            Logger.i(TAG, "取消Live Update通知成功: $sourceId")
        } catch (e: Exception) {
            Logger.e(TAG, "取消Live Update通知失败: ${e.message}")
        }
    }
    
    // 兼容旧方法名
    fun dismissLiveUpdateNotification(sourceId: String) {
        cancelLiveUpdate(sourceId)
    }

    fun cancelAllLiveUpdates() {
        notificationManager.cancelAll()
        Logger.i(TAG, "取消所有Live Update通知成功")
    }

    fun canUseLiveUpdates(): Boolean {
        // 检查设备是否支持Live Updates
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "canUseLiveUpdates: SDK版本 ${Build.VERSION.SDK_INT} < BAKLAVA")
            return false
        }

        // 检查notificationManager是否已初始化
        try {
            // 尝试访问notificationManager，会抛出UninitializedPropertyAccessException如果未初始化
            val managerRef = notificationManager
        } catch (e: UninitializedPropertyAccessException) {
            Logger.e(TAG, "canUseLiveUpdates: notificationManager未初始化")
            return false
        } catch (e: Exception) {
            Logger.e(TAG, "canUseLiveUpdates: 检查notificationManager时发生意外错误: ${e.message}")
            return false
        }

        // 检查是否可以发布提升通知 - 使用反射避免编译错误
        try {
            // 先检查方法是否存在
            val canPostPromotedNotificationsMethod = notificationManager.javaClass.getMethod("canPostPromotedNotifications")
            val result = canPostPromotedNotificationsMethod.invoke(notificationManager) as Boolean
            Logger.i(TAG, "canUseLiveUpdates: canPostPromotedNotifications返回 $result")
            return result
        } catch (e: NoSuchMethodException) {
            Logger.w(TAG, "canUseLiveUpdates: 未找到canPostPromotedNotifications方法 - 设备可能不支持")
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

    fun hasPromotableCharacteristics(notification: NotificationCompat.Builder): Boolean {
        val builtNotification = notification.build()
        // 使用反射检查通知是否具有可提升特性
        try {
            val hasPromotableCharacteristicsMethod = builtNotification.javaClass.getMethod("hasPromotableCharacteristics")
            return hasPromotableCharacteristicsMethod.invoke(builtNotification) as Boolean
        } catch (e: Exception) {
            Logger.w(TAG, "检查通知可提升特性失败: ${e.message}")
            return false
        }
    }
}