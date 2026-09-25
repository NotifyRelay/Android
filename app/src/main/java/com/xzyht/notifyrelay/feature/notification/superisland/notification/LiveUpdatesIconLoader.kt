package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.graphics.Bitmap
import android.os.Build
import androidx.collection.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.xzyht.notifyrelay.feature.notification.superisland.intent.NotificationIntentFactory
import github.xzynine.superislandui.model.core.ParamV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.base.util.image.ImageUtils

/**
 * 超级岛进度通知的图标加载器。
 *
 * 由 [LiveUpdatesNotificationManager] 拆分而来，仅做搬移，逻辑与原实现逐行一致。
 *
 * 线程约定（与拆分前完全一致，**不要**顺手改动）：
 * - [loadIconsAndUpdateNotification] 在 `Dispatchers.IO` 上启动协程，
 *   并在 IO 线程读取/写入 [iconCache]；
 * - [updateNotificationWithAllIcons] 通过 `withContext(Dispatchers.Main)` 切回主线程后再更新通知。
 *
 * 注意：[iconCache] 是既有的共享可变 LruCache（上限 10），原实现本身就存在
 * 「IO 线程写、Main 线程读」的跨线程访问，本拆分保持原样。
 *
 * 该并发访问**不需要额外加锁**：`androidx.collection.LruCache` 内部以 `Lock` 对
 * `get`/`put`/`evictAll` 等全部公开操作做了同步（类文档明确声明 "This class is thread-safe."），
 * 本项目实际解析到的 1.6.0 版本亦然。故不要为其再包一层全局锁，那只会无谓拖慢图标加载。
 */
internal object LiveUpdatesIconLoader {
    private const val TAG = LiveUpdatesNotificationManager.TAG
    private const val ICON_CACHE_SIZE = 10 // 最大缓存10个图标

    // 图标缓存，避免重复加载图标
    internal val iconCache =
        object : LruCache<String, Bitmap>(ICON_CACHE_SIZE) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int {
                // 返回1，表示每个图标计数为1，这样maxSize就表示图标数
                return 1
            }
        }

    /**
     * 清空图标缓存（供公平运行内存回调使用）。
     */
    internal fun clearIconCache() {
        iconCache.evictAll()
    }

    /**
     * 异步加载图标并更新通知
     */
    internal fun loadIconsAndUpdateNotification(
        sourceId: String,
        notificationId: Int,
        paramV2: ParamV2?,
        picMap: Map<String, String>?,
    ) {
        // 只在有图标资源时才异步加载图标
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
        picMap: Map<String, String>,
    ) {
        val progressInfo = paramV2.progressInfo
        val multiProgressInfo = paramV2.multiProgressInfo

        // 检查是否有任何进度信息
        if (progressInfo == null && multiProgressInfo == null) {
            return
        }

        // 获取当前进度�?
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
        val forwardIconKey =
            listOf(
                allIconKeys["picForward"],
                allIconKeys["picForwardBox"],
                allIconKeys["picMiddle"],
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
                val bitmap =
                    ImageUtils.loadBitmap(
                        context = LiveUpdatesNotificationManager.appContext,
                        uri = appIconUrl,
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

                    val bitmap =
                        ImageUtils.loadBitmap(
                            context = LiveUpdatesNotificationManager.appContext,
                            uri = iconUrl,
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
                Logger.w(TAG, "未找到有效的前进图标")
            }
        }

        // 在主线程统一更新通知，确保两个图标都能显示
        withContext(Dispatchers.Main) {
            updateNotificationWithAllIcons(
                sourceId,
                notificationId,
                paramV2,
                appIconBitmap,
                progressIconBitmap,
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
        progressIcon: Bitmap?,
    ) {
        try {
            // 构建基础通知
            val updatedBuilder = LiveUpdatesNotificationManager.buildBaseNotification()

            // 设置基础信息
            paramV2.baseInfo?.let {
                val title = it.title ?: ""
                val content = it.content ?: ""

                // 处理HTML，使用LEGACY模式确保颜色标签被支持
                val (processedTitle, processedContent) = LiveUpdatesNotificationManager.processHtmlText(title, content)

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
            val (processedTitle, processedContent) =
                LiveUpdatesNotificationManager.processHtmlText(
                    paramV2.baseInfo?.title,
                    paramV2.baseInfo?.content,
                )

            val shortText =
                when {
                    processedTitle.isNotEmpty() -> processedTitle
                    processedContent.isNotEmpty() -> processedContent
                    else -> " "
                }
            updatedBuilder.setShortCriticalText(shortText)

            // 检查浮窗功能是否开启；判定统一走 NotificationIntentFactory.needClickIntent
            val needClickIntent = NotificationIntentFactory.needClickIntent(LiveUpdatesNotificationManager.appContext)

            // 创建删除意图，用于处理用户移除通知时关闭浮窗
            val deleteIntent =
                if (needClickIntent) {
                    NotificationIntentFactory.createDeleteIntent(LiveUpdatesNotificationManager.appContext, notificationId)
                } else {
                    null
                }

            // 创建点击意图，用于处理用户点击通知时切换浮窗或切换列表
            val contentIntent =
                if (needClickIntent) {
                    NotificationIntentFactory.createPendingContentIntent(
                        context = LiveUpdatesNotificationManager.appContext,
                        notificationId = notificationId,
                        sourceId = sourceId,
                        title = paramV2.baseInfo?.title,
                        text = paramV2.baseInfo?.content,
                        appName = null,
                        paramV2Raw = null,
                    )
                } else {
                    null
                }

            // 设置意图
            if (needClickIntent) {
                updatedBuilder
                    .setDeleteIntent(deleteIntent)
                    .setContentIntent(contentIntent)
            }

            // 处理进度样式通知（仅处理进度类型）
            val progressInfo = paramV2.progressInfo
            val multiProgressInfo = paramV2.multiProgressInfo

            // 与官方示例保持一致：先获取基础样式，再增量添加图标和进度
            val progressStyle =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    LiveUpdatesProgressStyleBuilder.buildBaseProgressStyle(paramV2)
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

            LiveUpdatesNotificationManager.notificationManager.notify(notificationId, updatedBuilder.build())
        } catch (e: Exception) {
            Logger.w(TAG, "更新通知所有图标失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
