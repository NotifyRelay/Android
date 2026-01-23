package com.xzyht.notifyrelay.feature.notification.superisland

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.common.core.util.IntentUtils
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.parseParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingComposeContainer
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowLifecycleOwner
import com.xzyht.notifyrelay.feature.notification.superisland.floating.FloatingWindowManager
import com.xzyht.notifyrelay.feature.notification.superisland.floating.LifecycleManager
import com.xzyht.notifyrelay.feature.notification.superisland.floating.common.SuperIslandImageUtil
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.Gson
import org.json.JSONObject

/**
 * 接收端的超级岛复刻实现骨架。
 * 说明：真正的系统级悬浮窗需要用户授予 "悬浮窗/Display over other apps" 权限（TYPE_APPLICATION_OVERLAY），
 * 如果没有权限则退化为发送高优先级临时通知来提示用户（不会获得和系统超级岛完全一致的视觉效果）。
 */
object FloatingReplicaManager {
    private const val TAG = "超级岛"
    private const val FIXED_WIDTH_DP = 320 // 固定悬浮窗宽度，以确保MultiProgressRenderer完整显示

    // Compose浮窗管理器
    private val floatingWindowManager = FloatingWindowManager().apply {
        // 设置条目为空时的回调
        onEntriesEmpty = { removeOverlayContainer() }
    }
    // Compose生命周期管理器
    private val lifecycleManager = LifecycleManager()
    // 提供给 Compose 的生命周期所有者（不依赖 ViewTree）
    private var overlayLifecycleOwner: FloatingWindowLifecycleOwner? = null

    // 浮窗容器视图
    private var overlayView: WeakReference<View>? = null
    // 浮窗布局参数
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    // WindowManager实例
    private var windowManager: WeakReference<WindowManager>? = null

    // 会话级屏蔽池：进程结束后自然清空，value 为最后屏蔽时间戳
    private val blockedInstanceIds = ConcurrentHashMap<String, Long>()
    // 会话级屏蔽过期时间（默认 15 秒），避免用户刚刚关闭后立即再次弹出
    private const val BLOCK_EXPIRE_MS = 15_000L

    // 保存sourceId到entryKey列表的映射，以便后续能正确移除条目
    // 一个sourceId可能对应多个条目，所以使用列表保存
    private val sourceIdToEntryKeyMap = mutableMapOf<String, MutableList<String>>()
    
    // 保存entryKey到notificationId的映射，用于管理复刻通知
    private val entryKeyToNotificationId = mutableMapOf<String, Int>()
    // 通知渠道ID
    private const val NOTIFICATION_CHANNEL_ID = "super_island_replica"
    // 通知ID基础值
    private const val NOTIFICATION_BASE_ID = 20000

    /**
     * 显示超级岛复刻悬浮窗。
     * paramV2Raw: miui.focus.param 中 param_v2 的原始 JSON 字符串（可为 null）
     * picMap: 从 extras 中解析出的图片键->URL 映射（可为 null）
     */
    // sourceId: 用于区分不同来源的超级岛通知（通常传入 superPkg），用于刷新/去重同一来源的浮窗
    fun showFloating(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        paramV2Raw: String? = null,
        picMap: Map<String, String>? = null,
        appName: String? = null,
        isLocked: Boolean = false
    ) {
        try {
            // 会话级屏蔽检查：同一个 instanceId 在本轮被用户关闭后不再展示
            if (sourceId.isNotBlank() && isInstanceBlocked(sourceId)) {
                Logger.i(TAG, "超级岛: instanceId=$sourceId 已在本轮会话中被屏蔽，忽略展示")
                return
            }

            if (!canShowOverlay(context)) {
                Logger.i(TAG, "超级岛: 无悬浮窗权限，尝试请求权限")
                requestOverlayPermission(context)
                return
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // 预先准备生命周期所有者，供 Compose 注入 LocalLifecycleOwner 使用
                    if (overlayLifecycleOwner == null) {
                        overlayLifecycleOwner = FloatingWindowLifecycleOwner()
                    }
                    // 通知Compose生命周期管理器浮窗显示
                    lifecycleManager.onShow()
                    // 尝试解析paramV2
                    val paramV2 = parseParamV2Safe(paramV2Raw)

                    // 判断是否为摘要态
                    val summaryOnly = when {
                        paramV2?.business == "miui_flashlight" -> true
                        paramV2Raw?.contains("miui_flashlight") == true -> true
                        else -> false
                    }

                    // 将所有图片 intern 为引用，避免重复保存相同图片
                    val internedPicMap = SuperIslandImageStore.internAll(context, picMap)
                    // 生成唯一的entryKey，确保包含sourceId，以便后续能正确移除
                    // 对于同一通知的不同时间更新，应该使用相同的key，所以不能包含时间戳
                    val entryKey = sourceId

                    // 更新Compose浮窗管理器的条目
                    // 锁屏状态下不自动展开，非锁屏状态保持原有逻辑
                    floatingWindowManager.addOrUpdateEntry(
                        key = entryKey,
                        paramV2 = paramV2,
                        paramV2Raw = paramV2Raw,
                        picMap = internedPicMap,
                        isExpanded = if (isLocked) false else !summaryOnly,
                        summaryOnly = summaryOnly,
                        business = paramV2?.business,
                        title = title,
                        text = text,
                        appName = appName
                    )

                    // 保存sourceId到entryKey的映射，以便后续能正确移除
                    if (sourceId.isNotBlank()) {
                        // 如果sourceId已存在，添加到列表中；否则创建新列表
                        val entryKeys = sourceIdToEntryKeyMap.getOrPut(sourceId) { mutableListOf() }
                        // 确保每个entryKey只添加一次
                        if (!entryKeys.contains(entryKey)) {
                            entryKeys.add(entryKey)
                        }
                    }

                    // 创建或更新浮窗UI
                    addOrUpdateEntry(context, entryKey, summaryOnly)
                    
                    // 发送复刻通知
                    sendReplicaNotification(context, entryKey, title, text, appName, paramV2, internedPicMap)
                } catch (e: Exception) {
                    Logger.w(TAG, "超级岛: 显示浮窗失败(协程): ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 显示浮窗失败，退化为通知: ${e.message}")
        }
    }

    // ---- 会话级屏蔽工具方法 ----

    private fun isInstanceBlocked(instanceId: String?): Boolean {
        if (instanceId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val ts = blockedInstanceIds[instanceId] ?: return false
        // 检查是否超过过期时间
        if (now - ts > BLOCK_EXPIRE_MS) {
            blockedInstanceIds.remove(instanceId)
            Logger.i(TAG, "超级岛: 屏蔽过期，自动移除 instanceId=$instanceId")
            return false
        }
        // 如果会话仍在活跃（有新请求），更新屏蔽时间，让屏蔽继续保持
        blockedInstanceIds[instanceId] = now
        return true
    }

    private fun blockInstance(instanceId: String?) {
        if (instanceId.isNullOrBlank()) return
        blockedInstanceIds[instanceId] = System.currentTimeMillis()
        Logger.i(TAG, "超级岛: 会话级屏蔽 instanceId=$instanceId")
    }

    // 兼容空值的 param_v2 解析包装，避免在调用点产生空值分支和推断问题
    private fun parseParamV2Safe(raw: String?): ParamV2? {
        return try {
            val s = raw ?: return null
            if (s.isBlank()) null else parseParamV2(s)
        } catch (_: Exception) { null }
    }

    private fun onEntryClicked(key: String) {
        // 使用FloatingWindowManager管理条目状态
        val entry = floatingWindowManager.getEntry(key)
        if (entry == null) {
            return
        }

        // 切换展开/折叠状态，toggleEntryExpanded内部会处理摘要态的情况
        floatingWindowManager.toggleEntryExpanded(key)
    }
    
    /**
     * 处理容器拖动开始事件
     */
    private fun onContainerDragStarted() {
        // 移除原来的关闭层相关逻辑
    }

    /**
     * 处理容器拖动结束事件
     */
    private fun onContainerDragEnded() {
        // 移除原来的关闭层相关逻辑
    }
    
    /**
     * 发送复刻通知，与原通知保持一致
     */
    private fun sendReplicaNotification(
        context: Context,
        key: String,
        title: String?,
        text: String?,
        appName: String?,
        paramV2: ParamV2?,
        picMap: Map<String, String>?
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建通知渠道（Android O及以上）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "超级岛复刻通知",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            // 生成唯一的通知ID
            val notificationId = key.hashCode().and(0xffff) + NOTIFICATION_BASE_ID
            
            // 构建基础通知，调整属性使其更接近实际超级岛通知
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
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
            
            // 添加超级岛相关的结构化数据，严格按照小米官方文档规范和实际通知结构
            val extras = builder.extras
            
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
                extras.putBoolean("android.chronometerCountDown", false)
                extras.putBoolean("android.showChronometer", true)
            }
            
            // 添加应用信息，与原始通知保持一致
            extras.putBoolean("android.reduced.images", true)
            
            // 添加超级岛源包信息，与原始通知保持一致
            extras.putString("superIslandSourcePackage", context.packageName)
            
            // 添加包名信息，与原始通知保持一致
            extras.putString("app_package", context.packageName)
            
            // 发送通知
            notificationManager.notify(notificationId, builder.build())
            
            // 保存entryKey到notificationId的映射
            entryKeyToNotificationId[key] = notificationId
            
            Logger.i(TAG, "超级岛: 发送复刻通知成功，key=$key, notificationId=$notificationId")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 发送复刻通知失败: ${e.message}")
        }
    }
    
    /**
     * 取消复刻通知
     */
    private fun cancelReplicaNotification(context: Context, key: String) {
        try {
            val notificationId = entryKeyToNotificationId.remove(key)
            if (notificationId != null) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
                Logger.i(TAG, "超级岛: 取消复刻通知成功，key=$key, notificationId=$notificationId")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 取消复刻通知失败: ${e.message}")
        }
    }
    
    /**
     * 根据通知ID关闭对应的浮窗条目
     */
    fun closeByNotificationId(notificationId: Int) {
        try {
            // 查找对应的entryKey
            val entryKey = entryKeyToNotificationId.entries.find { it.value == notificationId }?.key
            if (entryKey != null) {
                // 移除浮窗条目
                floatingWindowManager.removeEntry(entryKey)
                // 从映射中移除
                entryKeyToNotificationId.remove(entryKey)
                // 从sourceId映射中移除，并将sourceId添加到黑名单
                sourceIdToEntryKeyMap.forEach { (sourceId, keys) ->
                    if (keys.contains(entryKey)) {
                        keys.remove(entryKey)
                        if (keys.isEmpty()) {
                            sourceIdToEntryKeyMap.remove(sourceId)
                            // 当用户手动移除通知关闭浮窗时，将sourceId添加到黑名单，避免短时间内再次弹出
                            blockInstance(sourceId)
                        }
                    }
                }
                Logger.i(TAG, "超级岛: 根据通知ID关闭浮窗条目成功，notificationId=$notificationId, entryKey=$entryKey")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 根据通知ID关闭浮窗条目失败: ${e.message}")
        }
    }
    
    /**
     * 关闭所有浮窗条目
     */
    fun closeAllEntries() {
        try {
            // 清空所有条目
            floatingWindowManager.clearAllEntries()
            // 清空映射
            entryKeyToNotificationId.clear()
            sourceIdToEntryKeyMap.clear()
            Logger.i(TAG, "超级岛: 关闭所有浮窗条目成功")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 关闭所有浮窗条目失败: ${e.message}")
        }
    }

    // 新增：按来源键立刻移除指定浮窗（用于接收终止事件SI_END时立即消除）
    fun dismissBySource(sourceId: String) {
        try {
            val context = overlayView?.get()?.context
            
            // 从映射中获取所有对应的entryKey
            val entryKeys = sourceIdToEntryKeyMap[sourceId]
            if (entryKeys != null) {
                // 移除所有相关条目
                entryKeys.forEach { entryKey ->
                    floatingWindowManager.removeEntry(entryKey)
                    // 取消对应的复刻通知
                    if (context != null) {
                        cancelReplicaNotification(context, entryKey)
                    } else {
                        // 如果没有上下文，直接从映射中移除
                        entryKeyToNotificationId.remove(entryKey)
                    }
                }
                // 清理映射关系
                sourceIdToEntryKeyMap.remove(sourceId)
            } else {
                // 如果没有找到映射，尝试直接使用sourceId移除
                floatingWindowManager.removeEntry(sourceId)
                // 取消对应的复刻通知
                if (context != null) {
                    cancelReplicaNotification(context, sourceId)
                } else {
                    // 如果没有上下文，直接从映射中移除
                    entryKeyToNotificationId.remove(sourceId)
                }
            }
            // 如果对应的会话结束（SI_END），同步移除黑名单，允许后续同一通知重新展示
            blockedInstanceIds.remove(sourceId)
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 按来源关闭浮窗失败: ${e.message}")
        }
    }

    /**
     * 移除浮窗容器
     */
    private fun removeOverlayContainer() {
        try {
            val view = overlayView?.get()
            val wm = windowManager?.get()
            val lp = overlayLayoutParams

            if (view != null && wm != null && lp != null) {
                // 移除浮窗容器
                wm.removeView(view)
                Logger.i(TAG, "超级岛: 浮窗容器已移除")

                // 清理资源
                overlayView = null
                overlayLayoutParams = null
                windowManager = null

                // 通知Compose生命周期管理器浮窗隐藏
                lifecycleManager.onHide()

                // 调用生命周期所有者的onHide方法
                overlayLifecycleOwner?.let {
                    try { it.onHide() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 移除浮窗容器失败: ${e.message}")
            // 即使移除失败，也要清理资源引用，避免内存泄漏
            overlayView = null
            overlayLayoutParams = null
            windowManager = null
        }
    }

    /**
     * 添加或更新浮窗条目
     * @param context 上下文
     * @param key 条目唯一标识
     * @param summaryOnly 是否为摘要态
     */
    private fun addOrUpdateEntry(
        context: Context,
        key: String,
        summaryOnly: Boolean
    ) {
        try {
            // 首条条目到来时再创建 Overlay 容器，避免先有空容器
            if (overlayView?.get() == null || windowManager?.get() == null || overlayLayoutParams == null) {
                    try {
                        val appCtx = context.applicationContext
                        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                            ?: return

                        // 确保存在用于 Compose 的生命周期所有者（不依赖 ViewTree）
                        val lifecycleOwner = overlayLifecycleOwner ?: FloatingWindowLifecycleOwner().also {
                            overlayLifecycleOwner = it
                        }
                        // 标记浮窗进入前台生命周期，供 Compose 使用
                        try { lifecycleOwner.onShow() } catch (_: Exception) {}
                        // 通知Compose生命周期管理器浮窗显示
                        lifecycleManager.onShow()

                        val density = context.resources.displayMetrics.density
                        val layoutParams = WindowManager.LayoutParams(
                            (FIXED_WIDTH_DP * density).toInt(),
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                            PixelFormat.TRANSLUCENT
                        ).apply {
                            gravity = Gravity.LEFT or Gravity.TOP
                            x = ((context.resources.displayMetrics.widthPixels - (FIXED_WIDTH_DP * density).toInt()) / 2).coerceAtLeast(0)
                            y = 100
                        }

            // 使用Compose容器替代传统的FrameLayout和LinearLayout
            val composeContainer = FloatingComposeContainer(context).apply {
                val padding = (12 * density).toInt()
                setPadding(padding, padding, padding, padding)
                // 设置浮窗管理器
                this.floatingWindowManager = this@FloatingReplicaManager.floatingWindowManager
                // 设置生命周期所有者
                this.lifecycleOwner = lifecycleOwner
                // 设置WindowManager和LayoutParams，用于更新浮窗位置
                this.windowManager = wm
                this.windowLayoutParams = layoutParams
                // 设置条目点击回调
                this.onEntryClick = { entryKey -> onEntryClicked(entryKey) }
                // 设置容器拖动开始回调
                this.onContainerDragStart = { onContainerDragStarted() }
                // 设置容器拖动中回调，移除了关闭区重叠检测逻辑
                this.onContainerDragging = { }
                // 设置容器拖动结束回调
                this.onContainerDragEnd = { onContainerDragEnded() }
            }

                        var added = false
                        try {
                            wm.addView(composeContainer, layoutParams)
                            added = true
                        } catch (e: Exception) {
                            Logger.w(TAG, "超级岛: addView 失败: ${e.message}")
                        }
                        if (added) {
                            overlayView = WeakReference(composeContainer)
                            overlayLayoutParams = layoutParams
                            windowManager = WeakReference(wm)
                            Logger.i(TAG, "超级岛: 浮窗容器已创建(首条条目触发)，x=${layoutParams.x}, y=${layoutParams.y}")
                        }
                    } catch (e: Exception) {
                        Logger.w(TAG, "超级岛: 创建浮窗容器失败: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: addOrUpdateEntry 出错: ${e.message}")
        }
    }

    private fun canShowOverlay(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    private fun requestOverlayPermission(context: Context) {
        try {
            val intent = IntentUtils.createImplicitIntent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            IntentUtils.startActivity(context, intent, true)
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 请求悬浮窗权限失败: ${e.message}")
        }
    }

    private suspend fun downloadBitmapByKey(context: Context, picMap: Map<String, String>?, key: String?): Bitmap? {
        if (picMap.isNullOrEmpty() || key.isNullOrBlank()) return null
        val raw = picMap[key] ?: return null
        val url = SuperIslandImageStore.resolve(context, raw) ?: raw
        return withContext(Dispatchers.IO) { downloadBitmap(context, url, 5000) }
    }

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

    private suspend fun downloadBitmap(context: Context, url: String, timeoutMs: Int): Bitmap? {
        return try {
            SuperIslandImageUtil.loadBitmapSuspend(context, url, timeoutMs)
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛: 下载图片失败: ${e.message}")
            null
        }
    }
}