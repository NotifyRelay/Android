package com.xzyht.notifyrelay.feature.notification.superisland.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import github.xzynine.superislandui.builder.SuperIslandExtras
import github.xzynine.superislandui.builder.SuperIslandImageSpec
import github.xzynine.superislandui.builder.SuperIslandParamBuilder
import kotlinx.coroutines.CancellationException
import notifyrelay.base.util.Logger
import notifyrelay.base.util.image.ImageUtils
import org.json.JSONObject
import java.io.ByteArrayOutputStream

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

    // 复刻（非媒体）图片上限：图片以 base64 data URI 写入，体积会再膨胀约 1/3，
    // 而系统通知 extras 的 Binder 事务有上限（实测 527KB 会抛 TransactionTooLargeException 导致通知投递失败），
    // 因此这里用更小的尺寸与总预算。
    private const val MAX_REPLICA_PIC_DIMENSION = 160
    private const val MAX_REPLICA_PIC_PER_IMAGE_BYTES = 48 * 1024
    private const val MAX_REPLICA_PIC_TOTAL_BYTES = 224 * 1024

    // 超级岛封面图资源 key
    private const val PIC_COVER = "miui.focus.pic_cover"

    // PNG 超限时的 JPEG 兜底压缩质量档位（从高到低逐级试探，直到满足单张上限）
    private val JPEG_FALLBACK_QUALITIES = intArrayOf(85, 70, 55, 40)

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
                        // 复刻类型：直接透传来源设备的原始超级岛数据，不做二次拼装。
                        // 复刻数据来自另一台设备已发出的 miui.focus.param（或裸 param_v2），
                        // 重新构建（补默认值 / 重写 param_island / 追加 ticker 等）会引入与来源不一致的差异，
                        // 而系统按字段渲染大岛，任何差异都可能让大岛膨胀失败并回退小岛兜底数据。
                        val payload = buildReplicaPayload(rawData)

                        val issues = SuperIslandParamBuilder.validate(payload)
                        if (issues.isNotEmpty()) {
                            Logger.w(TAG, "超级岛 param_v2 合规校验提示: $issues")
                        }

                        SuperIslandExtras.writeParam(extras, payload)
                        Logger.i(TAG, "添加miui.focus.param成功（原样透传复刻数据）")
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
     * 复刻载荷构造：**原样透传**来源数据，仅做必要的「包一层」。
     *
     * - 已是完整 `miui.focus.param`（含 `param_v2`）→ 原样返回，保留外层字段；
     * - 裸 `param_v2` → 仅包一层 `{"param_v2": 原数据}`，不补默认值、不重写任何组件。
     *
     * 依据官方《超级岛开发指南》：`miui.focus.param` 顶层**只有 `param_v2`**（文档未定义顶层 `type`），
     * 因此这里不额外注入任何模板工厂标识，避免与来源设备载荷产生差异。
     *
     * 解析失败时抛出，由调用方回退为原始字符串。
     */
    private fun buildReplicaPayload(raw: String): String {
        val json = JSONObject(raw)
        val inner = json.optJSONObject("param_v2")
        return if (inner != null) raw else JSONObject().apply { put("param_v2", json) }.toString()
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
                    }.focusType(SuperIslandParamBuilder.FOCUS_V3_TYPE)
                    .ticker(title ?: "")
                    .aodTitle(title ?: "")
                    .component(
                        "baseInfo",
                        JSONObject().apply {
                            put("type", 2)
                            put("title", title ?: "")
                            put("content", text ?: "")
                        },
                    ).build()

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
     * 清理已被 Icon 取代的顶层 `miui.focus.pic_*` URL extra。
     *
     * `writePicMap` 会为每张图片同时写入顶层 URL extra 与 `miui.focus.pics` Bundle。
     * 当同一 key 已成功注入 Parcelable Icon 后，顶层 URL 就成了重复数据（同一张图在 extras
     * 里存两份，曾把体积顶到 527KB 触发 TransactionTooLargeException）。
     *
     * **仅移除确实注入成功的 key**：注入失败、未执行注入或未被 Icon 使用的图片必须保留
     * 原有顶层 extra，以维持兼容路径（系统可直接读取 URL 的场景）。
     *
     * @param injectedKeys [injectPicMapIcons] 返回的、已成功写入 Icon 的图片 key 集合
     * @return 实际移除的 extra 数量
     */
    fun removeSupersededPicUrlExtras(
        extras: Bundle,
        injectedKeys: Set<String>,
    ): Int {
        if (injectedKeys.isEmpty()) return 0
        var removed = 0
        injectedKeys.forEach { key ->
            if (!key.startsWith(SuperIslandExtras.PIC_KEY_PREFIX)) return@forEach
            if (extras.containsKey(key)) {
                extras.remove(key)
                removed++
            }
        }
        if (removed > 0) {
            Logger.i(TAG, "已移除被 Icon 取代的顶层图片 URL extra，共 $removed 个")
        }
        return removed
    }

    /**
     * 媒体类型专用：下载图片为 Bitmap 并转为 Icon 放入 miui.focus.pics（客户端模式要求 Parcelable Icon）
     */
    private suspend fun addMediaPicMapToExtras(
        context: Context,
        extras: Bundle,
        picMap: Map<String, String>?,
    ) {
        val result =
            loadPicBitmaps(
                context,
                picMap,
                MAX_MEDIA_PIC_DIMENSION,
                MAX_MEDIA_PIC_PER_IMAGE_BYTES,
                MAX_MEDIA_PIC_TOTAL_BYTES,
            )
        if (result.icons.isEmpty()) return
        val picsBundle = Bundle()
        result.icons.forEach { (key, data) ->
            picsBundle.putParcelable(key, Icon.createWithData(data, 0, data.size))
        }
        extras.putBundle(SuperIslandExtras.KEY_PICS, picsBundle)
        Logger.i(TAG, "媒体图片资源注入成功，共 ${result.icons.size} 个图片（总计 ${result.totalBytes} bytes）")
    }

    /**
     * 非媒体类型（复刻）：把图片下载/解码后以 [Icon] 写入 `miui.focus.pics` Bundle。
     *
     * 背景：复刻数据的图片来源可能是另一台设备（MIPUSH 场景，图片为 https URL）；本端是「客户端实现」，
     * 系统不会下载 URL，必须由应用把图片作为 Icon 资源提供，Json 里的 pic 只作为 pics Bundle 的 key。
     *
     * 这里**只写 Bundle**，不再同时写单个 `miui.focus.pic_*` extra —— 双写会让同一份图片在 extras 里
     * 存两份，曾把通知体积顶到 527KB 触发 TransactionTooLargeException（通知投递失败、不显示）。
     * 由调用方依据返回值清理 `writePicMap` 已写入的同名顶层 URL extra。
     *
     * @return 实际写入 Icon 的图片 key 集合（空集合表示未注入任何图片）
     */
    suspend fun injectPicMapIcons(
        context: Context,
        extras: Bundle,
        picMap: Map<String, String>?,
    ): Set<String> {
        val result =
            loadPicBitmaps(
                context,
                picMap,
                MAX_REPLICA_PIC_DIMENSION,
                MAX_REPLICA_PIC_PER_IMAGE_BYTES,
                MAX_REPLICA_PIC_TOTAL_BYTES,
            )
        if (result.icons.isEmpty()) return emptySet()
        // 依据《小米澎湃OS 岛通知开发指南》附录「图片数据参数：miui.focus.pics」：
        // 客户端实现把图片以 Icon 放进 miui.focus.pics Bundle，Json 里的 pic 只作为该 Bundle 的 key。
        val picsBundle = Bundle()
        result.icons.forEach { (key, data) ->
            picsBundle.putParcelable(key, Icon.createWithData(data, 0, data.size))
        }
        extras.putBundle(SuperIslandExtras.KEY_PICS, picsBundle)
        Logger.i(TAG, "超级岛图片资源注入成功（Icon，仅写 pics Bundle），共 ${result.icons.size} 个图片（总计 ${result.totalBytes} bytes）")
        return result.icons.keys
    }

    /** 图片加载结果：key → 已编码的图标数据（及编码后总字节数，用于上限统计） */
    private class PicBitmapResult(
        val icons: Map<String, ByteArray>,
        val totalBytes: Int,
    )

    /**
     * 下载/解码并缩放图片：仅处理规范的 `miui.focus.pic_` 前缀与合法链接，
     * 受数量（[SuperIslandImageSpec.MAX_IMAGE_COUNT]）、单张尺寸与总大小上限约束。
     */
    private suspend fun loadPicBitmaps(
        context: Context,
        picMap: Map<String, String>?,
        maxDimension: Int,
        maxPerImageBytes: Int,
        maxTotalBytes: Int,
    ): PicBitmapResult {
        if (picMap.isNullOrEmpty()) return PicBitmapResult(emptyMap(), 0)
        val icons = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0
        picMap.forEach { (picKey, picUrl) ->
            if (icons.size >= SuperIslandImageSpec.MAX_IMAGE_COUNT) return@forEach
            if (!picKey.startsWith(SuperIslandExtras.PIC_KEY_PREFIX)) return@forEach
            if (!SuperIslandImageSpec.isPicEntryValid(picUrl)) return@forEach
            if (totalBytes >= maxTotalBytes) {
                Logger.w(TAG, "图片总大小已达上限，跳过后续图片: $picKey")
                return@forEach
            }
            val bitmap =
                try {
                    ImageUtils.loadBitmap(context, picUrl)
                } catch (e: CancellationException) {
                    // 协程取消必须原样抛出，避免被当作普通异常吞掉
                    throw e
                } catch (e: Exception) {
                    Logger.w(TAG, "图片加载失败 $picKey: ${e.message}")
                    null
                }
            if (bitmap != null) {
                // 按比例缩放到上限尺寸
                val scaled = ImageUtils.scaleDown(bitmap, maxDimension)
                // 单张必须真正落在上限内：encodePicData 内部逐级降质重压，
                // 仍无法满足上限时返回 null，此时拒绝该图片（不能只做总量检查）。
                val data =
                    encodePicData(scaled, maxPerImageBytes)
                        ?: run {
                            Logger.w(TAG, "图片超过单张大小限制($maxPerImageBytes bytes)，跳过: $picKey")
                            return@forEach
                        }
                if (totalBytes + data.size > maxTotalBytes) {
                    Logger.w(TAG, "图片超过总大小限制，跳过: $picKey (${data.size} bytes)")
                    return@forEach
                }
                icons[picKey] = data
                totalBytes += data.size
            }
        }
        return PicBitmapResult(icons, totalBytes)
    }

    /**
     * 将位图编码为图标数据，并**保证结果不超过 [maxBytes]**：
     * 优先 PNG 保留透明度；PNG 超限时改用 JPEG，并从高到低逐级降质重压；
     * 仍无法满足上限时返回 null，由调用方拒绝该图片（避免超限图片进入通知 extras）。
     *
     * @return 编码后的字节数组；无法压到 [maxBytes] 以内时返回 null
     */
    private fun encodePicData(
        bitmap: Bitmap,
        maxBytes: Int = MAX_MEDIA_PIC_PER_IMAGE_BYTES,
    ): ByteArray? {
        val pngOut = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
        val png = pngOut.toByteArray()
        if (png.size <= maxBytes) return png
        // PNG 过大时改用 JPEG（体积小得多；会丢透明通道，仅作体积兜底），逐级降质直到满足上限
        for (quality in JPEG_FALLBACK_QUALITIES) {
            val jpegOut = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpegOut)
            val jpeg = jpegOut.toByteArray()
            if (jpeg.size <= maxBytes) return jpeg
        }
        return null
    }

    private fun addActionBundlesToExtras(extras: Bundle) {
        extras.putBoolean("miui.showAction", true)
        val actionsBundle = Bundle()
        actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
        actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
        extras.putBundle("miui.focus.actions", actionsBundle)
    }
}
