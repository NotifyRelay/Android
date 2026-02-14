package com.xzyht.notifyrelay.feature.notification.superisland.common

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.NotificationBroadcastReceiver
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.TimerInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.parseParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.left.AComponent
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.left.AImageText1
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.left.AImageText5
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BComponent
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BEmpty
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BFixedWidthDigitInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BImageText2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BImageText3
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BImageText6
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BPicInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BProgressTextInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BSameWidthDigitInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BTextInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.formatTimerInfo
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 计时器更新管理器，负责定期更新包含计时器的通知
 */
class TimerUpdateManager private constructor() {
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(5)
    private val timers = ConcurrentHashMap<String, ScheduledFuture<*>?>()
    
    /**
     * 定时器任务
     */
    private class TimerTask(
        private val contextRef: WeakReference<Context>,
        private val notificationId: Int,
        private val timerInfo: TimerInfo,
        private val onComplete: () -> Unit
    ) : Runnable {
        override fun run() {
            try {
                val context = contextRef.get() ?: run {
                    onComplete()
                    return
                }
                
                // 检查计时器是否已停止
                if (timerInfo.timerType == 0) {
                    onComplete()
                    return
                }
                
                // 计算当前计时器时间
                val timerText = formatTimerInfo(timerInfo)
                
                // 获取通知管理器
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // 创建通知渠道（如果不存在）
                val channelId = "super_island_replica"
                val channel = android.app.NotificationChannel(
                    channelId,
                    "超级岛复刻通知",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
                
                // 创建通知构建器，确保设置了所有必要的属性
                val builder = NotificationCompat.Builder(context, channelId)
                    // 设置必要的文本内容
                    .setContentText(timerText)
                    .setShortCriticalText(timerText)
                    // 设置为ongoing，保持通知不可清除
                    .setOngoing(true)
                    // 保持高优先级
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    // 不显示时间
                    .setShowWhen(false)
                    // 只提示一次
                    .setOnlyAlertOnce(true)
                    // 公开可见
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                
                // 根据计时器类型设置自动计时功能
                when (timerInfo.timerType) {
                    -1 -> {
                        // 倒计时进行中：使用系统的自动倒计时功能
                        builder.setUsesChronometer(true)
                            .setChronometerCountDown(true)
                            .setWhen(timerInfo.timerWhen) // 设置为倒计时结束时间
                    }
                    1 -> {
                        // 正计时进行中：使用系统的自动正计时功能
                        builder.setUsesChronometer(true)
                            .setChronometerCountDown(false)
                            .setWhen(timerInfo.timerWhen) // 设置为正计时开始时间
                    }
                    else -> {
                        // 暂停或停止状态：不使用自动计时功能
                        builder.setWhen(System.currentTimeMillis())
                    }
                }
                
                // 更新通知
                notificationManager.notify(notificationId, builder.build())
                
                // 检查是否需要停止（倒计时结束或计时器已暂停）
                if (timerInfo.timerType == -1 || timerInfo.timerType == -2) {
                    val now = System.currentTimeMillis()
                    if (now >= timerInfo.timerWhen) {
                        onComplete()
                    }
                } else if (timerInfo.timerType == 2) {
                    // 正计时已暂停，停止更新
                    onComplete()
                }
                
                // 记录更新日志
                Logger.d("TimerUpdateManager", "更新计时器通知成功，notificationId=$notificationId, timerText=$timerText")
            } catch (e: Exception) {
                Logger.e("TimerUpdateManager", "更新计时器通知失败", e)
                onComplete()
            }
        }
    }
    
    /**
     * 添加或更新计时器通知
     */
    fun addOrUpdateTimer(
        key: String,
        context: Context,
        notificationId: Int,
        timerInfo: TimerInfo
    ) {
        // 移除旧的定时器
        removeTimer(key)
        
        // 创建新的定时器任务
        val contextRef = WeakReference(context)
        val task = TimerTask(contextRef, notificationId, timerInfo) { 
            removeTimer(key) 
        }
        
        // 立即执行一次，然后每秒执行一次
        val future = executor.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS)
        timers[key] = future
        
        // 记录定时器创建日志
        Logger.d("TimerUpdateManager", "创建计时器更新定时器成功，key=$key, notificationId=$notificationId")
    }
    
    /**
     * 移除计时器通知
     */
    fun removeTimer(key: String) {
        timers.remove(key)?.cancel(false)
    }
    
    /**
     * 清理所有定时器
     */
    fun clearAll() {
        timers.forEach { (_, future) ->
            future?.cancel(false)
        }
        timers.clear()
    }
    
    /**
     * 停止executor，释放资源
     */
    fun shutdown() {
        clearAll()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
    
    companion object {
        val instance by lazy { TimerUpdateManager() }
    }
}



/**
 * 通知生成器，负责处理超级岛通知的生成和注入
 */
object NotificationGenerator {
    private const val TAG = "超级岛通知生成"
    // 通知渠道ID
    private const val NOTIFICATION_CHANNEL_ID = "super_island_replica"
    // 通知ID基础值
    private const val NOTIFICATION_BASE_ID = 20000
    
    // 缓存变量，用于优化图标生成
    private var cachedIconKey = ""
    private var cachedIconBitmap: android.graphics.Bitmap? = null
    
    // 滚动更新相关
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scrollRunnables = mutableMapOf<String, Runnable>()
    
    /**
     * 设置滚动更新
     */
    private fun setupScrollUpdate(
        key: String,
        scrollKey: String,
        capsuleText: String,
        context: Context,
        notificationId: Int,
        originalBuilder: NotificationCompat.Builder,
        notificationManager: NotificationManager,
        floatingWindowManager: FloatingWindowManager,
        entryKeyToNotificationId: ConcurrentHashMap<String, Int>
    ) {
        // 移除旧的滚动Runnable
        scrollRunnables.remove(key)?.let {
            mainHandler.removeCallbacks(it)
        }
        
        // 创建新的滚动Runnable
        val scrollRunnable = Runnable {
            try {
                // 检查是否需要更新
                if (!CapsuleScrollManager.shouldUpdateNotification(scrollKey)) {
                    return@Runnable
                }
                
                // 获取当前应该显示的文本
                val displayText = CapsuleScrollManager.getCurrentDisplayText(scrollKey, capsuleText)
                
                // 构建原始通知以获取其属性
                val originalNotification = originalBuilder.build()
                
                // 获取原始通知的标题和内容
                val contentTitle = originalNotification.extras.getString("android.title")
                val contentText = originalNotification.extras.getString("android.text")
                
                // 更新通知
                val updatedBuilder = NotificationCompat.Builder(context, "channel_id_focusNotifLyrics")
                    .setContentTitle(contentTitle ?: "")
                    .setContentText(contentText ?: "")
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setShowWhen(false)
                    .setWhen(System.currentTimeMillis())
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setRequestPromotedOngoing(true)
                    .setShortCriticalText(displayText)
                
                // 复制extras
                val updatedNotification = updatedBuilder.build()
                updatedNotification.extras.putAll(originalNotification.extras)
                
                // 发送更新后的通知
                notificationManager.notify(notificationId, updatedNotification)
                
                // 继续调度下一次更新
                val delay = CapsuleScrollManager.getScrollDelay(scrollKey)
                mainHandler.postDelayed(scrollRunnables[key]!!, delay)
            } catch (e: Exception) {
                Logger.e(TAG, "滚动更新失败", e)
            }
        }
        
        // 存储Runnable
        scrollRunnables[key] = scrollRunnable
        
        // 调度第一次更新，初始延迟为0，确保滚动直接开始
        mainHandler.postDelayed(scrollRunnable, 0)
    }
    
    /**
     * 停止滚动更新
     */
    fun stopScrollUpdate(key: String) {
        scrollRunnables.remove(key)?.let {
            mainHandler.removeCallbacks(it)
        }
        CapsuleScrollManager.resetScrollState("${key}_scroll")
    }
    
    /**
     * 清理所有滚动更新
     */
    fun clearAllScrollUpdates() {
        scrollRunnables.forEach {
            mainHandler.removeCallbacks(it.value)
        }
        scrollRunnables.clear()
        CapsuleScrollManager.clearAll()
    }
    


    /**
     * 发送复刻通知，与原通知保持一致
     */
    internal suspend fun sendReplicaNotification(
        context: Context,
        key: String,
        title: String?,
        text: String?,
        appName: String?,
        paramV2: ParamV2?,
        picMap: Map<String, String>?,
        sourceId: String, // 新增sourceId参数
        floatingWindowManager: FloatingWindowManager,
        entryKeyToNotificationId: ConcurrentHashMap<String, Int>
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 生成唯一的通知ID
            val notificationId = key.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            
            // 创建点击意图，用于处理用户点击通知时切换浮窗显示/隐藏
            val contentIntent = Intent(context, NotificationBroadcastReceiver::class.java).apply {
                action = "com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING"
                putExtra("sourceId", sourceId)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("appName", appName)
                putExtra("paramV2Raw", paramV2?.toString()) // 注意：这里可能需要原始的json字符串，但paramV2是对象。如果需要原始串，应该在参数中传入
                // 优化：传入paramV2Raw
                val entry = floatingWindowManager.getEntry(key)
                if (entry?.paramV2Raw != null) {
                    putExtra("paramV2Raw", entry.paramV2Raw)
                }
                
                // 传入图片映射
                if (!picMap.isNullOrEmpty()) {
                    val bundle = Bundle()
                    picMap.forEach { (k, v) -> bundle.putString(k, v) }
                    putExtra("picMap", bundle)
                }
            }
            
            val pendingContentIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 检查是否为媒体类型的超级岛浮窗
            val isMediaType = paramV2?.business == "media"

            // 创建删除意图，用于处理用户移除通知时关闭浮窗
            val deleteIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                Intent(context, NotificationBroadcastReceiver::class.java)
                    .putExtra("notificationId", notificationId)
                    .setAction("com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 对于媒体类型，使用HyperCeiler焦点歌词的特殊处理
            if (isMediaType) {
                // 创建媒体类型通知渠道
                val mediaChannel = android.app.NotificationChannel(
                    "channel_id_focusNotifLyrics",
                    "焦点歌词通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(mediaChannel)
                
                var builder = NotificationCompat.Builder(context, "channel_id_focusNotifLyrics")
                    .setContentTitle(appName ?: "媒体应用") // 使用实际应用名作为通知标题
                    .setContentText(title ?: "")
                    .setSmallIcon(android.R.drawable.stat_notify_more) // TODO: 使用应用自己的小图标
                    // 调整为不可被一键清除的属性，只能手动划去
                    .setAutoCancel(false) // 不允许用户点击清除通知
                    .setOngoing(true) // 不允许通知被一键清除
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setShowWhen(false)
                    .setWhen(System.currentTimeMillis())
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setDeleteIntent(deleteIntent) // 设置删除意图，处理用户移除通知的情况
                    .setContentIntent(pendingContentIntent) // 设置点击意图
                    .setRequestPromotedOngoing(true)
                
                // 添加胶囊形式支持
                try {
                    // 使用 ProgressStyle 设置胶囊样式
                    val segment = NotificationCompat.ProgressStyle.Segment(100)
                    val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                    segments.add(segment)
                    
                    val progressStyle = NotificationCompat.ProgressStyle()
                        .setProgressSegments(segments)
                        .setStyledByProgress(true)
                        .setProgress(0)
                    
                    builder.setStyle(progressStyle)
                } catch (e: Exception) {
                    Logger.e(TAG, "设置胶囊样式失败: ${e.message}")
                }
                
                // 处理歌词拆分和显示
                val lyricText = title ?: ""
                var capsuleText = lyricText
                var iconText = ""
                
                // 检查是否为本地传递
                val isLocalTransmit = try {
                    // 从现有entry中获取paramV2Raw
                    val entry = floatingWindowManager.getEntry(key)
                    val paramV2RawValue = entry?.paramV2Raw
                    val paramV2Json = JSONObject(paramV2RawValue ?: paramV2?.toString() ?: "{}")
                    paramV2Json.optBoolean("localTransmit", false)
                } catch (_: Exception) {
                    false
                }
                
                // 当歌词超过阈值时，拆分为图标文本和胶囊文本
                // 远端和本地都保持6字符开始分割
                val threshold = 6
                if (lyricText.length > threshold) {
                    // 使用TextSplitter工具类进行歌词拆分
                    val (splitIconText, splitCapsuleText) = TextSplitter.splitLyricWithCharacterType(lyricText, threshold)
                    iconText = splitIconText
                    capsuleText = splitCapsuleText
                }
                
                // 使用CapsuleScrollManager处理胶囊文本滚动
                val scrollKey = "${key}_scroll"
                val displayText = CapsuleScrollManager.getCurrentDisplayText(scrollKey, capsuleText)
                
                // 设置胶囊文本
                builder.setShortCriticalText(displayText)
                
                // 设置滚动更新机制
                setupScrollUpdate(key, scrollKey, capsuleText, context, notificationId, originalBuilder = builder, notificationManager, floatingWindowManager, entryKeyToNotificationId)
                
                // ... (后续构建extras的代码保持不变)
                // 添加焦点歌词相关的结构化数据
                val extras = builder.extras
                
                // 构建符合HyperIslandApi标准的媒体类型miui.focus.param，优化数据结构
                val fullFocusParam = JSONObject().apply {
                    put("protocol", 1)
                    put("scene", "music") // 媒体类型固定使用music场景
                    put("ticker", title ?: "")
                    put("content", text ?: "")
                    put("enableFloat", false)
                    put("updatable", true)
                    put("reopen", "close")
                    
                    // 媒体类型需要的animTextInfo字段
                    put("animTextInfo", JSONObject().apply {
                        put("title", title ?: "")
                        put("content", text ?: "")
                    })
                    
                    // 优化媒体类型param_v2结构，符合小米官方标准
                    val paramV2Json = JSONObject().apply {
                        put("business", "music")
                        put("protocol", 1)
                        put("scene", "music")
                        put("ticker", title ?: "")
                        put("content", text ?: "")
                        put("enableFloat", false)
                        put("updatable", true)
                        put("reopen", "close")
                        put("timerType", 0)
                        put("timerWhen", 0)
                        put("timerSystemCurrent", 0)
                        
                        // 媒体类型必须包含的baseInfo字段
                        put("baseInfo", JSONObject().apply {
                            put("title", title ?: "")
                            put("content", text ?: "")
                        })
                    }
                    
                    put("param_v2", paramV2Json)
                }
                
                extras.putString("miui.focus.param", fullFocusParam.toString())
                
                // 添加其他必要的焦点通知字段
                extras.putBoolean("miui.showAction", true)
                
                // 添加模拟的action字段
                val actionsBundle = Bundle()
                actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
                actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
                extras.putBundle("miui.focus.actions", actionsBundle)
                
                // 添加图片资源
                picMap?.let { map ->
                    // 按照小米官方文档规范，将每个图片资源作为单独的extra添加
                    map.forEach { (picKey, picUrl) ->
                        if (picKey.startsWith("miui.focus.pic_")) {
                            val actualPicUrl = SuperIslandImageStore.resolve(context, picUrl) ?: picUrl
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
                }
                
                // 添加应用信息
                extras.putBoolean("android.reduced.images", true)
                extras.putString("superIslandSourcePackage", context.packageName)
                extras.putString("app_package", context.packageName)
                
                // 构建通知
                val notification = builder.build()
                
                // 生成并注入动态图标
                if (iconText.isNotEmpty()) {
                    val iconBitmap = textToBitmap(iconText)
                    if (iconBitmap != null) {
                        injectSmallIcon(notification, iconBitmap)
                    }
                } else {
                    // 没有图标文本时，尝试使用专辑图作为小图标
                    val coverKey = "miui.focus.pic_cover"
                    if (!picMap.isNullOrEmpty() && picMap.containsKey(coverKey)) {
                        val coverUrl = picMap[coverKey]
                        if (!coverUrl.isNullOrBlank()) {
                            // 同步下载专辑图
                            val bitmap = runBlocking {
                                downloadBitmap(context, coverUrl, 5000)
                            }
                            if (bitmap != null) {
                                injectSmallIcon(notification, bitmap)
                            }
                        }
                    }
                }
                
                // 重新解析param_v2数据，获取进度信息
                val progressEntry = floatingWindowManager.getEntry(key)
                val paramV2Raw = progressEntry?.paramV2Raw
                
                // 检查是否已经有图标文本，如果有，就不再生成新的图标
                if (iconText.isEmpty()) {
                    // 尝试从A/B区数据中获取图标或生成位图
                    var smallIconBitmap: android.graphics.Bitmap? = null
                    
                    // 解析param_v2中的bigIsland数据
                    var bigIsland: JSONObject? = null
                    val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
                    
                    paramV2RawValue?.let {
                        try {
                            val json = JSONObject(it)
                            // 尝试从param_island -> bigIslandArea中解析
                            val paramIsland = json.optJSONObject("param_island")
                            bigIsland = paramIsland?.optJSONObject("bigIslandArea")
                            
                            // 如果没有找到，尝试直接从bigIsland字段解析
                            if (bigIsland == null) {
                                bigIsland = json.optJSONObject("bigIsland")
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG, "超级岛: 解析bigIsland失败: ${e.message}")
                        }
                    }
                    
                    // 解析A/B区数据
                    val bComponent = parseBComponent(bigIsland)
                    
                    // 提取进度数据
                    val bProgress = when (bComponent) {
                        is BProgressTextInfo -> bComponent.progress
                        else -> null
                    }
                    
                    val bProgressColorReach = when (bComponent) {
                        is BProgressTextInfo -> bComponent.colorReach
                        else -> null
                    }
                    
                    val bProgressColorUnReach = when (bComponent) {
                        is BProgressTextInfo -> bComponent.colorUnReach
                        else -> null
                    }
                    
                    val bProgressIsCCW = when (bComponent) {
                        is BProgressTextInfo -> bComponent.isCCW
                        else -> false
                    }
                    
                    // 处理进度数据，生成位图
                    if (bProgress != null) {
                        smallIconBitmap = progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
                    }
                    
                    // 如果没有进度数据，尝试生成文本位图
                    if (smallIconBitmap == null) {
                        // 优先使用B区文本生成位图
                        val textToRender = when (bComponent) {
                            is BImageText2 -> bComponent.title ?: bComponent.content
                            is BImageText3 -> bComponent.title
                            is BImageText6 -> bComponent.title
                            is BTextInfo -> bComponent.title ?: bComponent.content
                            is BFixedWidthDigitInfo -> bComponent.digit
                            is BSameWidthDigitInfo -> bComponent.digit
                            is BProgressTextInfo -> bComponent.title ?: bComponent.content
                            else -> null
                        }
                        
                        if (!textToRender.isNullOrBlank()) {
                            smallIconBitmap = textToBitmap(textToRender)
                        }
                    }
                    
                    // 如果没有文本数据，尝试使用应用图标
                    if (smallIconBitmap == null) {
                        // 优先使用应用图标（大图标的键值提供的图标）
                        val appIconKey = "miui.focus.pic_app_icon"
                        if (!picMap.isNullOrEmpty() && picMap.containsKey(appIconKey)) {
                            val appIconUrl = picMap[appIconKey]
                            if (!appIconUrl.isNullOrBlank()) {
                                // 同步下载应用图标
                                val bitmap = runBlocking {
                                    downloadBitmap(context, appIconUrl, 5000)
                                }
                                if (bitmap != null) {
                                    smallIconBitmap = bitmap
                                }
                            }
                        }
                    }
                    
                    // 注入小图标
                    // 只有当smallIconBitmap不为null时才注入，否则保留之前的图标
                    if (smallIconBitmap != null) {
                        injectSmallIcon(notification, smallIconBitmap)
                    } else {
                        // 保留之前的图标，不进行修改
                        Logger.i(TAG, "超级岛: 保留之前的小图标，不进行修改")
                    }
                } else {
                    // 已经有图标文本，保留之前的图标，不进行修改
                    Logger.i(TAG, "超级岛: 已有图标文本，保留之前的小图标，不进行修改")
                }
                
                // 发送通知
                notificationManager.notify(notificationId, notification)
            } else {
                // 非媒体类型，使用原来的通知渠道和构建方式
                // 创建通知渠道
                val channel = android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "超级岛复刻通知",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
                
                // 构建基础通知，调整属性使其更接近实际超级岛通知
                var builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(title ?: appName ?: "超级岛通知")
                    .setContentText(text ?: "")
                    .setSmallIcon(android.R.drawable.stat_notify_more) // TODO: 使用应用自己的小图标
                    // 调整为与实际超级岛通知一致的属性
                    .setAutoCancel(false) // 实际通知通常不可清除
                    .setOngoing(true) // 实际通知通常是持续的
                    .setPriority(NotificationCompat.PRIORITY_MAX) // 提高优先级到最高，与原始通知一致
                    .setShowWhen(false) // 不显示时间，与原始通知一致
                    .setWhen(System.currentTimeMillis()) // 设置时间，但不显示
                    .setOnlyAlertOnce(true) // 只提示一次
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 公开可见
                    .setDeleteIntent(deleteIntent) // 设置删除意图
                    .setContentIntent(pendingContentIntent) // 设置点击意图
                
                // 检查是否为进度类型通知，如果是，则可能已经通过 LiveUpdatesNotificationManager 处理
                val isProgressType = paramV2?.progressInfo != null || paramV2?.multiProgressInfo != null
                
                // 构建通知
                val notification = if (!isProgressType) {
                    // 非进度类型通知，添加胶囊兼容字段并注入图标
                    val entry = floatingWindowManager.getEntry(key)
                    val paramV2RawValue = entry?.paramV2Raw
                    buildCapsuleCompatibleNotificationWithIconInjection(context, builder, title, text, appName, paramV2, picMap, paramV2RawValue)
                } else {
                    // 进度类型通知，已经通过 LiveUpdatesNotificationManager 处理，不重复添加胶囊兼容字段
                    Logger.i(TAG, "超级岛: 进度类型通知，已通过 LiveUpdatesNotificationManager 处理，不重复添加胶囊兼容字段")
                    // 构建通知
                    val builtNotification = builder.build()
                    // 尝试从 A/B 区数据中获取图标或生成位图
                    var smallIconBitmap: android.graphics.Bitmap? = null
                    
                    // 解析 param_v2 中的 bigIsland 数据
                    var bigIsland: JSONObject? = null
                    val entry = floatingWindowManager.getEntry(key)
                    val paramV2RawValue = entry?.paramV2Raw ?: paramV2?.toString()
                    
                    paramV2RawValue?.let {
                        try {
                            val json = JSONObject(it)
                            // 尝试从 param_island -> bigIslandArea 中解析
                            val paramIsland = json.optJSONObject("param_island") 
                            bigIsland = paramIsland?.optJSONObject("bigIslandArea")
                            
                            // 如果没有找到，尝试直接从 bigIsland 字段解析
                            if (bigIsland == null) {
                                bigIsland = json.optJSONObject("bigIsland")
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG, "超级岛: 解析 bigIsland 失败: ${e.message}")
                        }
                    }
                    
                    // 解析 A/B 区数据
                    val aComponent = parseAComponent(bigIsland)
                    val bComponent = parseBComponent(bigIsland)
                    
                    // 提取 A/B 区数据
                    val aPicKey = when (aComponent) {
                        is AImageText1 -> aComponent.picKey
                        is AImageText5 -> aComponent.picKey
                        else -> null
                    }
                    
                    val bPicKey = when (bComponent) {
                        is BImageText2 -> bComponent.picKey
                        is BImageText3 -> bComponent.picKey
                        is BImageText6 -> bComponent.picKey
                        is BProgressTextInfo -> bComponent.picKey
                        else -> null
                    }
                    
                    // 处理图标
                    // 优先使用应用图标（大图标的键值提供的图标）
                    val appIconKey = "miui.focus.pic_app_icon"
                    if (!picMap.isNullOrEmpty() && picMap.containsKey(appIconKey)) {
                        val appIconUrl = picMap[appIconKey]
                        if (!appIconUrl.isNullOrBlank()) {
                            // 同步下载应用图标
                            val bitmap = runBlocking {
                                downloadBitmap(context, appIconUrl, 5000)
                            }
                            if (bitmap != null) {
                                smallIconBitmap = bitmap
                            }
                        }
                    }
                    
                    // 如果没有应用图标，再使用 A 区图标
                    if (smallIconBitmap == null) {
                        // 优先使用 A 区图标
                        val picKeyToUse = aPicKey ?: bPicKey
                        if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
                            val picUrl = picMap[picKeyToUse]
                            if (!picUrl.isNullOrBlank()) {
                                // 同步下载图标
                                val bitmap = runBlocking {
                                    downloadBitmap(context, picUrl, 5000)
                                }
                                if (bitmap != null) {
                                    smallIconBitmap = bitmap
                                }
                            }
                        }
                    }
                    
                    // 注入小图标
                    if (smallIconBitmap != null) {
                        injectSmallIcon(builtNotification, smallIconBitmap)
                    }
                    
                    builtNotification
                }
                
                // ... (后续构建extras的代码保持不变)
                // 添加超级岛相关的结构化数据，严格按照小米官方文档规范和实际通知结构
                val extras = notification.extras
                
                // 获取paramV2原始数据
                val entry = floatingWindowManager.getEntry(key)
                val paramV2Raw = entry?.paramV2Raw
                
                // 构建符合小米官方规范的完整miui.focus.param结构
                paramV2Raw?.let {
                    try {
                        // 解析原始paramV2数据
                        val paramV2Json = JSONObject(it)
                        
                        // 构建完整的焦点通知参数结构，包含外层scene、ticker等字段
                        val fullFocusParam = JSONObject().apply {
                            put("protocol", 1)
                            put("scene", paramV2Json.optString("business", "default"))
                            put("ticker", title ?: "")
                            put("content", text ?: "")
                            put("timerType", 0)
                            put("timerWhen", 0)
                            put("timerSystemCurrent", 0)
                            put("enableFloat", false)
                            put("updatable", true)
                            put("param_v2", paramV2Json) // 将原始paramV2作为嵌套字段
                        }
                        
                        extras.putString("miui.focus.param", fullFocusParam.toString())
                    } catch (e: Exception) {
                        // 如果构建完整结构失败，回退到直接使用原始数据
                        extras.putString("miui.focus.param", it)
                    }
                }
                
                // 按照小米官方文档规范，将每个图片资源作为单独的extra添加
                picMap?.let {map ->
                    map.forEach { (picKey, picUrl) ->
                        // 确保key以"miui.focus.pic_"前缀开头，符合小米规范
                        if (picKey.startsWith("miui.focus.pic_")) {
                            // 解析图片引用符，获取实际的图片数据
                            val actualPicUrl = SuperIslandImageStore.resolve(context, picUrl) ?: picUrl
                            extras.putString(picKey, actualPicUrl)
                        }
                    }
                    
                    // 添加miui.focus.pics字段，包含所有图片资源的Bundle
                    val picsBundle = Bundle()
                    map.forEach { (picKey, picUrl) ->
                        if (picKey.startsWith("miui.focus.pic_")) {
                            // 这里简化处理，实际应该创建Icon对象
                            picsBundle.putString(picKey, picUrl)
                        }
                    }
                    extras.putBundle("miui.focus.pics", picsBundle)
                }
                
                // 添加焦点通知必要的额外字段
                extras.putBoolean("miui.showAction", true)
                
                // 添加模拟的action字段，实际应该包含真实的Notification.Action对象
                val actionsBundle = Bundle()
                actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
                actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
                extras.putBundle("miui.focus.actions", actionsBundle)
                
                // 添加原始通知中存在的其他字段，这些可能影响UI显示
                // 对于计时器类通知，添加计时器相关字段
                if (title?.contains("计时") == true || title?.contains("秒表") == true) {
                    // 解析B区数据，检查是否为计时器类型
                    val bigIsland: JSONObject? = try {
                        val paramV2RawValue = entry?.paramV2Raw ?: paramV2?.toString()
                        paramV2RawValue?.let {
                            val json = JSONObject(it)
                            val paramIsland = json.optJSONObject("param_island")
                            paramIsland?.optJSONObject("bigIslandArea") ?: json.optJSONObject("bigIsland")
                        }
                    } catch (e: Exception) {
                        null
                    }
                    
                    val bComponent = parseBComponent(bigIsland)
                    if (bComponent is BSameWidthDigitInfo && bComponent.timer != null) {
                        // 根据timerType设置计时模式
                        val timer = bComponent.timer
                        val timerType = timer.timerType
                        // timerType: -2倒计时暂停，-1倒计时开始，0默认，1正计时开始，2正计时暂停
                        
                        // 只对正在进行中的计时器启用自动流逝
                        if (timerType == -1 || timerType == 1) {
                            val isCountDown = timerType < 0
                            
                            // 使用NotificationCompat的计时器功能
                            if (isCountDown) {
                                // 倒计时：计算剩余时间并设置
                                val now = System.currentTimeMillis()
                                val remaining = timer.timerWhen - now
                                if (remaining > 0) {
                                    // 对于倒计时，我们需要特殊处理
                                    // 由于通知系统的限制，我们使用当前时间作为基准
                                    builder.setUsesChronometer(true)
                                    builder.setChronometerCountDown(true)
                                    // 设置倒计时的终点时间
                                    builder.setWhen(timer.timerWhen)
                                }
                            } else {
                                // 正计时：使用timerWhen作为起点
                                builder.setUsesChronometer(true)
                                builder.setChronometerCountDown(false)
                                // 设置正计时的起点时间
                                builder.setWhen(timer.timerWhen)
                            }
                        }
                    } else {
                        // 非计时器类型或计时器已暂停，不启用自动流逝
                        builder.setUsesChronometer(true)
                        builder.setChronometerCountDown(false)
                        builder.setWhen(System.currentTimeMillis())
                    }
                }
                
                // 添加应用信息，与原始通知保持一致
                extras.putBoolean("android.reduced.images", true)
                
                // 添加超级岛源包信息，与原始通知保持一致
                extras.putString("superIslandSourcePackage", context.packageName)
                
                // 包名信息
                extras.putString("app_package", context.packageName)
                
                // 发送通知
                notificationManager.notify(notificationId, notification)
                
                // 检查是否需要创建计时器更新定时器
                val bigIsland: JSONObject? = try {
                    val paramV2RawValue = entry?.paramV2Raw ?: paramV2?.toString()
                    paramV2RawValue?.let {
                        val json = JSONObject(it)
                        val paramIsland = json.optJSONObject("param_island")
                        paramIsland?.optJSONObject("bigIslandArea") ?: json.optJSONObject("bigIsland")
                    }
                } catch (e: Exception) {
                    null
                }
                
                val bComponent = parseBComponent(bigIsland)
                if (bComponent is BSameWidthDigitInfo && bComponent.timer != null) {
                    val timer = bComponent.timer
                    // 检查计时器是否已停止
                    if (timer.timerType == 0) {
                        // 计时器已停止，移除定时器
                        TimerUpdateManager.instance.removeTimer(key)
                        Logger.i(TAG, "超级岛: 计时器已停止，移除定时器，key=$key")
                    } else {
                        // 创建或更新计时器更新定时器
                        // 创建一个新的通知构建器，基于当前通知的内容
                        val updatedBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                            // 复制原始构建器的所有属性
                            .setContentTitle(title ?: appName ?: "超级岛通知")
                            .setContentText(text ?: "")
                            .setSmallIcon(android.R.drawable.stat_notify_more) // 使用默认图标作为基础
                            .setAutoCancel(false)
                            .setOngoing(true)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setShowWhen(false)
                            .setWhen(System.currentTimeMillis())
                            .setOnlyAlertOnce(true)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setDeleteIntent(deleteIntent)
                            .setContentIntent(pendingContentIntent)
                        
                        // 创建或更新计时器更新定时器
                        TimerUpdateManager.instance.addOrUpdateTimer(
                            key,
                            context,
                            notificationId,
                            timer
                        )
                        Logger.i(TAG, "超级岛: 创建计时器更新定时器成功，key=$key")
                    }
                } else {
                    // 没有计时器信息或不是计时器类型，移除定时器
                    TimerUpdateManager.instance.removeTimer(key)
                    Logger.i(TAG, "超级岛: 没有计时器信息或不是计时器类型，移除定时器，key=$key")
                }
        }
        
        // 保存entryKey到notificationId的映射
        entryKeyToNotificationId[key] = notificationId
        
        Logger.i(TAG, "超级岛: 发送复刻通知成功，key=$key, notificationId=$notificationId")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 发送复刻通知失败: ${e.message}")
        }
    }

    // ---- 胶囊兼容数据解析方法 ----

    /**
     * 解析 A 区（imageTextInfoLeft），参考 AParser.kt
     */
    private fun parseAComponent(bigIsland: JSONObject?): AComponent? {
        val left = bigIsland?.optJSONObject("imageTextInfoLeft") ?: return null
        val type = left.optInt("type", 0)

        return when (type) {
            1 -> {
                val textInfo = left.optJSONObject("textInfo")
                val title = left.optString("title", "").takeIf { it.isNotBlank() }
                    ?: textInfo?.optString("title", "")?.takeIf { it.isNotBlank() }
                val content = left.optString("content", "").takeIf { it.isNotBlank() }
                    ?: textInfo?.optString("content", "")?.takeIf { it.isNotBlank() }
                val narrowFont = textInfo?.optBoolean("narrowFont", false) ?: false
                val showHighlightColor = textInfo?.optBoolean("showHighlightColor", false) ?: false

                val picInfo = left.optJSONObject("picInfo")
                val t = picInfo?.optInt("type", 0) ?: 0
                val picKey: String? = when (t) {
                    1, 4 -> picInfo?.optString("pic", "")?.takeIf { it.isNotBlank() }
                    2, 3 -> null
                    else -> null
                }
                val mustHavePicKey = (t == 4)
                if (mustHavePicKey && picKey == null) return null

                AImageText1(
                    title = title,
                    content = content,
                    narrowFont = narrowFont,
                    showHighlightColor = showHighlightColor,
                    picKey = picKey
                )
            }
            5 -> {
                val textInfo = left.optJSONObject("textInfo")
                val title = textInfo?.optString("title", "")?.takeIf { it.isNotBlank() }
                    ?: left.optString("title", "").takeIf { it.isNotBlank() }
                val content = textInfo?.optString("content", "")?.takeIf { it.isNotBlank() }
                    ?: left.optString("content", "").takeIf { it.isNotBlank() }
                val showHighlightColor = textInfo?.optBoolean("showHighlightColor", false) ?: false

                val picInfo = left.optJSONObject("picInfo")
                val picTypeOk = (picInfo?.optInt("type", 0) == 4)
                val picKey = picInfo?.optString("pic", "")?.takeIf { it.isNotBlank() }

                if (title == null || !picTypeOk || picKey == null) return null

                AImageText5(
                    title = title,
                    content = content,
                    showHighlightColor = showHighlightColor,
                    picKey = picKey
                )
            }
            else -> null
        }
    }

    /**
     * 解析 B 区（优先识别 imageTextInfoRight 的 type，其次识别 text/digit/progress/pic），参考 BParser.kt
     */
    private fun parseBComponent(bigIsland: JSONObject?): BComponent {
        val right = bigIsland?.optJSONObject("imageTextInfoRight")
        if (right != null) {
            val type = right.optInt("type", 0)
            val textInfo = right.optJSONObject("textInfo")
            val titleInline = right.optString("title", "").takeIf { it.isNotBlank() }
            val contentInline = right.optString("content", "").takeIf { it.isNotBlank() }
            val titleText = titleInline ?: textInfo?.optString("title", "")?.takeIf { it.isNotBlank() }
            val contentText = contentInline ?: textInfo?.optString("content", "")?.takeIf { it.isNotBlank() }
            val frontTitle = textInfo?.optString("frontTitle", "")?.takeIf { it.isNotBlank() }
            val narrowFont = textInfo?.optBoolean("narrowFont", false) ?: false
            val showHighlightColor = textInfo?.optBoolean("showHighlightColor", false) ?: false

            val picInfo = right.optJSONObject("picInfo")
            val picTypeOk = (picInfo?.optInt("type", 0) == 1)
            val picKey = picInfo?.optString("pic", "")?.takeIf { it.isNotBlank() }

            return when (type) {
                2 -> {
                    val title = titleText ?: return BEmpty
                    if (!picTypeOk || picKey == null) return BEmpty
                    BImageText2(
                        frontTitle = frontTitle,
                        title = title,
                        content = contentText,
                        narrowFont = narrowFont,
                        showHighlightColor = showHighlightColor,
                        picKey = picKey
                    )
                }
                3 -> {
                    val title = titleText ?: return BEmpty
                    if (!picTypeOk || picKey == null) return BEmpty
                    BImageText3(
                        title = title,
                        narrowFont = narrowFont,
                        showHighlightColor = showHighlightColor,
                        picKey = picKey
                    )
                }
                4 -> BEmpty
                6 -> {
                    val title = titleText ?: return BEmpty
                    val staticIcon = (picInfo?.optInt("type", 0) == 4)
                    if (!staticIcon || picKey == null) return BEmpty
                    BImageText6(
                        title = title,
                        narrowFont = narrowFont,
                        showHighlightColor = showHighlightColor,
                        picKey = picKey
                    )
                }
                else -> BEmpty
            }
        }

        bigIsland?.optJSONObject("textInfo")?.let { ti ->
            val title = ti.optString("title", "").takeIf { it.isNotBlank() } ?: return BEmpty
            val frontTitle = ti.optString("frontTitle", "").takeIf { it.isNotBlank() }
            val content = ti.optString("content", "").takeIf { it.isNotBlank() }
            val narrowFont = ti.optBoolean("narrowFont", false)
            val showHighlightColor = ti.optBoolean("showHighlightColor", false)
            return BTextInfo(
                frontTitle = frontTitle,
                title = title,
                content = content,
                narrowFont = narrowFont,
                showHighlightColor = showHighlightColor
            )
        }

        bigIsland?.optJSONObject("fixedWidthDigitInfo")?.let { fi ->
            val digit = fi.optString("digit", "").takeIf { it.isNotBlank() }
                ?: fi.optString("text", "").takeIf { it.isNotBlank() }
            digit ?: return@let
            val content = fi.optString("content", "").takeIf { it.isNotBlank() }
            val showHighlightColor = fi.optBoolean("showHighlightColor", false)
            return BFixedWidthDigitInfo(
                digit = digit,
                content = content,
                showHighlightColor = showHighlightColor
            )
        }

        bigIsland?.optJSONObject("sameWidthDigitInfo")?.let { si ->
            val timerObj = si.optJSONObject("timerInfo")
            val timer = timerObj?.let { to ->
                val typeExists = to.has("timerType")
                if (!typeExists) null else {
                    val timerType = to.optInt("timerType")
                    val timerWhen = to.optLong("timerWhen", 0)
                    val timerTotal = to.optLong("timerTotal", 0)
                    val timerSystemCurrent = to.optLong("timerSystemCurrent", 0)
                    TimerInfo(
                        timerType = timerType,
                        timerWhen = timerWhen,
                        timerTotal = timerTotal,
                        timerSystemCurrent = timerSystemCurrent
                    )
                }
            }

            val digit = si.optString("digit", "").takeIf { it.isNotBlank() }
                ?: si.optString("text", "").takeIf { it.isNotBlank() }

            if (timer == null && digit == null) return@let

            val content = si.optString("content", "").takeIf { it.isNotBlank() }
            val showHighlightColor = si.optBoolean("showHighlightColor", false)
            return BSameWidthDigitInfo(
                digit = digit,
                timer = timer,
                content = content,
                showHighlightColor = showHighlightColor
            )
        }

        bigIsland?.optJSONObject("progressTextInfo")?.let { root ->
            val ti = root.optJSONObject("textInfo")
            val frontTitle = ti?.optString("frontTitle", "")?.takeIf { it.isNotBlank() }
            val title = ti?.optString("title", "")?.takeIf { it.isNotBlank() }
            val content = ti?.optString("content", "")?.takeIf { it.isNotBlank() }
            val narrowFont = ti?.optBoolean("narrowFont", false) ?: false
            val showHighlightColor = ti?.optBoolean("showHighlightColor", false) ?: false

            val pInfo = root.optJSONObject("progressInfo") ?: return@let
            val progress = pInfo.optInt("progress", -1)
            if (progress !in 0..100) return@let
            val colorReach = pInfo.optString("colorReach", "").takeIf { it.isNotBlank() }
            val colorUnReach = pInfo.optString("colorUnReach", "").takeIf { it.isNotBlank() }
            val isCCW = pInfo.optBoolean("isCCW", false)

            val picObj = root.optJSONObject("picInfo")
            val picKey = picObj?.let { po ->
                val typeOk = po.optInt("type", 0) == 1
                val key = po.optString("pic", "").takeIf { it.isNotBlank() }
                if (typeOk) key else null
            }

            return BProgressTextInfo(
                frontTitle = frontTitle,
                title = title,
                content = content,
                narrowFont = narrowFont,
                showHighlightColor = showHighlightColor,
                progress = progress,
                colorReach = colorReach,
                colorUnReach = colorUnReach,
                isCCW = isCCW,
                picKey = picKey
            )
        }

        bigIsland?.optJSONObject("picInfo")?.let { pi ->
            val type = pi.optInt("type", -1)
            if (type != 1 && type != 4) return@let
            val picKey = pi.optString("pic", "").takeIf { it.isNotBlank() } ?: return@let
            return BPicInfo(picKey = picKey, type = type)
        }

        return BEmpty
    }

    // ---- 胶囊兼容工具方法 ----

    /**
     * 将文本转换为位图，参考 Capsulyric 的实现
     */
    private fun textToBitmap(text: String, forceFontSize: Float? = null): android.graphics.Bitmap? {
        try {
            // 检查文本是否为空
            if (text.isBlank()) {
                Logger.w(TAG, "超级岛: 文本为空，无法生成位图")
                return null
            }
            
            // 自适应字体大小算法
            // 基础大小：40f. 最小大小：20f.
            // 衰减：超过10个字符后每字符减少0.8f.
            val length = text.length
            
            val fontSize = forceFontSize ?: if (length <= 10) {
                40f
            } else {
                (40f - (length - 10) * 0.8f).coerceAtLeast(20f)
            }
            
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSize
                color = android.graphics.Color.WHITE
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            val baseline = -paint.ascent() // ascent() 为负值
            // 为紧凑裁剪中的宽字符添加更多缓冲区
            val width = (paint.measureText(text) + 10).toInt() 
            val height = (baseline + paint.descent() + 5).toInt()
            
            // 空或无效尺寸的安全检查
            if (width <= 0 || height <= 0) {
                Logger.w(TAG, "超级岛: 文本位图尺寸无效，width=$width, height=$height")
                return null
            }

            // 确保尺寸在合理范围内
            val maxSize = 500
            val finalWidth = width.coerceAtMost(maxSize)
            val finalHeight = height.coerceAtMost(maxSize)
            
            val image = android.graphics.Bitmap.createBitmap(finalWidth, finalHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(image)
            // 绘制时添加小的左内边距
            canvas.drawText(text, 5f, baseline, paint)
            Logger.d(TAG, "超级岛: 生成文本位图成功，尺寸: ${finalWidth}x${finalHeight}")
            return image
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 生成文本位图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * 将进度数据转换为环形进度圈位图
     */
    private fun progressToBitmap(progress: Int, colorReach: String? = null, colorUnReach: String? = null, isCCW: Boolean = false): Bitmap? {
        try {
            // 检查进度值是否有效
            if (progress < 0 || progress > 100) {
                Logger.w(TAG, "超级岛: 进度值无效，progress=$progress")
                return null
            }
            
            val size = 100 // 位图大小
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            // 背景透明
            canvas.drawColor(android.graphics.Color.TRANSPARENT)
            
            // 计算进度角度
            val sweepAngle = (progress / 100f) * 360f
            val startAngle = if (isCCW) 90f else -90f
            
            // 绘制未达到的部分
            val paintUnReach = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
                color = colorUnReach?.let { 
                    try {
                        android.graphics.Color.parseColor(it)
                    } catch (e: Exception) {
                        Logger.w(TAG, "超级岛: 解析未达到部分颜色失败: ${e.message}")
                        android.graphics.Color.GRAY
                    }
                } ?: android.graphics.Color.GRAY
            }
            canvas.drawArc(
                10f, 10f, (size - 10).toFloat(), (size - 10).toFloat(),
                0f, 360f, false, paintUnReach
            )
            
            // 绘制已达到的部分
            val paintReach = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 10f
                color = colorReach?.let { 
                    try {
                        android.graphics.Color.parseColor(it)
                    } catch (e: Exception) {
                        Logger.w(TAG, "超级岛: 解析已达到部分颜色失败: ${e.message}")
                        android.graphics.Color.WHITE
                    }
                } ?: android.graphics.Color.WHITE
            }
            canvas.drawArc(
                10f, 10f, (size - 10).toFloat(), (size - 10).toFloat(),
                startAngle, if (isCCW) -sweepAngle else sweepAngle, false, paintReach
            )
            
            Logger.d(TAG, "超级岛: 生成进度圈位图成功，进度: $progress%")
            return bitmap
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 生成进度圈位图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    // ---- 胶囊兼容通知构建方法 ----

    /**
     * 构建胶囊兼容的通知，添加标准通知字段和 smallIcon 注入
     */
    private fun buildCapsuleCompatibleNotification(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String?,
        text: String?,
        appName: String?,
        paramV2: ParamV2?,
        picMap: Map<String, String>?,
        paramV2Raw: String?
    ): NotificationCompat.Builder {
        try {
            // 解析 param_v2 中的 bigIsland 数据
        var bigIsland: JSONObject? = null
        val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
        
        paramV2RawValue?.let {
            try {
                val json = JSONObject(it)
                // 尝试从 param_island -> bigIslandArea 中解析
                val paramIsland = json.optJSONObject("param_island")
                bigIsland = paramIsland?.optJSONObject("bigIslandArea")
                
                // 如果没有找到，尝试直接从 bigIsland 字段解析
                if (bigIsland == null) {
                    bigIsland = json.optJSONObject("bigIsland")
                }
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 解析 bigIsland 失败: ${e.message}")
            }
        }
            
            // 解析 A/B 区数据
            val aComponent = parseAComponent(bigIsland)
            val bComponent = parseBComponent(bigIsland)
            
            // 提取 A/B 区数据
            val aTitle = when (aComponent) {
                is AImageText1 -> aComponent.title
                is AImageText5 -> aComponent.title
                else -> null
            }
            
            val aContent = when (aComponent) {
                is AImageText1 -> aComponent.content
                is AImageText5 -> aComponent.content
                else -> null
            }
            
            val aPicKey = when (aComponent) {
                is AImageText1 -> aComponent.picKey
                is AImageText5 -> aComponent.picKey
                else -> null
            }
            
            val bTitle = when (bComponent) {
                is BImageText2 -> bComponent.title
                is BImageText3 -> bComponent.title
                is BImageText6 -> bComponent.title
                is BTextInfo -> bComponent.title
                is BFixedWidthDigitInfo -> bComponent.digit
                is BSameWidthDigitInfo -> {
                    // 优先使用timer信息计算计时值
                    if (bComponent.timer != null) {
                        formatTimerInfo(bComponent.timer)
                    } else {
                        bComponent.digit
                    }
                }
                is BProgressTextInfo -> bComponent.title
                else -> null
            }
            
            val bContent = when (bComponent) {
                is BImageText2 -> bComponent.content
                is BTextInfo -> bComponent.content
                is BFixedWidthDigitInfo -> bComponent.content
                is BSameWidthDigitInfo -> {
                    // 优先使用timer信息计算计时值
                    if (bComponent.timer != null) {
                        formatTimerInfo(bComponent.timer)
                    } else {
                        bComponent.content
                    }
                }
                is BProgressTextInfo -> bComponent.content
                else -> null
            }
            
            val bPicKey = when (bComponent) {
                is BImageText2 -> bComponent.picKey
                is BImageText3 -> bComponent.picKey
                is BImageText6 -> bComponent.picKey
                is BProgressTextInfo -> bComponent.picKey
                else -> null
            }
            
            val bProgress = when (bComponent) {
                is BProgressTextInfo -> bComponent.progress
                else -> null
            }
            
            val bProgressColorReach = when (bComponent) {
                is BProgressTextInfo -> bComponent.colorReach
                else -> null
            }
            
            val bProgressColorUnReach = when (bComponent) {
                is BProgressTextInfo -> bComponent.colorUnReach
                else -> null
            }
            
            val bProgressIsCCW = when (bComponent) {
                is BProgressTextInfo -> bComponent.isCCW
                else -> false
            }
            
            // 设置标准通知字段
            // 优先检查是否有计时器信息
            val timerText = when (bComponent) {
                is BSameWidthDigitInfo -> {
                    // 优先使用timer信息计算计时值
                    if (bComponent.timer != null) {
                        formatTimerInfo(bComponent.timer)
                    } else {
                        null
                    }
                }
                else -> null
            }
            
            val capsuleTitle = aTitle ?: bTitle ?: title
            val capsuleText = timerText ?: aContent ?: bContent ?: text
            val capsuleSubText = appName ?: "超级岛通知"
            val capsuleShortText = timerText ?: bTitle ?: bContent ?: aTitle ?: aContent ?: text
            
            builder
                .setContentTitle(capsuleTitle ?: capsuleSubText)
                .setContentText(capsuleText ?: "")
                .setSubText(capsuleSubText)
                .setShortCriticalText(capsuleShortText ?: "")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setRequestPromotedOngoing(true)
            
            // 处理 smallIcon
            var smallIconBitmap: Bitmap? = null
            
            // 优先处理进度数据
            if (bProgress != null) {
                smallIconBitmap = progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
            }
            
            // 处理文本位图
            if (smallIconBitmap == null) {
                // 优先使用 B 区文本生成位图
                val textToRender = when (bComponent) {
                    is BSameWidthDigitInfo -> {
                        // 优先使用timer信息计算计时值
                        if (bComponent.timer != null) {
                            formatTimerInfo(bComponent.timer)
                        } else {
                            bComponent.digit ?: bComponent.content
                        }
                    }
                    else -> {
                        bTitle ?: bContent ?: aTitle ?: aContent
                    }
                }
                if (!textToRender.isNullOrBlank()) {
                    smallIconBitmap = textToBitmap(textToRender)
                }
            }
            
            // 处理图标
            if (smallIconBitmap == null) {
                // 优先使用应用图标（大图标的键值提供的图标）
                val appIconKey = "miui.focus.pic_app_icon"
                Logger.d(TAG, "超级岛: 处理应用图标 - appIconKey: $appIconKey, picMap: ${picMap?.keys}")
                if (!picMap.isNullOrEmpty() && picMap.containsKey(appIconKey)) {
                    val appIconUrl = picMap[appIconKey]
                    if (!appIconUrl.isNullOrBlank()) {
                        // 同步下载应用图标
                        Logger.d(TAG, "超级岛: 使用应用图标作为小图标，URL: $appIconUrl")
                        val bitmap = runBlocking {
                            downloadBitmap(context, appIconUrl, 5000)
                        }
                        if (bitmap != null) {
                            smallIconBitmap = bitmap
                            Logger.d(TAG, "超级岛: 应用图标加载成功")
                        } else {
                            Logger.w(TAG, "超级岛: 应用图标加载失败")
                        }
                    } else {
                        Logger.w(TAG, "超级岛: 应用图标 URL 为空")
                    }
                } else {
                    Logger.d(TAG, "超级岛: 未找到应用图标键 $appIconKey")
                }
            }
            
            // 如果没有应用图标，再使用 A 区图标
            if (smallIconBitmap == null) {
                // 优先使用 A 区图标
                val picKeyToUse = aPicKey ?: bPicKey
                Logger.d(TAG, "超级岛: 处理 A 区图标 - picKeyToUse: $picKeyToUse, picMap: ${picMap?.keys}")
                if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
                    val picUrl = picMap[picKeyToUse]
                    if (!picUrl.isNullOrBlank()) {
                        // 同步下载图标
                        Logger.d(TAG, "超级岛: 使用 A 区图标作为小图标，URL: $picUrl")
                        val bitmap = runBlocking {
                            downloadBitmap(context, picUrl, 5000)
                        }
                        if (bitmap != null) {
                            smallIconBitmap = bitmap
                            Logger.d(TAG, "超级岛: A 区图标加载成功")
                        } else {
                            Logger.w(TAG, "超级岛: A 区图标加载失败")
                        }
                    } else {
                        Logger.w(TAG, "超级岛: A 区图标 URL 为空")
                    }
                } else {
                    Logger.d(TAG, "超级岛: 未找到 A 区图标键")
                }
            }
            
            // 如果没有生成位图，使用默认图标
            if (smallIconBitmap == null) {
                builder.setSmallIcon(android.R.drawable.stat_notify_more)
            } else {
                // 使用生成的位图作为小图标
                val icon = android.graphics.drawable.BitmapDrawable(context.resources, smallIconBitmap)
                builder.setSmallIcon(android.R.drawable.stat_notify_more) // 设置默认图标作为占位符
                // 注意：在 Android 中，setSmallIcon 只能接受资源 ID，所以我们需要使用其他方式注入位图
                // 这里我们保持默认图标，实际的位图注入需要在通知构建后处理
            }
            
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 构建胶囊兼容通知失败: ${e.message}")
        }
        
        return builder
    }

    // ---- 图标注入辅助方法 ----

    /**
     * 注入小图标到通知中
     */
    private fun injectSmallIcon(notification: android.app.Notification, bitmap: Bitmap?) {
        bitmap?.let {
            try {
                val icon = android.graphics.drawable.Icon.createWithBitmap(it)
                val field = android.app.Notification::class.java.getDeclaredField("mSmallIcon")
                field.isAccessible = true
                field.set(notification, icon)
                Logger.i(TAG, "超级岛: 成功注入小图标到胶囊通知")
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 注入小图标失败: ${e.message}")
            }
        }
    }

    /**
     * 构建胶囊兼容的通知并注入图标
     */
    private suspend fun buildCapsuleCompatibleNotificationWithIconInjection(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String?,
        text: String?,
        appName: String?,
        paramV2: ParamV2?,
        picMap: Map<String, String>?,
        paramV2Raw: String?
    ): android.app.Notification {
        try {
            // 先构建胶囊兼容的通知
            val capsuleBuilder = buildCapsuleCompatibleNotification(context, builder, title, text, appName, paramV2, picMap, paramV2Raw)
            
            // 构建通知并注入图标
            val notification = capsuleBuilder.build()
        
        // 尝试从 A/B 区数据中获取图标或生成位图
        var smallIconBitmap: android.graphics.Bitmap? = null
        
        // 解析 param_v2 中的 bigIsland 数据
        var bigIsland: JSONObject? = null
        val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
        
        paramV2RawValue?.let {
            try {
                val json = JSONObject(it)
                // 尝试从 param_island -> bigIslandArea 中解析
                val paramIsland = json.optJSONObject("param_island")
                bigIsland = paramIsland?.optJSONObject("bigIslandArea")
                
                // 如果没有找到，尝试直接从 bigIsland 字段解析
                if (bigIsland == null) {
                    bigIsland = json.optJSONObject("bigIsland")
                }
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 解析 bigIsland 失败: ${e.message}")
            }
        }
        
        // 解析 A/B 区数据
        val aComponent = parseAComponent(bigIsland)
        val bComponent = parseBComponent(bigIsland)
        
        // 提取 A/B 区数据
        val aPicKey = when (aComponent) {
            is AImageText1 -> aComponent.picKey
            is AImageText5 -> aComponent.picKey
            else -> null
        }
        
        val bPicKey = when (bComponent) {
            is BImageText2 -> bComponent.picKey
            is BImageText3 -> bComponent.picKey
            is BImageText6 -> bComponent.picKey
            is BProgressTextInfo -> bComponent.picKey
            else -> null
        }
        
        val bProgress = when (bComponent) {
            is BProgressTextInfo -> bComponent.progress
            else -> null
        }
        
        val bProgressColorReach = when (bComponent) {
            is BProgressTextInfo -> bComponent.colorReach
            else -> null
        }
        
        val bProgressColorUnReach = when (bComponent) {
            is BProgressTextInfo -> bComponent.colorUnReach
            else -> null
        }
        
        val bProgressIsCCW = when (bComponent) {
            is BProgressTextInfo -> bComponent.isCCW
            else -> false
        }
        
        // 处理 smallIcon
        // 优先处理进度数据
        Logger.d(TAG, "超级岛: 处理小图标 - bProgress: $bProgress")
        if (bProgress != null) {
            Logger.d(TAG, "超级岛: 使用进度数据生成位图")
            smallIconBitmap = progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
            Logger.d(TAG, "超级岛: 进度位图生成结果: ${smallIconBitmap != null}")
        }
        
        // 处理文本位图
        if (smallIconBitmap == null) {
            // 检查是否为计时器类型，如果是，不生成文本位图，保留之前的图标
            val isTimerType = bComponent is BSameWidthDigitInfo && bComponent.timer != null
            if (!isTimerType) {
                // 优先使用 B 区文本生成位图
                val textToRender = when (bComponent) {
                    is BImageText2 -> bComponent.title ?: bComponent.content
                    is BImageText3 -> bComponent.title
                    is BImageText6 -> bComponent.title
                    is BTextInfo -> bComponent.title ?: bComponent.content
                    is BFixedWidthDigitInfo -> bComponent.digit
                    is BSameWidthDigitInfo -> bComponent.digit
                    is BProgressTextInfo -> bComponent.title ?: bComponent.content
                    else -> null
                } ?: when (aComponent) {
                    is AImageText1 -> aComponent.title ?: aComponent.content
                    is AImageText5 -> aComponent.title ?: aComponent.content
                    else -> null
                }
                
                Logger.d(TAG, "超级岛: 处理文本位图 - textToRender: $textToRender")
                if (!textToRender.isNullOrBlank()) {
                    Logger.d(TAG, "超级岛: 使用文本生成位图")
                    smallIconBitmap = textToBitmap(textToRender)
                    Logger.d(TAG, "超级岛: 文本位图生成结果: ${smallIconBitmap != null}")
                }
            } else {
                // 计时器类型，不生成文本位图，保留之前的图标
                Logger.d(TAG, "超级岛: 计时器类型，保留之前的小图标，不生成文本位图")
            }
        }
        
        // 处理图标
        if (smallIconBitmap == null) {
            // 优先使用应用图标（大图标的键值提供的图标）
            val appIconKey = "miui.focus.pic_app_icon"
            Logger.d(TAG, "超级岛: 处理应用图标 - appIconKey: $appIconKey, picMap: ${picMap?.keys}")
            if (!picMap.isNullOrEmpty() && picMap.containsKey(appIconKey)) {
                val appIconUrl = picMap[appIconKey]
                if (!appIconUrl.isNullOrBlank()) {
                    // 同步下载应用图标
                    Logger.d(TAG, "超级岛: 使用应用图标作为小图标")
                    val bitmap = runBlocking {
                        downloadBitmap(context, appIconUrl, 5000)
                    }
                    if (bitmap != null) {
                        smallIconBitmap = bitmap
                        Logger.d(TAG, "超级岛: 应用图标加载成功")
                    } else {
                        Logger.w(TAG, "超级岛: 应用图标加载失败")
                    }
                }
            }
        }
        
        // 如果没有应用图标，再使用 A 区图标
        if (smallIconBitmap == null) {
            // 优先使用 A 区图标
            val picKeyToUse = aPicKey ?: bPicKey
            Logger.d(TAG, "超级岛: 处理 A 区图标 - picKeyToUse: $picKeyToUse, picMap: ${picMap?.keys}")
            if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
                val picUrl = picMap[picKeyToUse]
                if (!picUrl.isNullOrBlank()) {
                    // 同步下载图标
                    Logger.d(TAG, "超级岛: 使用 A 区图标作为小图标")
                    val bitmap = runBlocking {
                        downloadBitmap(context, picUrl, 5000)
                    }
                    if (bitmap != null) {
                        smallIconBitmap = bitmap
                        Logger.d(TAG, "超级岛: A 区图标加载成功")
                    } else {
                        Logger.w(TAG, "超级岛: A 区图标加载失败")
                    }
                }
            }
        }
        
        // 如果没有生成位图，使用默认图标
        if (smallIconBitmap == null) {
            Logger.d(TAG, "超级岛: 没有生成位图，使用默认图标")
        } else {
            Logger.d(TAG, "超级岛: 成功生成小图标")
        }
        
        // 注入小图标
        injectSmallIcon(notification, smallIconBitmap)
        
        // 返回注入图标后的通知对象
        return notification
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 构建胶囊兼容通知并注入图标失败: ${e.message}")
            e.printStackTrace()
            // 发生异常时，返回原始构建器构建的通知
            return builder.build()
        }
    }

    // ---- 辅助方法 ----

    /**
     * 兼容空值的 param_v2 解析包装
     */
    internal fun parseParamV2Safe(raw: String?): ParamV2? {
        return try {
            val s = raw ?: return null
            if (s.isBlank()) null else parseParamV2(s)
        } catch (_: Exception) { null }
    }

    /**
     * 根据键下载位图
     */
    private suspend fun downloadBitmapByKey(context: Context, picMap: Map<String, String>?, key: String?): Bitmap? {
        if (picMap.isNullOrEmpty() || key.isNullOrBlank()) return null
        val raw = picMap[key] ?: return null
        val url = SuperIslandImageStore.resolve(context, raw) ?: raw
        return withContext(Dispatchers.IO) { downloadBitmap(context, url, 5000) }
    }

    /**
     * 下载第一个可用的图片
     */
    private suspend fun downloadFirstAvailableImage(context: Context, picMap: Map<String, String>?): Bitmap? {
        if (picMap.isNullOrEmpty()) return null
        for ((_, url) in picMap) {
            try {
                val resolved = SuperIslandImageStore.resolve(context, url) ?: url
                val bmp = withContext(Dispatchers.IO) { downloadBitmap(context, resolved, 5000) }
                if (bmp != null) return bmp
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 下载图片失败: ${e.message}")
            }
        }
        return null
    }

    /**
     * 下载位图
     */
    private suspend fun downloadBitmap(context: Context, url: String, timeoutMs: Int): Bitmap? {
        return try {
            SuperIslandImageUtil.loadBitmapSuspend(context, url, timeoutMs)
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 下载图片失败: ${e.message}")
            null
        }
    }

    /**
     * 取消复刻通知
     */
    internal fun cancelReplicaNotification(context: Context, key: String, entryKeyToNotificationId: ConcurrentHashMap<String, Int>) {
        try {
            val notificationId = entryKeyToNotificationId.remove(key)
            if (notificationId != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
                // 移除对应的定时器
                TimerUpdateManager.instance.removeTimer(key)
                Logger.i(TAG, "超级岛: 取消复刻通知成功，key=$key, notificationId=$notificationId")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 取消复刻通知失败: ${e.message}")
        }
    }

    /**
     * 清除所有复刻通知
     */
    internal fun clearAllReplicaNotifications(context: Context?, entryKeyToNotificationId: ConcurrentHashMap<String, Int>) {
        try {
            if (context != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // 取消所有映射中的通知
                entryKeyToNotificationId.forEach { (key, notificationId) ->
                    notificationManager.cancel(notificationId)
                    // 移除对应的定时器
                    TimerUpdateManager.instance.removeTimer(key)
                    Logger.i(TAG, "超级岛: 取消复刻通知成功，key=$key, notificationId=$notificationId")
                }
            }
            
            // 清空映射
            entryKeyToNotificationId.clear()
            // 清空所有定时器
            TimerUpdateManager.instance.clearAll()
            Logger.i(TAG, "超级岛: 清除所有复刻通知成功")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 清除所有复刻通知失败: ${e.message}")
        }
    }
}
