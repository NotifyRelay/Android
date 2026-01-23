package com.xzyht.notifyrelay.feature.notification.superisland

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.ProgressStyle
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

            // 根据paramV2数据构建不同样式的Live Update，但不包含图标
            when {
                paramV2?.progressInfo != null -> {
                    // 进度条样式
                    buildProgressStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                paramV2?.multiProgressInfo != null -> {
                    // 多进度样式
                    buildProgressStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                paramV2?.highlightInfo != null -> {
                    // 强调样式
                    buildHighlightStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                paramV2?.hintInfo != null -> {
                    // 提示组件（按钮组件2/3）
                    buildBasicStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                paramV2?.textButton != null -> {
                    // 文本按钮组件
                    buildBasicStyleNotificationWithoutIcons(notificationBuilder, paramV2)
                }
                else -> {
                    // 其他基础样式
                    buildBasicStyleNotificationWithoutIcons(notificationBuilder, paramV2)
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

            // 先发送基础通知
            notificationManager.notify(notificationId, notification)
            Logger.i(TAG, "发送基础Live Update通知成功: $sourceId")
            
            // 异步加载图标并更新通知
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
        val pointsCount = multiProgressInfo?.points ?: 4
        val segmentSize = 100 / pointsCount
        
        // 根据进度信息选择合适的图标
        val iconKey = when {
            currentProgress < segmentSize -> {
                progressInfo.picForward ?: multiProgressInfo?.picForward
            }
            currentProgress < segmentSize * 2 -> {
                progressInfo.picMiddle ?: multiProgressInfo?.picMiddle
            }
            currentProgress < segmentSize * 3 -> {
                progressInfo.picMiddle ?: multiProgressInfo?.picMiddle
            }
            else -> {
                progressInfo.picEnd ?: multiProgressInfo?.picEnd
            }
        }
        
        // 加载进度跟踪器图标
        iconKey?.let { key ->
            val iconUrl = picMap[key]
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
                        updateProgressNotificationWithIcon(sourceId, notificationId, paramV2, picMap, bitmap)
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
            // 重新构建进度样式通知，包含图标
            val updatedBuilder = buildBaseNotification(sourceId)
            buildProgressStyleNotificationWithIcon(updatedBuilder, paramV2, picMap, progressIcon)
            
            notificationManager.notify(notificationId, updatedBuilder.build())
            Logger.i(TAG, "更新进度通知图标成功: $sourceId")
        } catch (e: Exception) {
            Logger.w(TAG, "更新进度通知图标失败: ${e.message}")
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
        try {
            // 重新构建通知，添加应用图标
            val updatedBuilder = buildBaseNotification(sourceId)
            // 设置基础信息
            paramV2.baseInfo?.let {
                updatedBuilder
                    .setContentTitle(HtmlCompat.fromHtml(it.title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
                    .setContentText(HtmlCompat.fromHtml(it.content ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY))
            }
            updatedBuilder.setLargeIcon(appIcon)
            
            // 根据通知类型重新构建相应的样式，确保进度样式不丢失
            when {
                paramV2.progressInfo != null || paramV2.multiProgressInfo != null -> {
                    // 进度条样式，重新构建进度样式
                    buildProgressStyleNotificationWithoutIcons(updatedBuilder, paramV2)
                }
                paramV2.highlightInfo != null -> {
                    // 强调样式，重新构建强调样式
                    buildHighlightStyleNotificationWithoutIcons(updatedBuilder, paramV2)
                }
                paramV2.hintInfo != null -> {
                    // 提示组件（按钮组件2/3），重新构建基础样式
                    buildBasicStyleNotificationWithoutIcons(updatedBuilder, paramV2)
                }
                paramV2.textButton != null -> {
                    // 文本按钮组件，重新构建基础样式
                    buildBasicStyleNotificationWithoutIcons(updatedBuilder, paramV2)
                }
                else -> {
                    // 其他基础样式，重新构建基础样式
                    buildBasicStyleNotificationWithoutIcons(updatedBuilder, paramV2)
                }
            }
            
            notificationManager.notify(notificationId, updatedBuilder.build())
            Logger.i(TAG, "更新通知应用图标成功")
        } catch (e: Exception) {
            Logger.w(TAG, "更新通知应用图标失败: ${e.message}")
        }
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
     * 构建不包含图标的进度样式通知
     */
    private fun buildProgressStyleNotificationWithoutIcons(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2
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
            
            // 直接创建ProgressStyle实例
            val progressStyle = ProgressStyle()
                .setProgress(progressInfo.progress)

            // 获取颜色配置
            val progressColor = progressInfo.colorProgress ?: multiProgressInfo?.color
            val progressEndColor = progressInfo.colorProgressEnd ?: multiProgressInfo?.color
            
            // 动态生成进度点
            val progressPoints = mutableListOf<ProgressStyle.Point>()
            val progressSegments = mutableListOf<ProgressStyle.Segment>()
            
            // 根据multiProgressInfo.points动态调整进度点个数
            val pointsCount = multiProgressInfo?.points ?: 4 // 默认4个进度点
            val segmentSize = 100 / pointsCount
            
            // 生成进度点和进度段
            for (i in 1 until pointsCount + 1) {
                val position = i * segmentSize
                val point = ProgressStyle.Point(position) as ProgressStyle.Point
                
                // 设置颜色（如果有）
                progressColor?.let {
                    point.setColor(android.graphics.Color.parseColor(it))
                }
                
                progressPoints.add(point)
            }
            
            // 生成进度段
            for (i in 0 until pointsCount) {
                val segment = ProgressStyle.Segment(segmentSize) as ProgressStyle.Segment
                
                // 设置颜色（如果有）
                progressEndColor?.let {
                    segment.setColor(android.graphics.Color.parseColor(it))
                }
                
                progressSegments.add(segment)
            }
            
            // 根据当前进度确定显示哪些进度点
            val currentProgress = progressInfo.progress
            val visiblePoints = progressPoints.filter { point -> 
                val pointProgress = try {
                    // 使用反射获取进度值，避免编译错误
                    val getProgressMethod = point.javaClass.getMethod("getProgress")
                    getProgressMethod.invoke(point) as Int
                } catch (e: Exception) {
                    // 回退方案：直接返回进度点的位置
                    0
                }
                pointProgress <= currentProgress || pointProgress == 100
            }
            
            // 设置进度点和进度段
            progressStyle.setProgressPoints(visiblePoints)
            progressStyle.setProgressSegments(progressSegments)

            // 直接调用builder.setStyle方法
            return builder.setStyle(progressStyle)
        } catch (e: Exception) {
            // 如果ProgressStyle不可用，回退到简单的进度条
            Logger.w(TAG, "使用ProgressStyle失败，回退到简单进度条: ${e.message}")
            
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
        picMap: Map<String, String>,
        progressIcon: Bitmap
    ): NotificationCompat.Builder {
        val progressInfo = paramV2.progressInfo ?: return builder
        val multiProgressInfo = paramV2.multiProgressInfo
        
        try {
            // 调用不包含图标的方法构建基础进度样式
            buildProgressStyleNotificationWithoutIcons(builder, paramV2)
            
            // 重新获取进度样式
            val progressStyle = ProgressStyle()
                .setProgress(progressInfo.progress)

            // 获取颜色配置
            val progressColor = progressInfo.colorProgress ?: multiProgressInfo?.color
            val progressEndColor = progressInfo.colorProgressEnd ?: multiProgressInfo?.color
            
            // 动态生成进度点
            val progressPoints = mutableListOf<ProgressStyle.Point>()
            val progressSegments = mutableListOf<ProgressStyle.Segment>()
            
            // 根据multiProgressInfo.points动态调整进度点个数
            val pointsCount = multiProgressInfo?.points ?: 4 // 默认4个进度点
            val segmentSize = 100 / pointsCount
            
            // 生成进度点和进度段
            for (i in 1 until pointsCount + 1) {
                val position = i * segmentSize
                val point = ProgressStyle.Point(position) as ProgressStyle.Point
                
                // 设置颜色（如果有）
                progressColor?.let {
                    point.setColor(android.graphics.Color.parseColor(it))
                }
                
                progressPoints.add(point)
            }
            
            // 生成进度段
            for (i in 0 until pointsCount) {
                val segment = ProgressStyle.Segment(segmentSize) as ProgressStyle.Segment
                
                // 设置颜色（如果有）
                progressEndColor?.let {
                    segment.setColor(android.graphics.Color.parseColor(it))
                }
                
                progressSegments.add(segment)
            }
            
            // 根据当前进度确定显示哪些进度点
            val currentProgress = progressInfo.progress
            val visiblePoints = progressPoints.filter { point -> 
                val pointProgress = try {
                    // 使用反射获取进度值，避免编译错误
                    val getProgressMethod = point.javaClass.getMethod("getProgress")
                    getProgressMethod.invoke(point) as Int
                } catch (e: Exception) {
                    // 回退方案：直接返回进度点的位置
                    0
                }
                pointProgress <= currentProgress || pointProgress == 100
            }
            
            // 设置进度点和进度段
            progressStyle.setProgressPoints(visiblePoints)
            progressStyle.setProgressSegments(progressSegments)
            
            // 设置进度跟踪器图标
            progressStyle.setProgressTrackerIcon(IconCompat.createWithBitmap(progressIcon))
            
            // 直接调用builder.setStyle方法
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
            
            // 直接创建ProgressStyle实例
            val progressStyle = ProgressStyle()
                .setProgress(50) // 强调样式默认显示50%进度

            // 创建进度点列表
            val point25 = ProgressStyle.Point(25) as ProgressStyle.Point
            val point50 = ProgressStyle.Point(50) as ProgressStyle.Point
            
            // 生成进度段列表
            val segment25 = ProgressStyle.Segment(25) as ProgressStyle.Segment
            
            // 从highlightInfo获取颜色（如果有）
            val highlightColor = paramV2.highlightInfo?.colorTitle ?: paramV2.highlightInfo?.colorContent
            highlightColor?.let { colorString: String ->
                val color = android.graphics.Color.parseColor(colorString)
                point25.setColor(color)
                point50.setColor(color)
                segment25.setColor(color)
            }
            
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
            
            // 重新创建ProgressStyle实例
            val progressStyle = ProgressStyle()
                .setProgress(50) // 强调样式默认显示50%进度

            // 创建进度点列表
            val point25 = ProgressStyle.Point(25) as ProgressStyle.Point
            val point50 = ProgressStyle.Point(50) as ProgressStyle.Point
            
            // 生成进度段列表
            val segment25 = ProgressStyle.Segment(25) as ProgressStyle.Segment
            
            // 从highlightInfo获取颜色（如果有）
            val highlightColor = paramV2.highlightInfo?.colorTitle ?: paramV2.highlightInfo?.colorContent
            highlightColor?.let { colorString: String ->
                val color = android.graphics.Color.parseColor(colorString)
                point25.setColor(color)
                point50.setColor(color)
                segment25.setColor(color)
            }
            
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
        // 根据paramV2中的不同数据类型，构建不同的基础样式
        paramV2?.let {param ->
            // 处理基础文本组件
            param.baseInfo?.let {baseInfo ->
                // 处理HTML标签
                val title = baseInfo.title ?: ""
                val content = baseInfo.content ?: ""
                
                // 为BigTextStyle单独处理HTML，确保颜色标签被正确渲染
                val bigText = if (title.isNotEmpty()) title else content
                
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
            param.chatInfo?.let {chatInfo ->
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
            param.animTextInfo?.let {animTextInfo ->
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
            param.picInfo?.let {picInfo ->
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
            param.hintInfo?.let {hintInfo ->
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
            param.textButton?.let {textButton ->
                // 从actions中获取按钮文本
                val buttonText = textButton.actions?.firstOrNull()?.actionTitle ?: "文本按钮"
                return builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(buttonText)
                )
            }
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
            // 添加Live Updates所需的额外属性
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // 设置默认小图标，确保所有通知都有有效的小图标
            .setSmallIcon(android.R.drawable.stat_notify_more)

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
        paramV2: ParamV2?,
        picMap: Map<String, String>? = null
    ): NotificationCompat.Builder {
        // 这里保留原方法，供其他地方调用
        return buildBasicStyleNotificationWithoutIcons(builder, paramV2)
    }

    private fun buildProgressStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2,
        picMap: Map<String, String>? = null
    ): NotificationCompat.Builder {
        // 这里保留原方法，供其他地方调用
        return buildProgressStyleNotificationWithoutIcons(builder, paramV2)
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