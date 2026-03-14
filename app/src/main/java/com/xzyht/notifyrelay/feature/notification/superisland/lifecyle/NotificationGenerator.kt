package com.xzyht.notifyrelay.feature.notification.superisland.lifecyle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.NotificationBroadcastReceiver
import com.xzyht.notifyrelay.feature.notification.superisland.common.BitmapUtils
import com.xzyht.notifyrelay.feature.notification.superisland.common.CapsuleScrollManager
import com.xzyht.notifyrelay.feature.notification.superisland.common.TextSplitter
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.left.AImageText1
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.left.AImageText5
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BFixedWidthDigitInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BImageText2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BImageText3
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BImageText6
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BProgressTextInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BSameWidthDigitInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.SmallIsland.right.BTextInfo
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.formatTimerInfo
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.model.componets.TimerInfo
import com.xzyht.notifyrelay.feature.notification.superisland.model.core.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.model.core.parseParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.model.parseAComponent
import com.xzyht.notifyrelay.feature.notification.superisland.model.parseBComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知生成器，负责处理超级岛通知的生成和注入
 * 
 * 耦合逻辑说明：
 * 1. 浮窗功能与通知点击事件的耦合：
 *    - 当浮窗功能开启时，为通知设置点击意图和删除意图
 *    - 点击意图的 action 为 com.xzyht.notifyrelay.ACTION_TOGGLE_FLOATING
 *    - 删除意图的 action 为 com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION
 *    - 这些意图会触发 NotificationBroadcastReceiver 中的相应处理逻辑
 * 
 * 2. 通知与浮窗的去耦合：
 *    - 通过 SUPER_ISLAND_FLOATING_WINDOW_KEY 开关控制浮窗功能
 *    - 浮窗功能关闭时，不设置与浮窗关联的通知点击和关闭广播/意图
 *    - 浮窗功能关闭时，仅创建基础通知，不添加与浮窗相关的功能
 */
object NotificationGenerator {
    private const val TAG = "超级岛通知生成"
    // 通知渠道ID
    private const val NOTIFICATION_CHANNEL_ID = "super_island_replica"
    // 通知ID基础值
    private const val NOTIFICATION_BASE_ID = 20000
    // 浮窗功能开关键
    private const val SUPER_ISLAND_FLOATING_WINDOW_KEY = "super_island_floating_window"
    // 规范信息注入模式键
    private const val SPEC_INJECTION_MODE_KEY = "spec_injection_mode"
    
    // 注入方式枚举
    enum class SpecInjectionMode {
        SUPER_ISLAND,      // 仅超级岛规范信息注入
        LIVE_UPDATES,      // 仅Live Updates规范信息注入
        BOTH,              // 两者都注入
        NONE               // 都不注入（不应该使用，但为了完整性保留）
    }
    
    /**
     * 检查浮窗功能是否开启
     */
    private fun isFloatingWindowEnabled(context: Context): Boolean {
        return StorageManager.getBoolean(context, SUPER_ISLAND_FLOATING_WINDOW_KEY, true)
    }

    /**
     * 获取规范信息注入模式
     */
    private fun getSpecInjectionMode(context: Context): SpecInjectionMode {
        val modeOrdinal = StorageManager.getInt(context, SPEC_INJECTION_MODE_KEY, SpecInjectionMode.BOTH.ordinal)
        return SpecInjectionMode.values().getOrElse(modeOrdinal) { SpecInjectionMode.BOTH }
    }

    /**
     * 检查超级岛规范信息注入是否开启
     */
    private fun isSuperIslandSpecInjectionEnabled(context: Context): Boolean {
        val mode = getSpecInjectionMode(context)
        return mode == SpecInjectionMode.SUPER_ISLAND || mode == SpecInjectionMode.BOTH
    }

    /**
     * 检查Live Updates规范信息注入是否开启
     */
    private fun isLiveUpdatesSpecInjectionEnabled(context: Context): Boolean {
        val mode = getSpecInjectionMode(context)
        return mode == SpecInjectionMode.LIVE_UPDATES || mode == SpecInjectionMode.BOTH
    }

    /**
     * 检查是否至少有一种规范信息注入开启
     * @return true 如果至少有一种注入开启，false 如果都关闭
     */
    private fun isAnySpecInjectionEnabled(context: Context): Boolean {
        return isSuperIslandSpecInjectionEnabled(context) || isLiveUpdatesSpecInjectionEnabled(context)
    }

    /**
     * 验证规范信息注入开关状态，确保至少有一种开启
     * 如果都关闭，则默认开启两者都注入
     */
    private fun validateSpecInjectionSwitches(context: Context) {
        if (!isAnySpecInjectionEnabled(context)) {
            // 如果都关闭，默认开启两者都注入
            StorageManager.putInt(context, SPEC_INJECTION_MODE_KEY, SpecInjectionMode.BOTH.ordinal)
            Logger.w(TAG, "规范信息注入模式无效，已默认设置为两者都注入")
        }
    }
    
    // 缓存变量，用于优化图标生成
    private var cachedIconKey = ""
    private var cachedIconBitmap: Bitmap? = null
    
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
     * @return 通知ID，如果发送失败则返回null
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
    ): Int? {
        try {
            // 验证规范信息注入开关状态，确保至少有一种开启
            validateSpecInjectionSwitches(context)
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 生成唯一的通知ID
            val notificationId = key.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            
            // 检查浮窗功能是否开启
            val floatingWindowEnabled = isFloatingWindowEnabled(context)
            
            // 创建点击意图，用于处理用户点击通知时切换浮窗显示/隐藏
            val contentIntent = if (floatingWindowEnabled) {
                Intent(context, NotificationBroadcastReceiver::class.java).apply {
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
            } else {
                null
            }
            
            val pendingContentIntent = if (floatingWindowEnabled && contentIntent != null) {
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            // 检查是否为媒体类型的超级岛浮窗
            val isMediaType = paramV2?.business == "media"

            // 创建删除意图，用于处理用户移除通知时关闭浮窗
            val deleteIntent = if (floatingWindowEnabled) {
                PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    Intent(context, NotificationBroadcastReceiver::class.java)
                        .putExtra("notificationId", notificationId)
                        .setAction("com.xzyht.notifyrelay.ACTION_CLOSE_NOTIFICATION"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }
            
            // 对于媒体类型，使用HyperCeiler焦点歌词的特殊处理
            if (isMediaType) {
                // 创建媒体类型通知渠道
                val mediaChannel = NotificationChannel(
                    "channel_id_focusNotifLyrics",
                    "焦点歌词通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(mediaChannel)
                
                var builder = NotificationCompat.Builder(context, "channel_id_focusNotifLyrics")
                    .setContentTitle(appName ?: "媒体应用") // 使用实际应用名作为通知标题
                    .setContentText(title ?: "")
                    .setSmallIcon(android.R.drawable.stat_notify_more) // 使用系统默认图标
                    // 调整为不可被一键清除的属性，只能手动划去
                    .setOngoing(true) // 不允许通知被一键清除
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setShowWhen(false)
                    .setWhen(System.currentTimeMillis())
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setRequestPromotedOngoing(true)

                // 只有在浮窗功能开启时才设置删除意图和点击意图
                if (floatingWindowEnabled) {
                    builder
                        .setDeleteIntent(deleteIntent) // 设置删除意图，处理用户移除通知的情况
                        .setContentIntent(pendingContentIntent) // 设置点击意图
                }
                
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
                val textLength = TextSplitter.calculateTextLength(lyricText)
                if (textLength > threshold) {
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
                
                // 预先解析picMap中的图片ID
                val resolvedPicMap = if (picMap.isNullOrEmpty()) {
                    picMap
                } else {
                    SuperIslandImageStore.resolvePicMap(context, picMap)
                }
                
                // ... (后续构建extras的代码保持不变)
                // 添加焦点歌词相关的结构化数据
                SuperIslandStructuredDataHelper.addMediaSuperIslandStructuredData(
                    builder = builder,
                    context = context,
                    title = title,
                    text = text,
                    picMap = resolvedPicMap
                )
                
                // 构建通知
                val notification = builder.build()
                
                // 生成并注入动态图标
                if (iconText.isNotEmpty()) {
                    val iconBitmap = BitmapUtils.textToBitmap(iconText)
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
                    var smallIconBitmap: Bitmap? = null
                    
                    // 解析param_v2中的bigIsland数据
                    val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
                    val bigIsland = parseBigIsland(paramV2RawValue)
                    
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
                        smallIconBitmap = BitmapUtils.progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
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
                            smallIconBitmap = BitmapUtils.textToBitmap(textToRender)
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
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "超级岛复刻通知",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
                
                // 获取paramV2原始数据，提前解析用于判断是否为计时器类型
                val entry = floatingWindowManager.getEntry(key)
                val paramV2Raw = entry?.paramV2Raw
                val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
                val bigIsland = parseBigIsland(paramV2RawValue)
                val bComponent = parseBComponent(bigIsland)
                
                // 判断是否为计时器类型（包括运行中和暂停状态）
                val isTimerType = bComponent is BSameWidthDigitInfo && bComponent.timer != null
                
                // 计时器通知的标题和内容设置
                // 标题显示状态，内容显示应用名，时间流逝由chronometer自动处理
                val timerTitle: String
                val timerContent: String
                if (isTimerType && bComponent is BSameWidthDigitInfo) {
                    val timer = bComponent.timer!!
                    when (timer.timerType) {
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
                } else {
                    timerTitle = title ?: appName ?: "超级岛通知"
                    timerContent = text ?: ""
                }
                
                // 判断是否为正在运行的计时器类型（用于chronometer）
                val isRunningTimer = isTimerType && 
                    (bComponent?.timer?.timerType == -1 || bComponent?.timer?.timerType == 1)
                
                // 构建基础通知，调整属性使其更接近实际超级岛通知
                var builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(timerTitle)
                    .setContentText(timerContent)
                    .setSmallIcon(android.R.drawable.stat_notify_more) // 使用系统默认图标
                    // 调整为与实际超级岛通知一致的属性
                    .setOngoing(true) // 实际通知通常是持续的
                    .setPriority(NotificationCompat.PRIORITY_MAX) // 提高优先级到最高，与原始通知一致
                    .setShowWhen(isRunningTimer) // 计时器需要显示时间以支持chronometer自动流逝
                    .setWhen(System.currentTimeMillis()) // 设置时间
                    .setOnlyAlertOnce(true) // 只提示一次
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 公开可见

                // 只有在浮窗功能开启时才设置删除意图和点击意图
                if (floatingWindowEnabled) {
                    builder
                        .setDeleteIntent(deleteIntent) // 设置删除意图
                        .setContentIntent(pendingContentIntent) // 设置点击意图
                }
                
                // 对于计时器类通知，添加计时器相关字段
                if (title?.contains("计时") == true || title?.contains("秒表") == true) {
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
                                    // 对于倒计时，设置chronometer自动倒计时
                                    builder.setUsesChronometer(true)
                                    builder.setChronometerCountDown(true)
                                    builder.setShowWhen(true) // 确保显示时间
                                    // 设置倒计时的终点时间
                                    builder.setWhen(timer.timerWhen)
                                }
                            } else {
                                // 正计时：使用timerWhen作为起点
                                builder.setUsesChronometer(true)
                                builder.setChronometerCountDown(false)
                                builder.setShowWhen(true) // 确保显示时间
                                // 设置正计时的起点时间
                                builder.setWhen(timer.timerWhen)
                            }
                        }
                    }
                }
                
                // 检查是否为进度类型通知，如果是，则可能已经通过 LiveUpdatesNotificationManager 处理
                val isProgressType = paramV2?.progressInfo != null || paramV2?.multiProgressInfo != null
                
                // 构建通知
                val notification = if (!isProgressType) {
                    // 非进度类型通知，添加胶囊兼容字段并注入图标
                    buildCapsuleCompatibleNotificationWithIconInjection(context, builder, title, text, appName, paramV2, picMap, paramV2RawValue)
                } else {
                    // 进度类型通知，已经通过 LiveUpdatesNotificationManager 处理，不重复添加胶囊兼容字段
                    Logger.i(TAG, "超级岛: 进度类型通知，已通过 LiveUpdatesNotificationManager 处理，不重复添加胶囊兼容字段")
                    // 构建通知
                    val builtNotification = builder.build()
                    // 尝试从 A/B 区数据中获取图标或生成位图
                    var smallIconBitmap: Bitmap? = null
                    
                    // 解析 A/B 区数据
                    val aComponent = parseAComponent(bigIsland)
                    
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
                
                // 发送通知
                notificationManager.notify(notificationId, notification)
                
                // 计时器通知使用系统chronometer API自动更新，无需手动创建定时器
                Logger.i(TAG, "超级岛: 计时器通知已发送，使用系统chronometer自动更新，key=$key")
        }
        
        // 保存entryKey到notificationId的映射
        entryKeyToNotificationId[key] = notificationId
        
        Logger.i(TAG, "超级岛: 发送复刻通知成功，key=$key, notificationId=$notificationId")
        return notificationId
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 发送复刻通知失败: ${e.message}")
            return null
        }
    }

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
            val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
            val bigIsland = parseBigIsland(paramV2RawValue)
            
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
            // 判断是否为计时器类型（包括运行中和暂停状态）
            val isTimerType = bComponent is BSameWidthDigitInfo && bComponent.timer != null
            
            // 根据计时器状态设置标题和内容
            if (isTimerType && bComponent is BSameWidthDigitInfo) {
                val timer = bComponent.timer!!
                val timerTitle = when (timer.timerType) {
                    -2 -> "暂停中"
                    -1 -> "倒计时中"
                    1 -> "正计时中"
                    2 -> "暂停中"
                    else -> title ?: appName ?: "超级岛通知"
                }
                val timerContent = appName ?: "超级岛通知"
                
                builder
                    .setContentTitle(timerTitle)
                    .setContentText(timerContent)
                    .setSubText(appName ?: "超级岛通知")
                    .setShortCriticalText(timerTitle)
            } else {
                // 非计时器类型，使用原有逻辑
                val timerText = when (bComponent) {
                    is BSameWidthDigitInfo -> {
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
            }
            
            builder
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setRequestPromotedOngoing(true)
            
            // 确保右胶囊文本被正确设置到 miui.focus.param 字段
            paramV2RawValue?.let {
                try {
                    val paramV2Json = JSONObject(it)
                    val bigIslandJson = paramV2Json.optJSONObject("bigIsland") ?: JSONObject()
                    val islandAreaJson = bigIslandJson.optJSONObject("imageTextInfoRight") ?: JSONObject()
                    
                    // 设置右胶囊文本
                    if (bTitle != null) {
                        islandAreaJson.put("title", bTitle)
                    }
                    if (bContent != null) {
                        islandAreaJson.put("content", bContent)
                    }
                    
                    // 更新 bigIsland 和 paramV2Json
                    bigIslandJson.put("imageTextInfoRight", islandAreaJson)
                    paramV2Json.put("bigIsland", bigIslandJson)
                    
                    // 更新原始 paramV2RawValue
                    // 注意：这里我们不能直接修改原始字符串，而是需要在构建 miui.focus.param 时使用更新后的数据
                } catch (e: Exception) {
                    Logger.w(TAG, "超级岛: 设置右胶囊文本失败: ${e.message}")
                }
            }
            
            // 处理 smallIcon
            var smallIconBitmap: Bitmap? = null
            
            // 优先处理进度数据
            if (bProgress != null) {
                smallIconBitmap = BitmapUtils.progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
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
                    smallIconBitmap = BitmapUtils.textToBitmap(textToRender)
                }
            }
            
            // 处理图标
            if (smallIconBitmap == null) {
                // 优先使用 A 区图标或B区图标
                val picKeyToUse = aPicKey ?: bPicKey
                Logger.d(TAG, "超级岛: 处理 A 区图标或B区图标 - picKeyToUse: $picKeyToUse, picMap: ${picMap?.keys}")
                if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
                    val picUrl = picMap[picKeyToUse]
                    if (!picUrl.isNullOrBlank()) {
                        // 同步下载图标
                        Logger.d(TAG, "超级岛: 使用 A 区图标或B区图标作为小图标，URL: $picUrl")
                        val bitmap = runBlocking {
                            downloadBitmap(context, picUrl, 5000)
                        }
                        if (bitmap != null) {
                            smallIconBitmap = bitmap
                            Logger.d(TAG, "超级岛: A 区图标或B区图标加载成功")
                        } else {
                            Logger.w(TAG, "超级岛: A 区图标或B区图标加载失败")
                        }
                    } else {
                        Logger.w(TAG, "超级岛: A 区图标或B区图标 URL 为空")
                    }
                } else {
                    Logger.d(TAG, "超级岛: 未找到 A 区图标或B区图标键")
                }
            }
            
            // 如果没有 A 区图标或B区图标，再使用应用图标
            if (smallIconBitmap == null) {
                // 使用应用图标（大图标的键值提供的图标）
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
            
            // 如果没有生成位图，使用系统默认图标
            if (smallIconBitmap == null) {
                // 使用系统默认图标
                builder.setSmallIcon(android.R.drawable.stat_notify_more)
                Logger.d(TAG, "超级岛: 使用系统默认图标")
            } else {
                // 使用生成的位图作为小图标
                val icon = BitmapDrawable(context.resources, smallIconBitmap)
                // 设置系统默认图标作为占位符
                builder.setSmallIcon(android.R.drawable.stat_notify_more)
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
    private fun injectSmallIcon(notification: Notification, bitmap: Bitmap?) {
        bitmap?.let {
            try {
                val icon = Icon.createWithBitmap(it)
                val field = Notification::class.java.getDeclaredField("mSmallIcon")
                field.isAccessible = true
                field.set(notification, icon)
                Logger.i(TAG, "超级岛: 成功注入小图标到胶囊通知")
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 注入小图标失败: ${e.message}")
            }
        }
    }

    /**
     * 解析bigIsland数据
     * 尝试从param_island -> bigIslandArea中解析，如果没有找到，再从bigIsland字段解析
     */
    private fun parseBigIsland(paramV2RawValue: String?): JSONObject? {
        paramV2RawValue?.let {
            try {
                val json = JSONObject(it)
                // 尝试从param_island -> bigIslandArea中解析
                val paramIsland = json.optJSONObject("param_island")
                val bigIsland = paramIsland?.optJSONObject("bigIslandArea")
                
                // 如果没有找到，尝试直接从bigIsland字段解析
                return bigIsland ?: json.optJSONObject("bigIsland")
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛: 解析bigIsland失败: ${e.message}")
            }
        }
        return null
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
    ): Notification {
        try {
            // 先构建胶囊兼容的通知
            val capsuleBuilder = buildCapsuleCompatibleNotification(context, builder, title, text, appName, paramV2, picMap, paramV2Raw)
            
            // 构建通知并注入图标
            val notification = capsuleBuilder.build()
        
        // 尝试从 A/B 区数据中获取图标或生成位图
        var smallIconBitmap: Bitmap? = null
        
        // 解析 param_v2 中的 bigIsland 数据
        val paramV2RawValue = paramV2Raw ?: paramV2?.toString()
        val bigIsland = parseBigIsland(paramV2RawValue)
        
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
            smallIconBitmap = BitmapUtils.progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
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
                    smallIconBitmap = BitmapUtils.textToBitmap(textToRender)
                    Logger.d(TAG, "超级岛: 文本位图生成结果: ${smallIconBitmap != null}")
                }
            } else {
                // 计时器类型，不生成文本位图，保留之前的图标
                Logger.d(TAG, "超级岛: 计时器类型，保留之前的小图标，不生成文本位图")
            }
        }
        
        // 处理图标
        if (smallIconBitmap == null) {
            // 优先使用 A 区图标或B区图标
            val picKeyToUse = aPicKey ?: bPicKey
            Logger.d(TAG, "超级岛: 处理 A 区图标或B区图标 - picKeyToUse: $picKeyToUse, picMap: ${picMap?.keys}")
            if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
                val picUrl = picMap[picKeyToUse]
                if (!picUrl.isNullOrBlank()) {
                    // 同步下载图标
                    Logger.d(TAG, "超级岛: 使用 A 区图标或B区图标作为小图标")
                    val bitmap = runBlocking {
                        downloadBitmap(context, picUrl, 5000)
                    }
                    if (bitmap != null) {
                        smallIconBitmap = bitmap
                        Logger.d(TAG, "超级岛: A 区图标或B区图标加载成功")
                    } else {
                        Logger.w(TAG, "超级岛: A 区图标或B区图标加载失败")
                    }
                }
            }
        }
        
        // 如果没有 A 区图标或B区图标，再使用应用图标
        if (smallIconBitmap == null) {
            // 使用应用图标（大图标的键值提供的图标）
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
        
        // 如果没有生成位图，使用默认图标（改为本应用图标）
        if (smallIconBitmap == null) {
            Logger.d(TAG, "超级岛: 没有生成位图，使用本应用图标作为默认图标")
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
                // 停止对应的滚动更新
                stopScrollUpdate(key)
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
                    // 停止对应的滚动更新
                    stopScrollUpdate(key)
                    Logger.i(TAG, "超级岛: 取消复刻通知成功，key=$key, notificationId=$notificationId")
                }
            }
            
            // 清空映射
            entryKeyToNotificationId.clear()
            // 清空所有滚动更新
            clearAllScrollUpdates()
            Logger.i(TAG, "超级岛: 清除所有复刻通知成功")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 清除所有复刻通知失败: ${e.message}")
        }
    }
}
