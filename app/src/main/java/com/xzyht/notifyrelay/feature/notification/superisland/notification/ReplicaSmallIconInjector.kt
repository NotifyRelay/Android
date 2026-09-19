package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.R
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.data.SuperIslandStructuredDataHelper
import github.xzynine.superislandui.common.BitmapUtils
import github.xzynine.superislandui.floating.smallisland.left.AComponent
import github.xzynine.superislandui.floating.smallisland.left.aContent
import github.xzynine.superislandui.floating.smallisland.left.aPicKey
import github.xzynine.superislandui.floating.smallisland.left.aTitle
import github.xzynine.superislandui.floating.smallisland.right.BComponent
import github.xzynine.superislandui.floating.smallisland.right.BSameWidthDigitInfo
import github.xzynine.superislandui.floating.smallisland.right.bContent
import github.xzynine.superislandui.floating.smallisland.right.bPicKey
import github.xzynine.superislandui.floating.smallisland.right.bProgress
import github.xzynine.superislandui.floating.smallisland.right.bProgressColorReach
import github.xzynine.superislandui.floating.smallisland.right.bProgressColorUnReach
import github.xzynine.superislandui.floating.smallisland.right.bProgressIsCCW
import github.xzynine.superislandui.floating.smallisland.right.bTitle
import github.xzynine.superislandui.floating.smallisland.right.isTimerType
import github.xzynine.superislandui.floating.smallisland.right.textToRender
import kotlinx.coroutines.CancellationException
import notifyrelay.base.util.Logger
import notifyrelay.base.util.image.ImageUtils

/**
 * 小图标注入与解析：封装反射写入 [Notification.mSmallIcon]、
 * 胶囊兼容通知构建及图标位图解析。
 *
 * 与 [ReplicaScrollUpdater] 共用 [ReplicaIconCache]，注入时缓存图标供滚动更新恢复。
 */
internal object ReplicaSmallIconInjector {
    private const val TAG = "超级岛通知生成"

    /**
     * 清除小图标（超级岛模式用）：
     * 系统需要小图标字段合法存在（不可为 null），因此将 mSmallIcon 替换为透明占位图标。
     * 目的：左岛小图标优先级高于图文组件时，避免默认图抢占左岛专辑图展示。
     */
    private fun clearSmallIcon(notification: Notification) {
        try {
            val transparentIcon =
                Icon.createWithBitmap(
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8).apply { eraseColor(android.graphics.Color.TRANSPARENT) },
                )
            val field = Notification::class.java.getDeclaredField("mSmallIcon")
            field.isAccessible = true
            field.set(notification, transparentIcon)
            Logger.i(TAG, "超级岛 已清除小图标（注入透明占位图）")
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 清除小图标失败: ${e.message}")
        }
    }

    /**
     * 注入小图标到通知，同时缓存供滚动更新复用
     */
    private fun injectSmallIcon(
        notification: Notification,
        bitmap: Bitmap?,
        cacheKey: String? = null,
    ) {
        bitmap?.let {
            try {
                val icon = Icon.createWithBitmap(it)
                val field = Notification::class.java.getDeclaredField("mSmallIcon")
                field.isAccessible = true
                field.set(notification, icon)
                // 同时缓存供滚动更新复用，避免反射读取
                if (cacheKey != null) {
                    ReplicaIconCache.put(cacheKey, icon)
                }
                Logger.i(TAG, "超级岛 成功注入小图标到胶囊通知")
            } catch (e: Exception) {
                Logger.w(TAG, "超级岛 注入小图标失败: ${e.message}")
            }
        }
    }

    /**
     * 超级岛模式：清除 setSmallIcon 设置的默认图标（替换为透明占位），避免默认图抢占左岛专辑图展示
     */
    fun clearSmallIconForSuperIsland(notification: Notification) {
        clearSmallIcon(notification)
    }

    /**
     * 注入小图标到通知并缓存（供滚动更新恢复）
     */
    fun injectSmallIconWithCache(
        notification: Notification,
        bitmap: Bitmap?,
        cacheKey: String?,
    ) {
        injectSmallIcon(notification, bitmap, cacheKey)
    }

    /**
     * 注入小图标到通知（不缓存，用于单向进度分支）
     */
    fun injectSmallIconWithoutCache(
        notification: Notification,
        bitmap: Bitmap?,
    ) {
        injectSmallIcon(notification, bitmap)
    }

    /**
     * 解析小图标位图，遵循优先级：progress -> text -> picMap aPicKey/bPicKey -> appIconKey -> null
     */
    private suspend fun resolveSmallIconBitmap(
        context: Context,
        picMap: Map<String, String>?,
        aComponent: AComponent?,
        bComponent: BComponent?,
    ): Bitmap? {
        // 提取 A/B 区图片键
        val aPicKey = aComponent.aPicKey
        val bPicKey = bComponent.bPicKey
        val bProgress = bComponent.bProgress
        val bProgressColorReach = bComponent.bProgressColorReach
        val bProgressColorUnReach = bComponent.bProgressColorUnReach
        val bProgressIsCCW = bComponent.bProgressIsCCW

        // 处理 smallIcon
        // 优先处理进度数据
        Logger.d(TAG, "超级岛 处理小图标位图 - bProgress: $bProgress")
        if (bProgress != null) {
            Logger.d(TAG, "超级岛 使用进度数据生成位图")
            val bitmap = BitmapUtils.progressToBitmap(bProgress, bProgressColorReach, bProgressColorUnReach, bProgressIsCCW)
            Logger.d(TAG, "超级岛 进度位图生成结果: ${bitmap != null}")
            if (bitmap != null) return bitmap
        }

        // 处理文本位图
        // 检查是否为计时器类型，如果是，不生成文本位图，保留之前的图标
        val isTimerType = bComponent.isTimerType
        if (!isTimerType) {
            // 优先使用 A 区（左侧）文本生成位图，然后才是 B 区（右侧）文本
            val aText = aComponent.aTitle ?: aComponent.aContent
            val textToRender =
                if (!aText.isNullOrBlank()) {
                    aText
                } else {
                    bComponent.textToRender
                }

            Logger.d(TAG, "超级岛 处理文本位图 - textToRender: $textToRender")
            if (!textToRender.isNullOrBlank()) {
                Logger.d(TAG, "超级岛 使用文本生成位图")
                val albumBitmap = loadAlbumBitmapOrNull(context, picMap, textToRender.length)
                val bitmap = BitmapUtils.textToBitmap(textToRender, albumBitmap = albumBitmap)
                Logger.d(TAG, "超级岛 文本位图生成结果: ${bitmap != null}")
                if (bitmap != null) return bitmap
            }
        } else {
            // 计时器类型，不生成文本位图，保留之前的图标
            Logger.d(TAG, "超级岛 计时器类型，保留之前的小图标，不生成文本位图")
        }

        // 处理图标
        // 优先使用 A 区图标或B区图标
        val picKeyToUse = aPicKey ?: bPicKey
        Logger.d(TAG, "超级岛 处理 A 区图标或B区图标 - picKeyToUse: $picKeyToUse, picMap: ${picMap?.keys}")
        if (!picKeyToUse.isNullOrBlank() && !picMap.isNullOrEmpty()) {
            val picUrl = picMap[picKeyToUse]
            if (!picUrl.isNullOrBlank()) {
                // 异步下载图标
                Logger.d(TAG, "超级岛 使用 A 区图标或B区图标作为小图标")
                val bitmap = downloadBitmap(context, picUrl)
                if (bitmap != null) {
                    Logger.d(TAG, "超级岛 A 区图标或B区图标加载成功")
                    return bitmap
                } else {
                    Logger.w(TAG, "超级岛 A 区图标或B区图标加载失败")
                }
            }
        }

        // 如果没有 A 区图标或B区图标，再使用应用图标（大图标的键值提供的图标）
        val appIconKey = "miui.focus.pic_app_icon"
        Logger.d(TAG, "超级岛 处理应用图标 - appIconKey: $appIconKey, picMap: ${picMap?.keys}")
        if (!picMap.isNullOrEmpty() && picMap.containsKey(appIconKey)) {
            val appIconUrl = picMap[appIconKey]
            if (!appIconUrl.isNullOrBlank()) {
                // 异步下载应用图标
                Logger.d(TAG, "超级岛 使用应用图标作为小图标")
                val bitmap = downloadBitmap(context, appIconUrl)
                if (bitmap != null) {
                    Logger.d(TAG, "超级岛 应用图标加载成功")
                    return bitmap
                } else {
                    Logger.w(TAG, "超级岛 应用图标加载失败")
                }
            }
        }

        // 如果没有生成位图，返回null
        Logger.d(TAG, "超级岛 没有生成小图标")
        return null
    }

    /**
     * 构建胶囊兼容的通知，添加标准通知字段和 smallIcon 注入
     */
    private suspend fun buildCapsuleCompatibleNotification(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String?,
        text: String?,
        appName: String?,
        picMap: Map<String, String>?,
        paramV2Raw: String?,
        aComponent: AComponent?,
        bComponent: BComponent?,
        isTimerType: Boolean,
    ): NotificationCompat.Builder {
        try {
            // 提取 A/B 区数据（使用已解析的组件）
            val aTitle = aComponent.aTitle
            val aContent = aComponent.aContent
            val aPicKey = aComponent.aPicKey
            val bTitle = bComponent.bTitle
            val bContent = bComponent.bContent
            val bPicKey = bComponent.bPicKey
            val bProgress = bComponent.bProgress
            val bProgressColorReach = bComponent.bProgressColorReach
            val bProgressColorUnReach = bComponent.bProgressColorUnReach
            val bProgressIsCCW = bComponent.bProgressIsCCW

            // 设置标准通知字段
            // 根据计时器状态设置标题和内容（isTimerType 由调用方按「进度优先」判定后传入）
            if (isTimerType && bComponent is BSameWidthDigitInfo && bComponent.timer != null) {
                val timer = bComponent.timer
                val timerTitle =
                    timer?.let {
                        when (it.timerType) {
                            -2 -> "暂停"
                            -1 -> "倒计时中"
                            1 -> "正计时中"
                            2 -> "暂停"
                            else -> title ?: appName ?: "超级岛通知"
                        }
                    }
                val timerContent = appName ?: "超级岛通知"

                builder
                    .setContentTitle(timerTitle)
                    .setContentText(timerContent)
                    .setSubText(appName ?: "超级岛通知")
                    .setShortCriticalText(timerTitle)
            } else {
                // 非计时器类型，使用原有逻辑
                val timerText =
                    when (bComponent) {
                        is BSameWidthDigitInfo -> {
                            null
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

            // 这里不要设 setRequestPromotedOngoing(true)：常驻提升态会让系统走 promoted(实况)渲染路径，
            // 不再渲染 param_island.bigIslandArea，点击/长按展开只能看到 smallIslandArea 的兜底数据。
            // （setOngoing 由上方基础 builder 保留，此处不必重复。）
            builder
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            // 确保右胶囊文本被正确设置到 miui.focus.param 字段
            // 使用 SuperIslandStructuredDataHelper 添加结构化数据
            val superIslandInjectionEnabled = SuperIslandConfigUtils.isSuperIslandSpecInjectionEnabled(context)
            SuperIslandStructuredDataHelper.addSuperIslandStructuredData(
                builder = builder,
                context = context,
                paramV2Raw = paramV2Raw,
                picMap = picMap,
                title = title,
                text = text,
                isSuperIslandSpecInjectionEnabled = superIslandInjectionEnabled,
            )

            // 「客户端实现」方式下系统不会下载 JSON 中 pic 字段引用的 URL，图片必须以 Parcelable Icon
            // 提供到 miui.focus.pics，否则大岛（展开态）里引用图片的组件（如进度组件1 的前进图形/
            // 中间节点/目标点）会因取不到资源而膨胀失败，表现为退化成单进度条或回退小岛数据。
            if (superIslandInjectionEnabled) {
                val injectedKeys =
                    SuperIslandStructuredDataHelper.injectPicMapIcons(
                        context = context,
                        extras = builder.extras,
                        picMap = picMap,
                    )
                // 仅对确实注入成功的图片，移除 writePicMap 写入的同名顶层 URL extra；
                // 注入失败/未使用的图片保留原 extra，维持兼容路径。
                SuperIslandStructuredDataHelper.removeSupersededPicUrlExtras(
                    extras = builder.extras,
                    injectedKeys = injectedKeys,
                )
            }

            // 处理 smallIcon - 设置系统默认图标作为占位符
            builder.setSmallIcon(R.drawable.stat_notify_more)
        } catch (e: CancellationException) {
            // 协程取消必须原样抛出：否则会被下面的 catch(Exception) 吞掉并继续走 notify 流程
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 构建胶囊兼容通知失败: ${e.message}")
        }

        return builder
    }

    /**
     * 构建胶囊兼容通知并注入图标，返回注入后的 [Notification]。
     */
    suspend fun buildCapsuleCompatibleNotificationWithIconInjection(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String?,
        text: String?,
        appName: String?,
        picMap: Map<String, String>?,
        paramV2Raw: String?,
        aComponent: AComponent?,
        bComponent: BComponent?,
        isTimerType: Boolean,
    ): Notification {
        try {
            // 先构建胶囊兼容的通知
            val capsuleBuilder =
                buildCapsuleCompatibleNotification(
                    context,
                    builder,
                    title,
                    text,
                    appName,
                    picMap,
                    paramV2Raw,
                    aComponent,
                    bComponent,
                    isTimerType,
                )

            // 构建通知并注入图标
            val notification = capsuleBuilder.build()

            // 解析小图标位图
            val smallIconBitmap = resolveSmallIconBitmap(context, picMap, aComponent, bComponent)

            // 如果没有生成位图，使用默认图标（改为本应用图标）
            if (smallIconBitmap == null) {
                Logger.d(TAG, "超级岛 没有生成位图，使用本应用图标作为默认图标")
            } else {
                Logger.d(TAG, "超级岛 成功生成小图标")
            }

            // 注入小图标
            injectSmallIcon(notification, smallIconBitmap)

            // 返回注入图标后的通知对象
            return notification
        } catch (e: CancellationException) {
            // 协程取消必须原样抛出，避免把取消当作构建失败而返回兜底通知
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 构建胶囊兼容通知并注入图标失败 ${e.message}")
            e.printStackTrace()
            // 发生异常时，返回原始构建器构建的通知
            return builder.build()
        }
    }

    /**
     * 下载位图
     */
    suspend fun downloadBitmap(
        context: Context,
        url: String,
    ): Bitmap? =
        try {
            ImageUtils.loadBitmap(context, url)
        } catch (e: Exception) {
            Logger.w(TAG, "超级岛 下载图片失败: ${e.message}")
            null
        }

    /**
     * 加载专辑图位图，仅在文本长度 <= 6 且coverUrl 存在时执行
     */
    suspend fun loadAlbumBitmapOrNull(
        context: Context,
        picMap: Map<String, String>?,
        textLength: Int,
    ): Bitmap? {
        if (textLength > 6) return null
        val coverKey = "miui.focus.pic_cover"
        if (picMap.isNullOrEmpty() || !picMap.containsKey(coverKey)) return null
        val coverUrl = picMap[coverKey] ?: return null
        if (coverUrl.isBlank()) return null
        return downloadBitmap(context, coverUrl)
    }
}
