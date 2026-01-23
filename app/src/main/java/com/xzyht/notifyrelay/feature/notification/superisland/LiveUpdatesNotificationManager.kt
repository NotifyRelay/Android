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
            // 返回图标大小，单位为KB
            return value.byteCount / 1024
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

            // 构建基础通知
            val notificationBuilder = buildBaseNotification(sourceId)
                .setContentTitle(title ?: appName ?: "超级岛通知")
                .setContentText(text ?: "")
                .setSmallIcon(android.R.drawable.stat_notify_more)

            // 直接设置状态栏关键文本，不再使用反射
            val shortText = when {
                title?.isNotEmpty() == true && title.length <= 7 -> title
                appName?.isNotEmpty() == true && appName.length <= 7 -> appName
                paramV2?.baseInfo?.title?.isNotEmpty() == true && paramV2.baseInfo.title.length <= 7 -> paramV2.baseInfo.title
                else -> "更新"
            }
            notificationBuilder.setShortCriticalText(shortText)

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

            // 构建最终通知
            val finalBuilder = when {
                paramV2?.progressInfo != null || paramV2?.multiProgressInfo != null -> {
                    // 进度条样式或多进度样式
                    buildProgressStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                paramV2?.highlightInfo != null -> {
                    // 强调样式
                    buildHighlightStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                paramV2?.hintInfo != null || paramV2?.textButton != null -> {
                    // 提示组件或文本按钮组件
                    buildBasicStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                else -> {
                    // 其他基础样式
                    buildBasicStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
            }

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
            Logger.i(TAG, "发送Live Update通知成功: $sourceId")
            
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
                // 根据不同的通知类型，加载对应的图标
                when {
                    paramV2.progressInfo != null || paramV2.multiProgressInfo != null -> {
                        loadProgressStyleIcons(sourceId, notificationId, paramV2, picMap)
                    }
                    paramV2.highlightInfo != null -> {
                        loadHighlightStyleIcons(sourceId, notificationId, paramV2, picMap)
                    }
                    else -> {
                        loadBasicStyleIcons(sourceId, notificationId, paramV2, picMap)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "异步加载图标并更新通知失败: ${e.message}")
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
            
            // 处理进度样式通知
            if (paramV2.progressInfo != null || paramV2.multiProgressInfo != null) {
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
            } else if (paramV2.highlightInfo != null) {
                // 强调样式
                buildHighlightStyleNotificationWithoutIcons(updatedBuilder, paramV2)
            } else {
                // 其他基础样式
                buildBasicStyleNotificationWithoutIcons(updatedBuilder, paramV2)
            }
            
            notificationManager.notify(notificationId, updatedBuilder.build())
            Logger.i(TAG, "更新通知所有图标成功: $sourceId")
        } catch (e: Exception) {
            Logger.w(TAG, "更新通知所有图标失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 更新通知，添加应用图标
     */
    private fun updateNotificationWithAppIcon(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        appIcon: Bitmap
    ) {
        // 调用新的统一更新方法，不传入进度图标
        updateNotificationWithAllIcons(sourceId, notificationId, paramV2, appIcon, null)
    }
    
    /**
     * 加载强调样式的图标并更新通知
     */
    private suspend fun loadHighlightStyleIcons(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        picMap: Map<String, String>
    ) {
        val highlightInfo = paramV2.highlightInfo ?: return
        
        // 加载强调组件图标
        highlightInfo.picFunction?.let { picKey ->
            val iconUrl = picMap[picKey]
            if (iconUrl != null) {
                val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                    context = appContext,
                    urlOrData = iconUrl,
                    timeoutMs = 5000
                )
                
                if (bitmap != null) {
                    // 缓存图标
                    iconCache.put(iconUrl, bitmap)
                    
                    // 在主线程更新通知
                    withContext(Dispatchers.Main) {
                        updateHighlightNotificationWithIcon(sourceId, notificationId, paramV2, picMap, bitmap)
                    }
                }
            }
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
                    
                    // 在主线程更新通知
                    withContext(Dispatchers.Main) {
                        updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                    }
                }
            }
        }
    }
    
    /**
     * 更新强调通知，添加图标
     */
    private fun updateHighlightNotificationWithIcon(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        picMap: Map<String, String>,
        highlightIcon: Bitmap
    ) {
        try {
            // 重新构建强调样式通知，包含图标
            val updatedBuilder = buildBaseNotification(sourceId)
            buildHighlightStyleNotificationWithIcon(updatedBuilder, paramV2, picMap, highlightIcon)
            
            notificationManager.notify(notificationId, updatedBuilder.build())
            Logger.i(TAG, "更新强调通知图标成功: $sourceId")
        } catch (e: Exception) {
            Logger.w(TAG, "更新强调通知图标失败: ${e.message}")
        }
    }
    
    /**
     * 加载基础样式的图标并更新通知
     */
    private suspend fun loadBasicStyleIcons(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2,
        picMap: Map<String, String>
    ) {
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
                    
                    // 在主线程更新通知
                    withContext(Dispatchers.Main) {
                        updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                    }
                }
            }
        }
        
        // 根据不同的基础样式，加载对应的图标
        when {
            // 处理基础文本组件图标
            paramV2.baseInfo != null -> {
                paramV2.paramIsland?.smallIslandArea?.iconKey?.let { iconKey ->
                    val iconUrl = picMap[iconKey]
                    if (iconUrl != null) {
                        val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                            context = appContext,
                            urlOrData = iconUrl,
                            timeoutMs = 5000
                        )
                        
                        if (bitmap != null) {
                            // 缓存图标
                            iconCache.put(iconUrl, bitmap)
                            
                            // 在主线程更新通知
                            withContext(Dispatchers.Main) {
                                updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                            }
                        }
                    }
                }
            }
            // 处理IM图文组件头像
            paramV2.chatInfo != null -> {
                paramV2.chatInfo.picProfile?.let { picKey ->
                    val iconUrl = picMap[picKey]
                    if (iconUrl != null) {
                        val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                            context = appContext,
                            urlOrData = iconUrl,
                            timeoutMs = 5000
                        )
                        
                        if (bitmap != null) {
                            // 缓存图标
                            iconCache.put(iconUrl, bitmap)
                            
                            // 在主线程更新通知
                            withContext(Dispatchers.Main) {
                                updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                            }
                        }
                    }
                }
            }
            // 处理动画文本组件图标
            paramV2.animTextInfo != null -> {
                paramV2.paramIsland?.smallIslandArea?.iconKey?.let { iconKey ->
                    val iconUrl = picMap[iconKey]
                    if (iconUrl != null) {
                        val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                            context = appContext,
                            urlOrData = iconUrl,
                            timeoutMs = 5000
                        )
                        
                        if (bitmap != null) {
                            // 缓存图标
                            iconCache.put(iconUrl, bitmap)
                            
                            // 在主线程更新通知
                            withContext(Dispatchers.Main) {
                                updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                            }
                        }
                    }
                }
            }
            // 处理图片识别组件图标
            paramV2.picInfo != null -> {
                paramV2.picInfo.pic?.let { picKey ->
                    val iconUrl = picMap[picKey]
                    if (iconUrl != null) {
                        val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                            context = appContext,
                            urlOrData = iconUrl,
                            timeoutMs = 5000
                        )
                        
                        if (bitmap != null) {
                            // 缓存图标
                            iconCache.put(iconUrl, bitmap)
                            
                            // 在主线程更新通知
                            withContext(Dispatchers.Main) {
                                updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                            }
                        }
                    }
                }
            }
            // 处理提示组件图标
            paramV2.hintInfo != null -> {
                paramV2.hintInfo.picContent?.let { picKey ->
                    val iconUrl = picMap[picKey]
                    if (iconUrl != null) {
                        val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                            context = appContext,
                            urlOrData = iconUrl,
                            timeoutMs = 5000
                        )
                        
                        if (bitmap != null) {
                            // 缓存图标
                            iconCache.put(iconUrl, bitmap)
                            
                            // 在主线程更新通知
                            withContext(Dispatchers.Main) {
                                updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                            }
                        }
                    }
                }
            }
            // 处理文本按钮组件图标
            paramV2.textButton != null -> {
                paramV2.paramIsland?.smallIslandArea?.iconKey?.let { iconKey ->
                    val iconUrl = picMap[iconKey]
                    if (iconUrl != null) {
                        val bitmap = com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil.loadBitmapSuspend(
                            context = appContext,
                            urlOrData = iconUrl,
                            timeoutMs = 5000
                        )
                        
                        if (bitmap != null) {
                            // 缓存图标
                            iconCache.put(iconUrl, bitmap)
                            
                            // 在主线程更新通知
                            withContext(Dispatchers.Main) {
                                updateNotificationWithAppIcon(sourceId, notificationId, paramV2, bitmap)
                            }
                        }
                    }
                }
            }
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
    
    /**
     * 构建不包含图标的强调样式通知
     */
    private fun buildHighlightStyleNotificationWithoutIcons(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
    ): NotificationCompat.Builder {
        val highlightInfo = paramV2.highlightInfo ?: return builder
        
        // 处理强调图文组件
        try {
            // 更新通知标题和内容
            builder
                .setContentTitle(HtmlCompat.fromHtml(highlightInfo.title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText(HtmlCompat.fromHtml(highlightInfo.content ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
            
            // 直接创建ProgressStyle实例，使用正确的原生API
            val progressStyle = NotificationCompat.ProgressStyle()
                .setProgress(50) // 强调样式默认显示50%进度

            // 从highlightInfo获取颜色（如果有）
            val highlightColor = paramV2.highlightInfo?.colorTitle ?: paramV2.highlightInfo?.colorContent
            val color = highlightColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.BLUE
            
            // 创建进度点列表
            val point25 = NotificationCompat.ProgressStyle.Point(25).setColor(color)
            val point50 = NotificationCompat.ProgressStyle.Point(50).setColor(color)
            
            // 生成进度段列表
            val segment25 = NotificationCompat.ProgressStyle.Segment(25).setColor(color)
            
            val progressSegments = listOf(segment25, segment25, segment25, segment25)
            
            // 设置进度点和进度段
            progressStyle.setProgressPoints(listOf(point25, point50))
            progressStyle.setProgressSegments(progressSegments)

            // 直接调用builder.setStyle方法
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            // 回退到BigTextStyle
            Logger.w(TAG, "使用ProgressStyle失败，回退到BigTextStyle: ${e.message}")
            
            // 更新通知标题和内容
            val title = highlightInfo.title ?: ""
            val content = highlightInfo.content ?: ""
            val bigText = title.ifEmpty { content }
            
            builder
                .setContentTitle(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY))
            
            return builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(HtmlCompat.fromHtml(bigText, HtmlCompat.FROM_HTML_MODE_LEGACY))
            )
        }
    }
    
    /**
     * 构建包含图标的强调样式通知
     */
    private fun buildHighlightStyleNotificationWithIcon(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2,
        picMap: Map<String, String>,
        highlightIcon: Bitmap
    ): NotificationCompat.Builder {
        val highlightInfo = paramV2.highlightInfo ?: return builder
        
        try {
            // 调用不包含图标的方法构建基础强调样式
            buildHighlightStyleNotificationWithoutIcons(builder, paramV2)
            
            // 重新创建ProgressStyle实例，使用正确的原生API
            val progressStyle = NotificationCompat.ProgressStyle()
                .setProgress(50) // 强调样式默认显示50%进度

            // 从highlightInfo获取颜色（如果有）
            val highlightColor = paramV2.highlightInfo?.colorTitle ?: paramV2.highlightInfo?.colorContent
            val color = highlightColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.BLUE
            
            // 创建进度点列表
            val point25 = NotificationCompat.ProgressStyle.Point(25).setColor(color)
            val point50 = NotificationCompat.ProgressStyle.Point(50).setColor(color)
            
            // 生成进度段列表
            val segment25 = NotificationCompat.ProgressStyle.Segment(25).setColor(color)
            
            val progressSegments = listOf(segment25, segment25, segment25, segment25)
            
            // 设置进度点和进度段
            progressStyle.setProgressPoints(listOf(point25, point50))
            progressStyle.setProgressSegments(progressSegments)
            
            // 设置进度跟踪器图标
            progressStyle.setProgressTrackerIcon(IconCompat.createWithBitmap(highlightIcon))
            
            // 直接调用builder.setStyle方法
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            Logger.w(TAG, "构建包含图标的强调样式通知失败: ${e.message}")
            return builder
        }
    }
    
    /**
     * 构建不包含图标的基础样式通知
     */
    private fun buildBasicStyleNotificationWithoutIcons(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2?
    ): NotificationCompat.Builder {
        if (paramV2 == null) return builder
        
        // 处理基础文本组件
        paramV2.baseInfo?.let {baseInfo ->
            val title = baseInfo.title ?: ""
            val content = baseInfo.content ?: ""
            val bigText = title.ifEmpty { content }
            
            return builder
                // 标题和内容使用HTML渲染
                .setContentTitle(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY))
                // BigTextStyle也需要使用HTML渲染
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(HtmlCompat.fromHtml(bigText, HtmlCompat.FROM_HTML_MODE_LEGACY))
                )
        }
        
        // 处理IM图文组件
        paramV2.chatInfo?.let {chatInfo ->
            val title = chatInfo.title ?: ""
            val content = chatInfo.content ?: ""
            val combinedText = "${title}: ${content}"
            
            return builder
                .setContentTitle(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(HtmlCompat.fromHtml(combinedText, HtmlCompat.FROM_HTML_MODE_LEGACY))
                )
        }
        
        // 处理动画文本组件
        paramV2.animTextInfo?.let {animTextInfo ->
            val title = animTextInfo.title ?: ""
            val content = animTextInfo.content ?: ""
            val bigText = title.ifEmpty { content }
            
            return builder
                .setContentTitle(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(HtmlCompat.fromHtml(bigText, HtmlCompat.FROM_HTML_MODE_LEGACY))
                )
        }
        
        // 处理图片识别组件
        paramV2.picInfo?.let {picInfo ->
            val title = picInfo.title ?: ""
            
            return builder
                .setContentTitle(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText("")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                )
        }
        
        // 处理提示组件
        paramV2.hintInfo?.let {hintInfo ->
            val title = hintInfo.title ?: ""
            val content = hintInfo.content ?: ""
            val bigText = title.ifEmpty { content }
            
            return builder
                .setContentTitle(HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setContentText(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(HtmlCompat.fromHtml(bigText, HtmlCompat.FROM_HTML_MODE_LEGACY))
                )
        }
        
        // 处理文本按钮组件
        paramV2.textButton?.let {textButton ->
            // 从actions中获取按钮文本
            val buttonText = textButton.actions?.firstOrNull()?.actionTitle ?: "文本按钮"
            return builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(buttonText)
            )
        }
        
        // 默认使用BigTextStyle
        return builder.setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(builder.build().extras.getString(NotificationCompat.EXTRA_TEXT))
        )
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

    private fun buildBasicStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2?,
        picMap: Map<String, String>? = null
    ): NotificationCompat.Builder {
        // 这里保留原方法，供其他地方调用
        return buildBasicStyleNotificationWithoutIcons(builder, paramV2)
    }

    private fun buildHighlightStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2,
        picMap: Map<String, String>? = null
    ): NotificationCompat.Builder {
        // 这里保留原方法，供其他地方调用
        return buildHighlightStyleNotificationWithoutIcons(builder, paramV2)
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