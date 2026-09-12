package com.xzyht.notifyrelay.feature.notification.superisland.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import github.xzynine.superislandui.builder.SuperIslandExtras
import github.xzynine.superislandui.builder.SuperIslandImageSpec
import github.xzynine.superislandui.builder.SuperIslandParamBuilder
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import notifyrelay.base.util.Logger
import notifyrelay.core.util.image.ImageUtils
import org.json.JSONObject

/**
 * 超级岛结构化数据注入工具
 * 负责为通知添加符合小米官方规范的超级岛结构化数据。
 *
 * 说明：`miui.focus.param` 的 JSON 结构统一由 superislandui 的
 * [SuperIslandParamBuilder] 约束构建，避免各处手工拼装出层级错误/字段缺失的不合规结构。
 */
object SuperIslandStructuredDataHelper {
    private const val TAG = "SuperIslandStructuredDataHelper"

    // 媒体图片注入上限：缩放最长边并限制总字节数，避免通知事务过大
    private const val MAX_MEDIA_PIC_DIMENSION = 512
    private const val MAX_MEDIA_PIC_PER_IMAGE_BYTES = 256 * 1024
    private const val MAX_MEDIA_PIC_TOTAL_BYTES = 768 * 1024

    // 超级岛封面图资源 key
    private const val PIC_COVER = "miui.focus.pic_cover"

    /**
     * 添加超级岛相关的结构化数据到通知
     * @param builder 通知构建器
     * @param context 上下文
     * @param paramV2Raw ParamV2原始JSON字符串
     * @param picMap 图片映射
     * @param title 通知标题
     * @param text 通知内容
     * @param isSuperIslandSpecInjectionEnabled 是否开启超级岛规范信息注入
     */
    fun addSuperIslandStructuredData(
        builder: NotificationCompat.Builder,
        context: Context,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        title: String?,
        text: String?,
        isSuperIslandSpecInjectionEnabled: Boolean = true,
    ) {
        try {
            val extras = builder.extras

            if (isSuperIslandSpecInjectionEnabled) {
                paramV2Raw?.let { rawData ->
                    try {
                        // 由约束构建器补默认值并包裹 param_v2（规范结构）
                        // 复刻：business 与 param_island 为编译期必传项
                        val payload =
                            SuperIslandParamBuilder
                                .replica(rawData, SuperIslandParamBuilder.businessOf(rawData))
                                .island(SuperIslandParamBuilder.paramIslandOf(rawData))
                                .tickerIfBlank(title)
                                .aodTitleIfBlank(title)
                                .build()

                        val issues = SuperIslandParamBuilder.validate(payload)
                        if (issues.isNotEmpty()) {
                            Logger.w(TAG, "超级岛 param_v2 合规校验提示: $issues")
                        }

                        extras.putString(SuperIslandExtras.KEY_PARAM, payload)
                        Logger.i(TAG, "添加miui.focus.param成功")
                    } catch (e: Exception) {
                        extras.putString(SuperIslandExtras.KEY_PARAM, rawData)
                        Logger.w(TAG, "构建完整焦点通知参数结构失败，回退到原始数据 ${e.message}")
                    }
                }

                SuperIslandExtras.writePicMap(extras, picMap)
                logImageIssues(picMap)
                addActionBundlesToExtras(extras)

                extras.putBoolean(SuperIslandExtras.KEY_ISLAND_UPDATE_NO_FLOAT, false)
                extras.putBoolean(SuperIslandExtras.KEY_ISLAND_FIRST_FLOAT, false)
                extras.putBoolean(SuperIslandExtras.KEY_ENABLE_FLOAT, false)

                val titleValue = title ?: ""
                if (titleValue.contains("计时") || titleValue.contains("秒表")) {
                    extras.putBoolean("android.chronometerCountDown", false)
                    extras.putBoolean("android.showChronometer", true)
                }

                SuperIslandExtras.writeStandardFlags(extras, context.packageName)

                Logger.i(TAG, "添加超级岛结构化数据成功")
            }
        } catch (e: Exception) {
            Logger.w(TAG, "添加超级岛结构化数据失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 为媒体类型通知添加超级岛结构化数据
     * @param builder 通知构建器
     * @param context 上下文
     * @param title 通知标题（用于展开态 baseInfo）
     * @param text 通知内容（用于展开态 baseInfo）
     * @param picMap 图片映射
     * @param iconText 左侧文本（分割后的歌词左半部分，用于收起态 imageTextInfoLeft）
     * @param capsuleText 右侧文本（分割后的歌词右半部分，用于收起态 textInfo）
     */
    suspend fun addMediaSuperIslandStructuredData(
        builder: NotificationCompat.Builder,
        context: Context,
        title: String?,
        text: String?,
        picMap: Map<String, String>?,
        iconText: String? = null,
        capsuleText: String? = null,
    ) {
        try {
            val extras = builder.extras

            // 按小米超级岛模板库「a图文组件1 + b文本组件」构建
            val bigIslandArea =
                JSONObject().apply {
                    put(
                        "imageTextInfoLeft",
                        JSONObject().apply {
                            put("type", 1)
                            put(
                                "picInfo",
                                JSONObject().apply {
                                    put("type", 1)
                                    put("pic", PIC_COVER)
                                },
                            )
                            // 短文本（iconText 为空）时左侧为纯专辑图，不放文字
                            if (!iconText.isNullOrEmpty()) {
                                put(
                                    "textInfo",
                                    JSONObject().apply {
                                        put("title", iconText)
                                        put("content", "")
                                        put("narrowFont", false)
                                        put("showHighlightColor", true)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "textInfo",
                        JSONObject().apply {
                            put("frontTitle", "")
                            put("title", capsuleText ?: "")
                            put("content", "")
                            put("narrowFont", false)
                            put("showHighlightColor", true)
                        },
                    )
                }
            val smallIslandArea =
                JSONObject().apply {
                    put(
                        "picInfo",
                        JSONObject().apply {
                            put("type", 1)
                            put("pic", PIC_COVER)
                        },
                    )
                }

            // 由约束构建器生成合规 param_v2（含 V3 序列化标识）
            val payload =
                SuperIslandParamBuilder
                    .business("music")
                    .island {
                        islandProperty(1)
                        islandOrder(false)
                        highlightColor("#FFFFFF")
                        bigIslandArea(bigIslandArea)
                        smallIslandArea(smallIslandArea)
                    }
                    .focusType(SuperIslandParamBuilder.FOCUS_V3_TYPE)
                    .ticker(title ?: "")
                    .aodTitle(title ?: "")
                    .component(
                        "baseInfo",
                        JSONObject().apply {
                            put("type", 2)
                            put("title", title ?: "")
                            put("content", text ?: "")
                        },
                    )
                    .build()

            extras.putString(SuperIslandExtras.KEY_PARAM, payload)

            addActionBundlesToExtras(extras)
            addMediaPicMapToExtras(context, extras, picMap)

            SuperIslandExtras.writeStandardFlags(extras, context.packageName)

            Logger.i(TAG, "添加媒体类型超级岛结构化数据成功")
        } catch (e: CancellationException) {
            // 协程取消必须原样抛出，避免被当作普通异常吞掉
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "添加媒体类型超级岛结构化数据失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 为非媒体类型通知添加超级岛结构化数据（支持右胶囊文本更新）
     * @param builder 通知构建器
     * @param context 上下文
     * @param paramV2Raw ParamV2原始JSON字符串
     * @param picMap 图片映射
     * @param title 通知标题
     * @param text 通知内容
     * @param bTitle 右胶囊标题
     * @param bContent 右胶囊内容
     */
    fun addNonMediaSuperIslandStructuredData(
        builder: NotificationCompat.Builder,
        context: Context,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        title: String?,
        text: String?,
        bTitle: String? = null,
        bContent: String? = null,
    ) {
        try {
            val extras = builder.extras

            paramV2Raw?.let { raw ->
                try {
                    val island = SuperIslandParamBuilder.paramIslandOf(raw)

                    // 右胶囊文本写入规范路径 param_island.bigIslandArea.imageTextInfoRight
                    if (bTitle != null || bContent != null) {
                        val bigIslandArea =
                            island.optJSONObject("bigIslandArea")
                                ?: JSONObject().also { island.put("bigIslandArea", it) }
                        val right =
                            bigIslandArea.optJSONObject("imageTextInfoRight")
                                ?: JSONObject().also { bigIslandArea.put("imageTextInfoRight", it) }
                        if (bTitle != null) right.put("title", bTitle)
                        if (bContent != null) right.put("content", bContent)
                    }

                    // 复刻：business 与 param_island 为编译期必传项
                    val payload =
                        SuperIslandParamBuilder
                            .replica(raw, SuperIslandParamBuilder.businessOf(raw))
                            .island(island)
                            .tickerIfBlank(title)
                            .build()
                    val issues = SuperIslandParamBuilder.validate(payload)
                    if (issues.isNotEmpty()) {
                        Logger.w(TAG, "超级岛 param_v2 合规校验提示: $issues")
                    }
                    extras.putString(SuperIslandExtras.KEY_PARAM, payload)
                } catch (e: Exception) {
                    // 构建失败时回退到原始数据
                    extras.putString(SuperIslandExtras.KEY_PARAM, raw)
                }
            }

            SuperIslandExtras.writePicMap(extras, picMap)
            logImageIssues(picMap)
            addActionBundlesToExtras(extras)

            // 应用/源包信息等标准焦点标记
            SuperIslandExtras.writeStandardFlags(extras, context.packageName)

            Logger.i(TAG, "添加非媒体类型超级岛结构化数据成功")
        } catch (e: Exception) {
            Logger.w(TAG, "添加非媒体类型超级岛结构化数据失败 ${e.message}")
            e.printStackTrace()
        }
    }

    /** 校验 picMap 是否满足官方图片约束（数量/链接），不通过仅记录日志，不阻断发送 */
    private fun logImageIssues(picMap: Map<String, String>?) {
        val issues = SuperIslandImageSpec.validatePicMap(picMap)
        if (issues.isNotEmpty()) {
            Logger.w(TAG, "超级岛图片约束提示: $issues")
        }
    }

    /**
     * 媒体类型专用：下载图片为 Bitmap 并转为 Icon 放入 miui.focus.pics（客户端模式要求 Parcelable Icon）
     */
    private suspend fun addMediaPicMapToExtras(
        context: Context,
        extras: Bundle,
        picMap: Map<String, String>?,
    ) {
        picMap?.let { map ->
            val picsBundle = Bundle()
            var count = 0
            var totalBytes = 0
            map.forEach { (picKey, picUrl) ->
                if (!picKey.startsWith("miui.focus.pic_") || picUrl.isBlank()) return@forEach
                if (totalBytes >= MAX_MEDIA_PIC_TOTAL_BYTES) {
                    Logger.w(TAG, "媒体图片总大小已达上限，跳过后续图片: $picKey")
                    return@forEach
                }
                val bitmap =
                    try {
                        ImageUtils.loadBitmap(context, picUrl)
                    } catch (e: CancellationException) {
                        // 协程取消必须原样抛出，避免被当作普通异常吞掉
                        throw e
                    } catch (e: Exception) {
                        Logger.w(TAG, "媒体图片加载失败 ${picKey}: ${e.message}")
                        null
                    }
                if (bitmap != null) {
                    // 按比例缩放到上限尺寸并压缩，限制单张与总体大小，避免通知事务过大
                    val data = encodePicData(scaleDownBitmap(bitmap, MAX_MEDIA_PIC_DIMENSION))
                    if (totalBytes + data.size > MAX_MEDIA_PIC_TOTAL_BYTES) {
                        Logger.w(TAG, "媒体图片超过总大小限制，跳过: $picKey (${data.size} bytes)")
                        return@forEach
                    }
                    picsBundle.putParcelable(picKey, Icon.createWithData(data, 0, data.size))
                    totalBytes += data.size
                    count++
                }
            }
            if (count > 0) {
                extras.putBundle("miui.focus.pics", picsBundle)
                Logger.i(TAG, "媒体图片资源注入成功，共 $count 个图片（总计 $totalBytes bytes）")
            }
        }
    }

    /**
     * 按比例缩小位图，使最长边不超过 [maxDimension]；已在范围内则原样返回。
     */
    private fun scaleDownBitmap(
        bitmap: Bitmap,
        maxDimension: Int,
    ): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension || longest <= 0) return bitmap
        val ratio = maxDimension.toFloat() / longest
        val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * 将位图编码为图标数据：优先 PNG（保留透明度），过大时改用 JPEG 压缩以减小体积。
     */
    private fun encodePicData(bitmap: Bitmap): ByteArray {
        val pngOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
        val png = pngOut.toByteArray()
        if (png.size <= MAX_MEDIA_PIC_PER_IMAGE_BYTES) return png
        val jpegOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpegOut)
        return jpegOut.toByteArray()
    }

    private fun addActionBundlesToExtras(extras: Bundle) {
        extras.putBoolean("miui.showAction", true)
        val actionsBundle = Bundle()
        actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
        actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
        extras.putBundle("miui.focus.actions", actionsBundle)
    }
}
