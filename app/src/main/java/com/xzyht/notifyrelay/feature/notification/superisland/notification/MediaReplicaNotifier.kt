package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.data.SuperIslandStructuredDataHelper
import github.xzynine.superislandui.common.BitmapUtils
import github.xzynine.superislandui.common.CapsuleScrollManager
import github.xzynine.superislandui.common.TextSplitter
import github.xzynine.superislandui.floating.smallisland.right.bProgress
import github.xzynine.superislandui.floating.smallisland.right.bProgressColorReach
import github.xzynine.superislandui.floating.smallisland.right.bProgressColorUnReach
import github.xzynine.superislandui.floating.smallisland.right.bProgressIsCCW
import github.xzynine.superislandui.floating.smallisland.right.textToRender
import github.xzynine.superislandui.model.core.ParamV2
import notifyrelay.base.util.DeviceUtils
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager

/**
 * 媒体类型（business == "media"）复刻通知构建。
 *
 * 负责：歌词/图标文本拆分、胶囊滚动、结构化数据注入、滚动更新启动，
 * 以及按双开关（超级岛 / Live Updates）决定的小图标处理（clearSmallIcon 或 injectSmallIcon）。
 * 两个分支互斥，不可合并。
 */
internal object MediaReplicaNotifier {
    private const val TAG = "超级岛通知生成"

    suspend fun build(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String?,
        text: String?,
        appName: String?,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        paramV2: ParamV2?,
        key: String,
        notificationId: Int,
        notificationManager: NotificationManager,
    ): Notification {
        // 检查规范信息注入模式
        val isSuperIslandEnabled = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(context)
        val isLiveUpdatesEnabled = SuperIslandConfigUtils.isLiveUpdatesSpecInjectionEnabled(context)

        // 处理歌词拆分和显示
        val lyricText = title ?: ""
        var capsuleText = lyricText
        var iconText = ""

        // 检查歌词分割模式设置                // 0=默认（平板不分割，手机分割）1=分割 2=不分割
        val lyricsSplitMode = StorageManager.getInt(context, "lyrics_split_mode", 0)
        val shouldSplit =
            when (lyricsSplitMode) {
                1 -> true
                2 -> false
                else -> !DeviceUtils.isTablet(context)
            }

        if (shouldSplit) {
            // 当歌词超过阈值时，拆分为图标文本和胶囊文本
            // 远端和本地都保持6字符开始分割
            val threshold = 12
            val textLength = TextSplitter.calculateTextLength(lyricText)
            if (textLength > threshold) {
                // 使用TextSplitter工具类进行歌词拆分
                val (splitIconText, splitCapsuleText) = TextSplitter.splitLyric(lyricText, threshold)
                iconText = splitIconText
                capsuleText = splitCapsuleText
            }
        } else {
            // 不分割时，不进行任何截断和拆分，完整显示所有文本
            capsuleText = lyricText
            iconText = ""
        }

        // 使用CapsuleScrollManager处理胶囊文本滚动
        val scrollKey = "${key}_scroll"
        val displayText = CapsuleScrollManager.getCurrentDisplayText(scrollKey, capsuleText)

        // 设置右侧文本为拆分后的歌词
        builder.setContentTitle(capsuleText)

        // 仅 Live Updates 模式时注入胶囊文本和添加 ProgressStyle
        var progressStyle: NotificationCompat.ProgressStyle? = null
        if (isLiveUpdatesEnabled) {
            // 设置胶囊文本
            builder.setShortCriticalText(displayText)

            // 添加 ProgressStyle
            try {
                val segment = NotificationCompat.ProgressStyle.Segment(100)
                val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>()
                segments.add(segment)

                progressStyle =
                    NotificationCompat
                        .ProgressStyle()
                        .setProgressSegments(segments)
                        .setStyledByProgress(true)
                        .setProgress(0)

                builder.setStyle(progressStyle)
            } catch (e: Exception) {
                Logger.e(TAG, "设置胶囊样式失败: ${e.message}")
            }
        }

        // picMap 已在调用方通过 SuperIslandDataFormatter 解析，直接使用
        val resolvedPicMap = picMap ?: emptyMap()

        // 仅超级岛模式时注入超级岛结构化数据
        if (isSuperIslandEnabled) {
            SuperIslandStructuredDataHelper.addMediaSuperIslandStructuredData(
                builder = builder,
                context = context,
                title = title,
                text = text,
                picMap = resolvedPicMap,
                iconText = iconText,
                capsuleText = capsuleText,
            )
        }

        // 仅 Live Updates 模式时设置滚动更新机制
        // 需在结构化数据注入之后启动，确保滚动更新首次执行时 extras 完整（模拟渲染不丢失）
        if (isLiveUpdatesEnabled) {
            ReplicaScrollUpdater.setupScrollUpdate(
                key,
                scrollKey,
                capsuleText,
                context,
                notificationId,
                originalBuilder = builder,
                notificationManager,
                progressStyle = progressStyle,
            )
        }

        // 构建通知
        val notification = builder.build()

        // 生成并注入动态图标
        if (isSuperIslandEnabled) {
            // 超级岛模式：不注入小图标（左岛由图文组件渲染专辑图，V3 模板不依赖小图标）
            // 清除 setSmallIcon 设置的默认图标（替换为透明占位），避免默认图抢占左岛专辑图展示
            ReplicaSmallIconInjector.clearSmallIconForSuperIsland(notification)
            Logger.i(TAG, "超级岛 超级岛注入模式：不注入小图标，左岛由图文组件渲染专辑图")
        } else if (iconText.isNotEmpty()) {
            val albumBitmap = ReplicaSmallIconInjector.loadAlbumBitmapOrNull(context, picMap, iconText.length)
            val iconBitmap = BitmapUtils.textToBitmap(iconText, albumBitmap = albumBitmap)
            if (iconBitmap != null) {
                ReplicaSmallIconInjector.injectSmallIconWithCache(notification, iconBitmap, key)
            }
        } else {
            // 没有图标文本时，尝试使用专辑图作为小图标
            val coverKey = "miui.focus.pic_cover"
            if (!picMap.isNullOrEmpty() && picMap.containsKey(coverKey)) {
                val coverUrl = picMap[coverKey]
                if (!coverUrl.isNullOrBlank()) {
                    // 同步下载专辑图
                    val bitmap = ReplicaSmallIconInjector.downloadBitmap(context, coverUrl)
                    if (bitmap != null) {
                        ReplicaSmallIconInjector.injectSmallIconWithCache(notification, bitmap, key)
                    }
                }
            }
        }

        // 检查是否已经有图标文本，如果有，就不再生成新的图标
        // 超级岛注入模式下不注入小图标（小图标会抢占左岛展示，左岛应由图文组件渲染专辑图）
        if (!isSuperIslandEnabled && iconText.isEmpty()) {
            // 尝试从A/B区数据中获取图标或生成位图
            var smallIconBitmap: Bitmap? = null

            // 使用已解析的 paramV2 中的组件数据
            val bigIslandArea = paramV2?.paramIsland?.bigIslandArea
            val bComponent = bigIslandArea?.bComponent

            // 提取进度数据
            val bProgress = bComponent.bProgress
            val bProgressColorReach = bComponent.bProgressColorReach
            val bProgressColorUnReach = bComponent.bProgressColorUnReach
            val bProgressIsCCW = bComponent.bProgressIsCCW

            // 处理进度数据，生成位图
            if (bProgress != null) {
                smallIconBitmap = BitmapUtils.progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
            }

            // 如果没有进度数据，尝试生成文本位图
            if (smallIconBitmap == null) {
                // 优先使用B区文本生成位图
                val textToRender = bComponent.textToRender

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
                        // 异步下载应用图标
                        val bitmap = ReplicaSmallIconInjector.downloadBitmap(context, appIconUrl)
                        if (bitmap != null) {
                            smallIconBitmap = bitmap
                        }
                    }
                }
            }

            // 注入小图标
            // 只有当smallIconBitmap不为null时才注入，否则保留之前的图标
            if (smallIconBitmap != null) {
                ReplicaSmallIconInjector.injectSmallIconWithCache(notification, smallIconBitmap, key)
            } else {
                // 保留之前的图标，不进行修改
            }
        } else {
            // 已经有图标文本，保留之前的图标，不进行修改
        }

        return notification
    }
}
