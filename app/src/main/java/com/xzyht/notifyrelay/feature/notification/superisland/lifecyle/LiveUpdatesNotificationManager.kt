package com.xzyht.notifyrelay.feature.notification.superisland.lifecyle

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.collection.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.HtmlCompat
import com.xzyht.notifyrelay.feature.notification.superisland.NotificationBroadcastReceiver
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.parseParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import org.json.JSONObject

object LiveUpdatesNotificationManager {
    private const val TAG = "超级岛-进度类型"
    const val CHANNEL_ID = "live_updates_channel"
    private const val CHANNEL_NAME = "超级岛Live Updates"
    private const val NOTIFICATION_BASE_ID = 10000
    private const val ICON_CACHE_SIZE = 10 // 最大缓存10个图标

    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context

    // 图标缓存，避免重复加载
    private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            // 返回1，表示每个图标计数为1，这样maxSize就表示图标数量
            return 1
        }
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
        isLocked: Boolean,
        picMap: Map<String, String>? = null
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

            // 调试picMap内容
            if (picMap != null && picMap.isNotEmpty()) {
                Logger.d(TAG, "收到picMap，包含 ${picMap.size} 个图标资源: ${picMap.keys}")
            } else {
                Logger.d(TAG, "picMap为空或null")
            }

            // 仅处理进度类型通知
            if (paramV2?.progressInfo == null && paramV2?.multiProgressInfo == null) {
                Logger.i(TAG, "非进度类型通知，跳过处理: $sourceId")
                return
            }

            // 创建删除意图，用于处理用户移除通知时关闭浮窗
            val deleteIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId,
                Intent(appContext, NotificationBroadcastReceiver::class.java)
                    .putExtra("notificationId", notificationId)
                    .setAction("com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 创建点击意图，用于处理用户点击通知时切换浮窗显示/隐藏
            val contentIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId,
                Intent(appContext, NotificationBroadcastReceiver::class.java)
                    .putExtra("sourceId", sourceId)
                    .putExtra("title", title)
                    .putExtra("text", text)
                    .putExtra("appName", appName)
                    .putExtra("paramV2Raw", paramV2Raw)
                    .setAction("com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 构建基础通知
            val notificationBuilder = buildBaseNotification(sourceId)
                .setContentTitle(title ?: appName ?: "超级岛通知")
                .setContentText(text ?: "")
                .setSmallIcon(R.drawable.stat_notify_more)
                .setDeleteIntent(deleteIntent)
                .setContentIntent(contentIntent)

            // 尝试使用前进指示器图标作为小图标
            if (picMap != null && picMap.isNotEmpty()) {
                // 找到有效的前进图标
                val possibleIconKeys = listOf(
                    paramV2?.progressInfo?.picForward,
                    paramV2?.multiProgressInfo?.picForward,
                    paramV2?.multiProgressInfo?.picForwardBox,
                    paramV2?.progressInfo?.picMiddle,
                    paramV2?.multiProgressInfo?.picMiddle
                )

                val iconKey = possibleIconKeys.firstOrNull { key ->
                    key != null && picMap.containsKey(key)
                }

                if (iconKey != null) {
                    val iconUrl = picMap[iconKey]
                    if (iconUrl != null) {
                        // 尝试从缓存加载图标
                        val cachedBitmap = iconCache.get(iconUrl)
                        if (cachedBitmap != null) {
                            notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(cachedBitmap))
                        }
                    }
                }
            }

            // 直接设置状态栏关键文本，不再使用反射
            // 使用处理后的标题和内容
            val processedTitle = paramV2.baseInfo?.title?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() } ?: ""
            val processedContent = paramV2.baseInfo?.content?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() } ?: ""

            // 设置状态栏关键文本，优先使用处理后的标题（预计时间），然后是处理后的内容
            val shortText = when {
                processedTitle.isNotEmpty() -> processedTitle
                processedContent.isNotEmpty() -> processedContent
                title?.isNotEmpty() == true -> title
                appName?.isNotEmpty() == true -> appName
                else -> " "
            }
            notificationBuilder.setShortCriticalText(shortText)

            // 添加操作按钮（如果有）
            paramV2.actions?.let {
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

            // 构建最终通知 - 仅处理进度类型
            val finalBuilder = buildProgressStyleNotification(notificationBuilder, paramV2, picMap)

            // 添加超级岛相关的结构化数据
            addSuperIslandStructuredData(finalBuilder, paramV2, paramV2Raw, picMap)

            val notification = finalBuilder.build()

            // 验证通知是否具有可提升特性
            try {
                val hasPromotable = notification.hasPromotableCharacteristics()
                if (!hasPromotable) {
                    Logger.w(TAG, "通知不具有可提升特性 - Live Updates可能无法正常显示")
                } else {
                    Logger.i(TAG, "通知具有可提升特性")
                }
            } catch (e: Exception) {
                Logger.w(TAG, "检查通知可提升特性失败: ${e.message}")
            }

            // 发送通知
            notificationManager.notify(notificationId, notification)
            Logger.i(TAG, "发送Live Update进度通知成功: $sourceId")

            // 异步加载图标并更新通知，确保图标正确显示
            loadIconsAndUpdateNotification(sourceId, notificationId, paramV2, picMap)
        } catch (e: Exception) {
            Logger.e(TAG, "发送Live Update通知失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 异步加载图标并更新通知
     */
    private fun loadIconsAndUpdateNotification(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2?,
        picMap: Map<String, String>?
    ) {
        // 只在有图标资源时才异步加载
        if (picMap.isNullOrEmpty() || paramV2 == null) return

        // 异步加载图标
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 仅处理进度类型图标加载
                loadProgressStyleIcons(sourceId, notificationId, paramV2, picMap)
            } catch (e: Exception) {
                Logger.e(TAG, "异步加载进度图标并更新通知失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 加载进度样式的图标并更新通知
     */
    private suspend fun loadProgressStyleIcons(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        picMap: Map<String, String>
    ) {
        val progressInfo = paramV2.progressInfo
        val multiProgressInfo = paramV2.multiProgressInfo

        // 检查是否有任何进度信息
        if (progressInfo == null && multiProgressInfo == null) {
            return
        }

        // 获取当前进度值
        val currentProgress = progressInfo?.progress ?: multiProgressInfo?.progress ?: 0

        // 调试日志：打印picMap内容，确认图标资源是否存在
        Logger.d(TAG, "加载进度图标 - picMap: $picMap")
        Logger.d(TAG, "加载进度图标 - progressInfo: $progressInfo")
        Logger.d(TAG, "加载进度图标 - multiProgressInfo: $multiProgressInfo")

        // 根据当前进度和节点状态，为每个节点选择合适的图标
        val allIconKeys = mutableMapOf<String, String>()

        // 收集所有可能需要的图标键
        allIconKeys["picForward"] = progressInfo?.picForward ?: multiProgressInfo?.picForward ?: ""
        allIconKeys["picMiddle"] = progressInfo?.picMiddle ?: multiProgressInfo?.picMiddle ?: ""
        allIconKeys["picMiddleUnselected"] = progressInfo?.picMiddleUnselected ?: multiProgressInfo?.picMiddleUnselected ?: ""
        allIconKeys["picEnd"] = progressInfo?.picEnd ?: multiProgressInfo?.picEnd ?: ""
        allIconKeys["picEndUnselected"] = progressInfo?.picEndUnselected ?: multiProgressInfo?.picEndUnselected ?: ""
        allIconKeys["picForwardBox"] = multiProgressInfo?.picForwardBox ?: ""

        Logger.d(TAG, "所有图标键映射: $allIconKeys")
        Logger.d(TAG, "当前进度: $currentProgress, 进度信息: $progressInfo, 多进度信息: $multiProgressInfo")

        // 找到有效的前进图标作为进度指示点
        val forwardIconKey = listOf(
            allIconKeys["picForward"],
            allIconKeys["picForwardBox"],
            allIconKeys["picMiddle"]
        ).firstOrNull { key ->
            key != null && key.isNotEmpty() && picMap.containsKey(key)
        }

        // 调试日志：打印选中的图标键
        Logger.d(TAG, "选中的前进图标键: $forwardIconKey")

        // 并行加载进度图标和应用图标
        var progressIconBitmap: Bitmap? = null
        var appIconBitmap: Bitmap? = null

        // 优先加载应用图标作为小图标
        paramV2.picInfo?.pic?.let { picKey ->
            val appIconUrl = picMap[picKey]
            if (appIconUrl != null) {
                val bitmap = SuperIslandImageUtil.loadBitmapSuspend(
                    context = appContext,
                    urlOrData = appIconUrl,
                    timeoutMs = 5000
                )

                if (bitmap != null) {
                    Logger.d(TAG, "应用图标加载成功，大小: ${bitmap.width}x${bitmap.height}")
                    // 缓存图标
                    iconCache.put(appIconUrl, bitmap)
                    appIconBitmap = bitmap
                    // 优先使用应用图标作为小图标
                    progressIconBitmap = bitmap
                }
            }
        }

        // 如果没有应用图标，再加载前进图标作为小图标
        if (progressIconBitmap == null) {
            forwardIconKey?.let { key ->
                val iconUrl = picMap[key]
                if (iconUrl != null) {
                    Logger.d(TAG, "加载前进图标URL: $iconUrl")

                    val bitmap = SuperIslandImageUtil.loadBitmapSuspend(
                        context = appContext,
                        urlOrData = iconUrl,
                        timeoutMs = 5000
                    )

                    if (bitmap != null) {
                        Logger.d(TAG, "前进图标加载成功，大小: ${bitmap.width}x${bitmap.height}")
                        // 缓存图标
                        iconCache.put(iconUrl, bitmap)
                        progressIconBitmap = bitmap
                    } else {
                        Logger.w(TAG, "前进图标加载失败，URL: $iconUrl")
                    }
                }
            } ?: run {
                Logger.w(TAG, "未找到有效的前进图标键")
            }
        }

        // 在主线程统一更新通知，确保两个图标都能显示
        withContext(Dispatchers.Main) {
            updateNotificationWithAllIcons(
                sourceId,
                notificationId,
                paramV2,
                appIconBitmap,
                progressIconBitmap
            )
        }
    }

    /**
     * 更新通知，添加应用图标和进度图标
     */
    private fun updateNotificationWithAllIcons(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        appIcon: Bitmap?,
        progressIcon: Bitmap?
    ) {
        try {
            // 构建基础通知
            val updatedBuilder = buildBaseNotification(sourceId)

            // 设置基础信息
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""

                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)

                // 参考 NotificationGenerator.kt 的逻辑设置文本
                updatedBuilder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }

            // 设置应用图标（如果有）
            appIcon?.let {
                updatedBuilder.setLargeIcon(it)
            }

            // 使用前进指示器图标作为小图标
            progressIcon?.let {
                updatedBuilder.setSmallIcon(IconCompat.createWithBitmap(it))
            }

            // 设置状态栏关键文本，与初始创建通知时保持一致
            val processedTitle = paramV2.baseInfo?.title?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() } ?: ""
            val processedContent = paramV2.baseInfo?.content?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() } ?: ""

            val shortText = when {
                processedTitle.isNotEmpty() -> processedTitle
                processedContent.isNotEmpty() -> processedContent
                else -> " "
            }
            updatedBuilder.setShortCriticalText(shortText)

            // 创建删除意图，用于处理用户移除通知时关闭浮窗
            val deleteIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId,
                Intent(appContext, NotificationBroadcastReceiver::class.java)
                    .putExtra("notificationId", notificationId)
                    .setAction("com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 创建点击意图，用于处理用户点击通知时切换浮窗显示/隐藏
            val contentIntent = PendingIntent.getBroadcast(
                appContext,
                notificationId,
                Intent(appContext, NotificationBroadcastReceiver::class.java)
                    .putExtra("sourceId", sourceId)
                    .putExtra("title", paramV2.baseInfo?.title)
                    .putExtra("text", paramV2.baseInfo?.content)
                    .setAction("com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 设置意图
            updatedBuilder
                .setDeleteIntent(deleteIntent)
                .setContentIntent(contentIntent)

            // 处理进度样式通知（仅处理进度类型）
            val progressInfo = paramV2.progressInfo
            val multiProgressInfo = paramV2.multiProgressInfo

            // 与官方示例保持一致：先获取基础样式，再增量添加图标和进度
            val progressStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                buildBaseProgressStyle(paramV2)
            } else {
                // 在低 API 级别上使用简单的 ProgressStyle
                NotificationCompat.ProgressStyle()
            }

            // 设置进度图标（如果有）
            progressIcon?.let {
                progressStyle.setProgressTrackerIcon(IconCompat.createWithBitmap(it))
            }

            // 设置进度值
            val currentProgress = progressInfo?.progress ?: multiProgressInfo?.progress ?: 0
            progressStyle.setProgress(currentProgress)

            // 设置样式
            updatedBuilder.setStyle(progressStyle)

            notificationManager.notify(notificationId, updatedBuilder.build())
            Logger.i(TAG, "更新进度通知图标成功: $sourceId")
        } catch (e: Exception) {
            Logger.w(TAG, "更新通知所有图标失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 构建包含图标的进度样式通知，避免图标闪烁
     */
    private fun buildProgressStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2,
        picMap: Map<String, String>? = null
    ): NotificationCompat.Builder {
        val progressInfo = paramV2.progressInfo
        val multiProgressInfo = paramV2.multiProgressInfo

        // 检查是否有任何进度信息
        if (progressInfo == null && multiProgressInfo == null) {
            return builder
        }

        try {
            // 更新通知标题和内容
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""

                // 调试日志：打印原始HTML和处理后的文本
                Logger.d(TAG, "原始标题HTML: $title")
                Logger.d(TAG, "原始内容HTML: $content")

                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)

                Logger.d(TAG, "处理后标题: $processedTitle")
                Logger.d(TAG, "处理后内容: $processedContent")

                // 参考 NotificationGenerator.kt 的逻辑设置文本
                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }

            // 获取颜色配置
            val progressColor = progressInfo?.colorProgress ?: multiProgressInfo?.color
            val progressEndColor = progressInfo?.colorProgressEnd ?: multiProgressInfo?.color

            // 解析颜色值
            val pointColor = progressColor?.let { Color.parseColor(it) } ?: Color.BLUE
            val segmentColor = progressEndColor?.let { Color.parseColor(it) } ?: Color.CYAN

            // 直接创建ProgressStyle实例
            val progressStyle = NotificationCompat.ProgressStyle()

            // 声明变量，用于记录节点和分段数量
            var progressPointsCount = 0
            var progressSegmentsCount = 0

            // 只有当 multiProgressInfo 存在时才生成节点和分段
            if (multiProgressInfo != null) {
                // 根据 multiProgressInfo.points 生成进度点和分段
                val nodeCount = multiProgressInfo.points ?: 4
                val validNodeCount = maxOf(2, nodeCount) // 最少需要2个节点来创建分段
                val segmentCount = validNodeCount - 1
                val segmentSize = 100 / segmentCount

                // 生成进度点
                val progressPoints = mutableListOf<NotificationCompat.ProgressStyle.Point>()
                val nodePositions = mutableListOf<Int>()
                for (i in 0 until validNodeCount) {
                    val position = (i * 100) / (validNodeCount - 1)
                    nodePositions.add(position)
                    val point = NotificationCompat.ProgressStyle.Point(position).setColor(pointColor)
                    progressPoints.add(point)
                    Logger.d(TAG, "生成节点 $i，位置: $position%")
                }

                // 生成进度分段
                val progressSegments = mutableListOf<NotificationCompat.ProgressStyle.Segment>()
                for (i in 0 until segmentCount) {
                    progressSegments.add(NotificationCompat.ProgressStyle.Segment(segmentSize).setColor(segmentColor))
                }

                // 设置进度点和分段
                progressStyle.setProgressPoints(progressPoints)
                progressStyle.setProgressSegments(progressSegments)

                // 记录节点和分段数量
                progressPointsCount = progressPoints.size
                progressSegmentsCount = progressSegments.size

                Logger.d(TAG, "为 multiProgressInfo 生成了 $progressPointsCount 个节点，分别在位置: $nodePositions")
            }

            // 尝试直接设置进度跟踪器图标，避免闪烁
            if (picMap != null && picMap.isNotEmpty()) {
                // 找到有效的前进图标作为进度指示点
                val possibleIconKeys = listOf(
                    progressInfo?.picForward,
                    multiProgressInfo?.picForward,
                    multiProgressInfo?.picForwardBox,
                    progressInfo?.picMiddle,
                    multiProgressInfo?.picMiddle
                )

                val iconKey = possibleIconKeys.firstOrNull { key ->
                    key != null && picMap.containsKey(key)
                }

                if (iconKey != null) {
                    val iconUrl = picMap[iconKey]
                    if (iconUrl != null) {
                        // 尝试从缓存加载图标
                        val cachedBitmap = iconCache.get(iconUrl)
                        if (cachedBitmap != null) {
                            Logger.d(TAG, "从缓存加载图标成功，避免闪烁")
                            progressStyle.setProgressTrackerIcon(IconCompat.createWithBitmap(cachedBitmap))
                        } else {
                            // 缓存中没有，异步加载时会处理
                            Logger.d(TAG, "图标不在缓存中，异步加载时会处理")
                        }
                    }
                }
            }

            // 获取进度值
            val currentProgress = progressInfo?.progress ?: multiProgressInfo?.progress ?: 0

            // 最后设置进度，按照官方示例顺序
            progressStyle.setProgress(currentProgress)

            Logger.d(TAG, "设置了 ${progressPointsCount} 个进度点和 ${progressSegmentsCount} 个进度段，与官方示例保持一致")

            // 直接调用builder.setStyle方法，符合官方示例的API使用
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            // 如果ProgressStyle不可用，回退到简单的进度条
            Logger.w(TAG, "使用ProgressStyle失败，回退到简单进度条: ${e.message}")
            e.printStackTrace()

            // 更新通知标题和内容
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""

                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)

                // 参考 NotificationGenerator.kt 的逻辑设置文本
                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }

            // 获取进度值
            val currentProgress = progressInfo?.progress ?: multiProgressInfo?.progress ?: 0

            return builder.setProgress(
                100,
                currentProgress,
                false
            )
        }
    }

    /**
     * 构建基础进度样式，与官方示例保持一致
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildBaseProgressStyle(paramV2: ParamV2): NotificationCompat.ProgressStyle {
        val progressInfo = paramV2.progressInfo
        val multiProgressInfo = paramV2.multiProgressInfo

        // 获取颜色配置
        val progressColor = progressInfo?.colorProgress ?: multiProgressInfo?.color
        val progressEndColor = progressInfo?.colorProgressEnd ?: multiProgressInfo?.color

        // 解析颜色值
        val pointColor = progressColor?.let { Color.parseColor(it) } ?: Color.BLUE
        val segmentColor = progressEndColor?.let { Color.parseColor(it) } ?: Color.CYAN

        // 直接创建ProgressStyle实例
        val progressStyle = NotificationCompat.ProgressStyle()

        // 只有当 multiProgressInfo 存在时才生成节点和分段
        if (multiProgressInfo != null) {
            // 根据 multiProgressInfo.points 生成进度点和分段
            val nodeCount = multiProgressInfo.points ?: 4
            val validNodeCount = maxOf(2, nodeCount) // 最少需要2个节点来创建分段
            val segmentCount = validNodeCount - 1
            val segmentSize = 100 / segmentCount

            // 生成进度点
            val progressPoints = mutableListOf<NotificationCompat.ProgressStyle.Point>()
            for (i in 0 until validNodeCount) {
                var position = (i * 100) / (validNodeCount - 1)
                // 调整位置，避免使用0和100，因为原生通知可能不支持
                if (position == 0) {
                    position = 5 // 使用5%代替0%
                } else if (position == 100) {
                    position = 95 // 使用95%代替100%
                }
                progressPoints.add(NotificationCompat.ProgressStyle.Point(position).setColor(pointColor))
            }

            // 生成进度分段
            val progressSegments = mutableListOf<NotificationCompat.ProgressStyle.Segment>()
            for (i in 0 until segmentCount) {
                progressSegments.add(NotificationCompat.ProgressStyle.Segment(segmentSize).setColor(segmentColor))
            }

            // 设置进度点和分段
            progressStyle.setProgressPoints(progressPoints)
            progressStyle.setProgressSegments(progressSegments)
        }

        return progressStyle
    }

    /**
     * 构建不包含图标的进度样式通知
     */
    private fun buildProgressStyleNotificationWithoutIcons(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
    ): NotificationCompat.Builder {
        val progressInfo = paramV2.progressInfo ?: return builder

        try {
            // 更新通知标题和内容
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""

                // 调试日志：打印原始HTML和处理后的文本
                Logger.d(TAG, "原始标题HTML: $title")
                Logger.d(TAG, "原始内容HTML: $content")

                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)

                Logger.d(TAG, "处理后标题: $processedTitle")
                Logger.d(TAG, "处理后内容: $processedContent")

                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }

            // 与官方示例保持一致：先获取基础样式，再进行增量修改
            val progressStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                buildBaseProgressStyle(paramV2).setProgress(progressInfo.progress)
            } else {
                // 在低 API 级别上使用简单的 ProgressStyle
                NotificationCompat.ProgressStyle().setProgress(progressInfo.progress)
            }

            // 直接调用builder.setStyle方法，符合官方示例的API使用
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            // 如果ProgressStyle不可用，回退到简单的进度条
            Logger.w(TAG, "使用ProgressStyle失败，回退到简单进度条: ${e.message}")
            e.printStackTrace()

            // 更新通知标题和内容
            paramV2.baseInfo?.let {
                builder
                    .setContentTitle(HtmlCompat.fromHtml(it.title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
                    .setContentText(HtmlCompat.fromHtml(it.content ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
            }

            return builder.setProgress(
                100,
                progressInfo.progress,
                false
            )
        }
    }

    /**
     * 构建包含图标的进度样式通知
     */
    private fun buildProgressStyleNotificationWithIcon(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2,
        progressIcon: Bitmap
    ): NotificationCompat.Builder {
        val progressInfo = paramV2.progressInfo ?: return builder

        try {
            // 更新通知标题和内容
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""

                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)

                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }

            // 与官方示例保持一致：先获取基础样式，再增量添加图标和进度
            val progressStyle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                buildBaseProgressStyle(paramV2)
            } else {
                // 在低 API 级别上使用简单的 ProgressStyle
                NotificationCompat.ProgressStyle()
            }
                .setProgressTrackerIcon(IconCompat.createWithBitmap(progressIcon))
                .setProgress(progressInfo.progress)

            // 直接调用builder.setStyle方法，符合官方示例的API使用
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            Logger.w(TAG, "构建包含图标的进度样式通知失败: ${e.message}")
            return builder
        }
    }

    private fun buildBaseNotification(sourceId: String): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // 添加Live Updates所需的属性，参照参考实现
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 直接调用setRequestPromotedOngoing，不再使用反射
            .setRequestPromotedOngoing(true)

        return builder
    }

    fun cancelLiveUpdate(sourceId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return
        }
        try {
            // 检查notificationManager是否已初始化
            if (!::notificationManager.isInitialized) {
                Logger.w(TAG, "LiveUpdatesNotificationManager未初始化，跳过取消通知")
                return
            }
            val notificationId = sourceId.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            notificationManager.cancel(notificationId)
            Logger.i(TAG, "取消Live Update通知成功: $sourceId")
        } catch (e: Exception) {
            Logger.e(TAG, "取消Live Update通知失败: ${e.message}")
        }
    }

    // 兼容旧方法名
    fun dismissLiveUpdateNotification(sourceId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return
        }
        cancelLiveUpdate(sourceId)
    }

    fun cancelAllLiveUpdates() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return
        }
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
        if (!::notificationManager.isInitialized) {
            Logger.e(TAG, "canUseLiveUpdates: notificationManager未初始化")
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            Logger.w(TAG, "当前Android版本不支持Live Updates")
            return false
        }
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

    /**
     * 添加超级岛相关的结构化数据到Live Updates通知
     * @param builder 通知构建器
     * @param paramV2 ParamV2对象
     * @param paramV2Raw ParamV2原始JSON字符串
     * @param picMap 图片映射
     */
    private fun addSuperIslandStructuredData(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2?,
        paramV2Raw: String?,
        picMap: Map<String, String>?
    ) {
        try {
            // 获取通知的extras，用于添加结构化数据
            val extras = builder.extras

            // 构建符合小米官方规范的完整miui.focus.param结构
            paramV2Raw?.let { rawData ->
                try {
                    // 解析原始paramV2数据
                    val paramV2Json = JSONObject(rawData)

                    // 构建完整的焦点通知参数结构，包含外层scene、ticker等字段
                    // 从paramV2Json中直接获取baseInfo，确保与FloatingReplicaManager一致
                    val baseInfoJson = paramV2Json.optJSONObject("baseInfo")
                    val tickerValue = baseInfoJson?.optString("title", "") ?: ""
                    val contentValue = baseInfoJson?.optString("content", "") ?: ""

                    val fullFocusParam = JSONObject().apply {
                        put("protocol", 1)
                        put("scene", paramV2Json.optString("business", "default"))
                        put("ticker", tickerValue)
                        put("content", contentValue)
                        put("timerType", 0)
                        put("timerWhen", 0)
                        put("timerSystemCurrent", 0)
                        put("enableFloat", false)
                        put("updatable", true)
                        put("reopen", paramV2Json.optString("reopen", "close"))
                        put("timeout", paramV2Json.optInt("timeout", 720))
                        put("filterWhenNoPermission", paramV2Json.optBoolean("filterWhenNoPermission", false))
                        put("islandFirstFloat", paramV2Json.optBoolean("islandFirstFloat", false))
                        put("param_v2", paramV2Json) // 将原始paramV2作为嵌套字段
                    }

                    extras.putString("miui.focus.param", fullFocusParam.toString())
                    Logger.i(TAG, "添加miui.focus.param成功")
                } catch (e: Exception) {
                    // 如果构建完整结构失败，回退到直接使用原始数据
                    extras.putString("miui.focus.param", rawData)
                    Logger.w(TAG, "构建完整焦点通知参数结构失败，回退到原始数据: ${e.message}")
                }
            }

            // 按照小米官方文档规范，将每个图片资源作为单独的extra添加
            picMap?.let { map ->
                map.forEach { (picKey, picUrl) ->
                    // 确保key以"miui.focus.pic_"前缀开头，符合小米规范
                    if (picKey.startsWith("miui.focus.pic_")) {
                        // 解析图片引用符，获取实际的图片数据
                        val actualPicUrl = SuperIslandImageStore.resolve(appContext, picUrl) ?: picUrl
                        extras.putString(picKey, actualPicUrl)
                    }
                }

                // 添加miui.focus.pics字段，包含所有图片资源的Bundle
                val picsBundle = Bundle()
                map.forEach { (picKey, picUrl) ->
                    if (picKey.startsWith("miui.focus.pic_")) {
                        picsBundle.putString(picKey, picUrl)
                    }
                }
                extras.putBundle("miui.focus.pics", picsBundle)
                Logger.i(TAG, "添加图片资源成功，共${map.size}个图片")
            }

            // 添加焦点通知必要的额外字段
            extras.putBoolean("miui.showAction", true)

            // 添加模拟的action字段
            val actionsBundle = Bundle()
            actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
            actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
            extras.putBundle("miui.focus.actions", actionsBundle)

            // 添加原始通知中存在的其他字段，这些可能影响UI显示
            // 对于计时器类通知，添加计时器相关字段
            val title = paramV2?.baseInfo?.title ?: ""
            if (title.contains("计时") || title.contains("秒表")) {
                extras.putBoolean("android.chronometerCountDown", false)
                extras.putBoolean("android.showChronometer", true)
            }

            // 添加应用信息，与原始通知保持一致
            extras.putBoolean("android.reduced.images", true)

            // 添加超级岛源包信息，与原始通知保持一致
            extras.putString("superIslandSourcePackage", appContext.packageName)

            // 添加包名信息，与原始通知保持一致
            extras.putString("app_package", appContext.packageName)

            // 添加MIUI焦点通知所需的额外字段
            extras.putBoolean("miui.isFocusNotification", true)
            extras.putBoolean("miui.showBadge", false)

            Logger.i(TAG, "添加超级岛结构化数据成功")
        } catch (e: Exception) {
            Logger.w(TAG, "添加超级岛结构化数据失败: ${e.message}")
            e.printStackTrace()
        }
    }
}