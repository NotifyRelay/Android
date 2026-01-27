package com.xzyht.notifyrelay.feature.notification.superisland

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.text.HtmlCompat
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.parseParamV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
object LiveUpdatesNotificationManager {
    private const val TAG = "超级岛-LiveUpdates"
    const val CHANNEL_ID = "live_updates_channel"
    private const val CHANNEL_NAME = "超级岛Live Updates"
    private const val NOTIFICATION_BASE_ID = 10000
    private const val ICON_CACHE_SIZE = 10 // 最大缓存10个图标
    
    private lateinit var notificationManager: NotificationManager
    private lateinit var appContext: Context
    
    // 图标缓存，避免重复加载
    private val iconCache = object : androidx.collection.LruCache<String, android.graphics.Bitmap>(ICON_CACHE_SIZE) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int {
            // 返回1，表示每个图标计数为1，这样maxSize就表示图标数量
            return 1
        }
    }

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
                Logger.i(TAG, "收到picMap，包含 ${picMap.size} 个图标资源: ${picMap.keys}")
            } else {
                Logger.i(TAG, "picMap为空或null")
            }

            // 仅处理进度类型通知
            if (paramV2?.progressInfo == null && paramV2?.multiProgressInfo == null) {
                Logger.i(TAG, "非进度类型通知，跳过处理: $sourceId")
                return
            }

            // 构建基础通知
            val notificationBuilder = buildBaseNotification(sourceId)
                .setContentTitle(title ?: appName ?: "超级岛通知")
                .setContentText(text ?: "")
                .setSmallIcon(android.R.drawable.stat_notify_more)

            // 直接设置状态栏关键文本，不再使用反射
            val shortText = when {
                title?.isNotEmpty() == true && title.length <= 7 -> title
                appName?.isNotEmpty() == true && appName.length <= 7 -> appName
                paramV2.baseInfo?.title?.isNotEmpty() == true && paramV2.baseInfo.title.length <= 7 -> paramV2.baseInfo.title
                else -> "更新"
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
        val progressInfo = paramV2.progressInfo ?: return
        val multiProgressInfo = paramV2.multiProgressInfo
        val currentProgress = progressInfo.progress
        
        // 调试日志：打印picMap内容，确认图标资源是否存在
        Logger.i(TAG, "加载进度图标 - picMap: $picMap")
        Logger.i(TAG, "加载进度图标 - progressInfo: $progressInfo")
        Logger.i(TAG, "加载进度图标 - multiProgressInfo: $multiProgressInfo")
        
        // 根据当前进度和节点状态，为每个节点选择合适的图标
        val allIconKeys = mutableMapOf<String, String>()
        
        // 收集所有可能需要的图标键
        allIconKeys["picForward"] = progressInfo.picForward ?: multiProgressInfo?.picForward ?: ""
        allIconKeys["picMiddle"] = progressInfo.picMiddle ?: multiProgressInfo?.picMiddle ?: ""
        allIconKeys["picMiddleUnselected"] = progressInfo.picMiddleUnselected ?: multiProgressInfo?.picMiddleUnselected ?: ""
        allIconKeys["picEnd"] = progressInfo.picEnd ?: multiProgressInfo?.picEnd ?: ""
        allIconKeys["picEndUnselected"] = progressInfo.picEndUnselected ?: multiProgressInfo?.picEndUnselected ?: ""
        allIconKeys["picForwardBox"] = multiProgressInfo?.picForwardBox ?: ""
        
        Logger.i(TAG, "所有图标键映射: $allIconKeys")
        Logger.i(TAG, "当前进度: $currentProgress, 进度信息: $progressInfo, 多进度信息: $multiProgressInfo")
        
        // 找到有效的前进图标作为进度指示点
        val forwardIconKey = listOf(
            allIconKeys["picForward"],
            allIconKeys["picForwardBox"],
            allIconKeys["picMiddle"]
        ).firstOrNull { key -> 
            key != null && key.isNotEmpty() && picMap.containsKey(key)
        }
        
        // 调试日志：打印选中的图标键
        Logger.i(TAG, "选中的前进图标键: $forwardIconKey")
        
        // 并行加载进度图标和应用图标
        var progressIconBitmap: Bitmap? = null
        var appIconBitmap: Bitmap? = null
        
        // 加载进度图标
        forwardIconKey?.let { key ->
            val iconUrl = picMap[key]
            if (iconUrl != null) {
                Logger.i(TAG, "加载前进图标URL: $iconUrl")
                
                val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                    context = appContext,
                    urlOrData = iconUrl,
                    timeoutMs = 5000
                )
                
                if (bitmap != null) {
                    Logger.i(TAG, "前进图标加载成功，大小: ${bitmap.width}x${bitmap.height}")
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
        
        // 加载应用图标
        paramV2.picInfo?.pic?.let { picKey ->
            val appIconUrl = picMap[picKey]
            if (appIconUrl != null) {
                val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                    context = appContext,
                    urlOrData = appIconUrl,
                    timeoutMs = 5000
                )
                
                if (bitmap != null) {
                    // 缓存图标
                    iconCache.put(appIconUrl, bitmap)
                    appIconBitmap = bitmap
                }
            }
        }
        
        // 在主线程统一更新通知，确保两个图标都能显示
        withContext(Dispatchers.Main) {
            updateNotificationWithAllIcons(sourceId, notificationId, paramV2, appIconBitmap, progressIconBitmap)
        }
    }
    
    /**
     * 更新进度通知，添加进度跟踪器图标
     */
    private fun updateProgressNotificationWithIcon(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        picMap: Map<String, String>,
        progressIcon: Bitmap
    ) {
        try {
            // 调用新的统一更新方法，不传入应用图标（会在后续加载应用图标时统一更新）
            updateNotificationWithAllIcons(sourceId, notificationId, paramV2, null, progressIcon)
            Logger.i(TAG, "更新进度通知图标成功: $sourceId")
        } catch (e: Exception) {
            Logger.w(TAG, "更新进度通知图标失败: ${e.message}")
            e.printStackTrace()
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
                updatedBuilder
                    .setContentTitle(HtmlCompat.fromHtml(it.title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
                    .setContentText(HtmlCompat.fromHtml(it.content ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
            }
            
            // 设置应用图标（如果有）
            appIcon?.let {
                updatedBuilder.setLargeIcon(it)
            }
            
            // 处理进度样式通知（仅处理进度类型）
            val progressInfo = paramV2.progressInfo
            val multiProgressInfo = paramV2.multiProgressInfo
            
            // 与官方示例保持一致：先获取基础样式，再增量添加图标和进度
            val progressStyle = buildBaseProgressStyle(paramV2)
            
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
        val progressInfo = paramV2.progressInfo ?: return builder
        val multiProgressInfo = paramV2.multiProgressInfo
        
        try {
            // 更新通知标题和内容
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""
                
                // 调试日志：打印原始HTML和处理后的文本
                Logger.i(TAG, "原始标题HTML: $title")
                Logger.i(TAG, "原始内容HTML: $content")
                
                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                
                Logger.i(TAG, "处理后标题: $processedTitle")
                Logger.i(TAG, "处理后内容: $processedContent")
                
                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }
            
            // 获取颜色配置
            val progressColor = progressInfo.colorProgress ?: multiProgressInfo?.color
            val progressEndColor = progressInfo.colorProgressEnd ?: multiProgressInfo?.color
            
            // 解析颜色值
            val pointColor = progressColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.BLUE
            val segmentColor = progressEndColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.CYAN
            
            // 与官方示例保持一致，使用固定的4个进度点（25%, 50%, 75%, 100%）和4个进度段（25% each）
            val progressPoints = listOf(
                NotificationCompat.ProgressStyle.Point(25).setColor(pointColor),
                NotificationCompat.ProgressStyle.Point(50).setColor(pointColor),
                NotificationCompat.ProgressStyle.Point(75).setColor(pointColor),
                NotificationCompat.ProgressStyle.Point(100).setColor(pointColor)
            )
            
            val progressSegments = listOf(
                NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor),
                NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor),
                NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor),
                NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor)
            )
            
            // 直接创建ProgressStyle实例，按照官方示例顺序：先设置基础样式，再设置图标，最后设置进度
            val progressStyle = NotificationCompat.ProgressStyle()
                .setProgressPoints(progressPoints)
                .setProgressSegments(progressSegments)
            
            // 尝试直接设置进度跟踪器图标，避免闪烁
            if (picMap != null && picMap.isNotEmpty()) {
                // 找到有效的前进图标作为进度指示点
                val possibleIconKeys = listOf(
                    progressInfo.picForward,
                    multiProgressInfo?.picForward,
                    multiProgressInfo?.picForwardBox,
                    progressInfo.picMiddle,
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
                            Logger.i(TAG, "从缓存加载图标成功，避免闪烁")
                            progressStyle.setProgressTrackerIcon(IconCompat.createWithBitmap(cachedBitmap))
                        } else {
                            // 缓存中没有，异步加载时会处理
                            Logger.i(TAG, "图标不在缓存中，异步加载时会处理")
                        }
                    }
                }
            }
            
            // 最后设置进度，按照官方示例顺序
            progressStyle.setProgress(progressInfo.progress)
            
            Logger.i(TAG, "设置了 ${progressPoints.size} 个进度点和 ${progressSegments.size} 个进度段，与官方示例保持一致")
            
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
        val pointColor = progressColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.BLUE
        val segmentColor = progressEndColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.CYAN
        
        // 与官方示例保持一致，先创建基础ProgressStyle，设置默认的4个进度点和4个进度段
        return NotificationCompat.ProgressStyle()
            .setProgressPoints(
                listOf(
                    NotificationCompat.ProgressStyle.Point(25).setColor(pointColor),
                    NotificationCompat.ProgressStyle.Point(50).setColor(pointColor),
                    NotificationCompat.ProgressStyle.Point(75).setColor(pointColor),
                    NotificationCompat.ProgressStyle.Point(100).setColor(pointColor)
                )
            )
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor),
                    NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor),
                    NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor),
                    NotificationCompat.ProgressStyle.Segment(25).setColor(segmentColor)
                )
            )
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
                Logger.i(TAG, "原始标题HTML: $title")
                Logger.i(TAG, "原始内容HTML: $content")
                
                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val processedTitle = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                val processedContent = HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
                
                Logger.i(TAG, "处理后标题: $processedTitle")
                Logger.i(TAG, "处理后内容: $processedContent")
                
                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }
            
            // 与官方示例保持一致：先获取基础样式，再进行增量修改
            val progressStyle = buildBaseProgressStyle(paramV2)
                .setProgress(progressInfo.progress)
            
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
            val progressStyle = buildBaseProgressStyle(paramV2)
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
            // 设置默认小图标，确保所有通知都有有效的小图标
            .setSmallIcon(android.R.drawable.stat_notify_more)
            // 直接调用setRequestPromotedOngoing，不再使用反射
            .setRequestPromotedOngoing(true)

        return builder
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
                    val paramV2Json = org.json.JSONObject(rawData)
                    
                    // 构建完整的焦点通知参数结构，包含外层scene、ticker等字段
                    // 从paramV2Json中直接获取baseInfo，确保与FloatingReplicaManager一致
                    val baseInfoJson = paramV2Json.optJSONObject("baseInfo")
                    val tickerValue = baseInfoJson?.optString("title", "") ?: ""
                    val contentValue = baseInfoJson?.optString("content", "") ?: ""
                    
                    val fullFocusParam = org.json.JSONObject().apply {
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
                        val actualPicUrl = com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore.resolve(appContext, picUrl) ?: picUrl
                        extras.putString(picKey, actualPicUrl)
                    }
                }
                
                // 添加miui.focus.pics字段，包含所有图片资源的Bundle
                val picsBundle = android.os.Bundle()
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
            val actionsBundle = android.os.Bundle()
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