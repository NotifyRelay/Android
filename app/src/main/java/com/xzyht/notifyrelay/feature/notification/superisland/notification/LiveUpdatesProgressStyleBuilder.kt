package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.toColorInt
import github.xzynine.superislandui.model.core.ParamV2
import notifyrelay.base.util.Logger

/**
 * 进度样式通知构建器：集中构建超级岛进度类通知的 ProgressStyle。
 *
 * 由 [LiveUpdatesNotificationManager] 拆分而来，仅做搬移，逻辑与原实现逐行一致。
 *
 * 注意：[buildProgressStyleNotification] 与 [buildBaseProgressStyle] 高度相似但**语义不同**，
 * 前者是通用进度类型路径（节点位置保留 0%/100%），后者是 BAKLAVA 专属路径
 * （节点位置避开 0%/100%），两者不得合并。
 */
internal object LiveUpdatesProgressStyleBuilder {
    private const val TAG = LiveUpdatesNotificationManager.TAG

    /**
     * 构建包含图标的进度样式通知，避免图标闪烁
     */
    internal fun buildProgressStyleNotification(
        builder: NotificationCompat.Builder,
        paramV2: ParamV2,
        picMap: Map<String, String>? = null,
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
                val (processedTitle, processedContent) = LiveUpdatesNotificationManager.processHtmlText(title, content)

                Logger.d(TAG, "处理后标题: $processedTitle")
                Logger.d(TAG, "处理后内容: $processedContent")

                // 参�?NotificationGenerator.kt 的逻辑设置文本
                builder
                    .setContentTitle(processedTitle)
                    .setContentText(processedContent)
            }

            // 获取颜色配置
            val progressColor = progressInfo?.colorProgress ?: multiProgressInfo?.color
            val progressEndColor = progressInfo?.colorProgressEnd ?: multiProgressInfo?.color

            // 解析颜色配置
            val pointColor = progressColor?.toColorInt() ?: Color.BLUE
            val segmentColor = progressEndColor?.toColorInt() ?: Color.CYAN

            // 直接创建ProgressStyle实例
            val progressStyle = NotificationCompat.ProgressStyle()

            // 声明变量，用于记录节点和分段数量
            var progressPointsCount = 0
            var progressSegmentsCount = 0

            // 只有multiProgressInfo 存在时才生成节点和分段
            if (multiProgressInfo != null) {
                // 根据 multiProgressInfo.points 生成进度点和分段
                val nodeCount = multiProgressInfo.points ?: 4
                val validNodeCount = maxOf(2, nodeCount) // 最少需2个节点来创建分段
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

                Logger.d(TAG, "multiProgressInfo 生成 $progressPointsCount 个节点，分别在位置 $nodePositions")
            }

            // 尝试直接设置进度跟踪器图标，避免闪烁
            if (picMap != null && picMap.isNotEmpty()) {
                // 找到有效的前进图标作为进度指示点
                val possibleIconKeys =
                    listOf(
                        progressInfo?.picForward,
                        multiProgressInfo?.picForward,
                        multiProgressInfo?.picForwardBox,
                        progressInfo?.picMiddle,
                        multiProgressInfo?.picMiddle,
                    )

                val iconKey =
                    possibleIconKeys.firstOrNull { key ->
                        key != null && picMap.containsKey(key)
                    }

                if (iconKey != null) {
                    val iconUrl = picMap[iconKey]
                    if (iconUrl != null) {
                        // 尝试从缓存加载图标
                        val cachedBitmap = LiveUpdatesIconLoader.iconCache.get(iconUrl)
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

            Logger.d(TAG, "设置 $progressPointsCount 个进度点 $progressSegmentsCount 个进度段，与官方示例保持一致")

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
                val (processedTitle, processedContent) = LiveUpdatesNotificationManager.processHtmlText(title, content)

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
                false,
            )
        }
    }

    /**
     * 构建基础进度样式，与官方示例保持一致
     */
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    internal fun buildBaseProgressStyle(paramV2: ParamV2): NotificationCompat.ProgressStyle {
        val progressInfo = paramV2.progressInfo
        val multiProgressInfo = paramV2.multiProgressInfo

        // 获取颜色配置
        val progressColor = progressInfo?.colorProgress ?: multiProgressInfo?.color
        val progressEndColor = progressInfo?.colorProgressEnd ?: multiProgressInfo?.color

        // 解析颜色配置
        val pointColor = progressColor?.toColorInt() ?: Color.BLUE
        val segmentColor = progressEndColor?.toColorInt() ?: Color.CYAN

        // 直接创建ProgressStyle实例
        val progressStyle = NotificationCompat.ProgressStyle()

        // 只有 multiProgressInfo 存在时才生成节点和分段
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
                // 调整位置，避免使用0和100%，因为原生通知可能不支持这两个值
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
}
