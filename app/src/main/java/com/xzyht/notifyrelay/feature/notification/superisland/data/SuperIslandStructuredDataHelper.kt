package com.xzyht.notifyrelay.feature.notification.superisland.data

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import notifyrelay.base.util.Logger
import notifyrelay.core.util.image.ImageUtils
import org.json.JSONObject

/**
 * 超级岛结构化数据注入工具�?
 * 负责为通知添加符合小米官方规范的超级岛结构化数�?
 */
object SuperIslandStructuredDataHelper {
    private const val TAG = "SuperIslandStructuredDataHelper"

    /**
     * 添加超级岛相关的结构化数据到通知
     * @param builder 通知构建�?
     * @param context 上下�?
     * @param paramV2Raw ParamV2原始JSON字符�?
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
                        val paramV2Json = JSONObject(rawData)
                        val tickerValue = title ?: paramV2Json.optString("ticker", "")

                        // 在原始 param_v2 基础上补充缺失字段
                        if (!paramV2Json.has("protocol")) paramV2Json.put("protocol", 1)
                        if (!paramV2Json.has("ticker") || paramV2Json.optString("ticker").isBlank()) {
                            paramV2Json.put("ticker", tickerValue)
                        }
                        if (!paramV2Json.has("aodTitle") || paramV2Json.optString("aodTitle").isBlank()) {
                            paramV2Json.put("aodTitle", tickerValue)
                        }
                        if (!paramV2Json.has("updatable")) paramV2Json.put("updatable", true)
                        if (!paramV2Json.has("reopen")) paramV2Json.put("reopen", "close")
                        if (!paramV2Json.has("enableFloat")) paramV2Json.put("enableFloat", false)
                        if (!paramV2Json.has("islandFirstFloat")) paramV2Json.put("islandFirstFloat", false)

                        // 顶层包装：type(可选) + param_v2
                        val fullFocusParam = JSONObject().apply {
                            put("param_v2", paramV2Json)
                        }

                        extras.putString("miui.focus.param", fullFocusParam.toString())
                        Logger.i(TAG, "添加miui.focus.param成功")
                    } catch (e: Exception) {
                        extras.putString("miui.focus.param", rawData)
                        Logger.w(TAG, "构建完整焦点通知参数结构失败，回退到原始数据 ${e.message}")
                    }
                }

                addPicMapToExtras(extras, picMap)
                addActionBundlesToExtras(extras)

                extras.putBoolean("miui.island.updateNoFloat", false)
                extras.putBoolean("miui.island.firstFloat", false)
                extras.putBoolean("miui.enableFloat", false)

                val titleValue = title ?: ""
                if (titleValue.contains("计时") || titleValue.contains("秒表")) {
                    extras.putBoolean("android.chronometerCountDown", false)
                    extras.putBoolean("android.showChronometer", true)
                }

                extras.putBoolean("android.reduced.images", true)
                extras.putString("superIslandSourcePackage", context.packageName)
                extras.putString("app_package", context.packageName)
                extras.putBoolean("miui.isFocusNotification", true)
                extras.putBoolean("miui.showBadge", false)

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
     * @param title 通知标题（用于展开态 animTextInfo）
     * @param text 通知内容（用于展开态 animTextInfo）
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

            // 按照小米超级岛模板库"序号二：a图文组件1 + b文本组件"构建
            val fullFocusParam = JSONObject().apply {
                put("param_v2", JSONObject().apply {
                    put("protocol", 1)
                    put("business", "music")
                    put("ticker", title ?: "")
                    put("aodTitle", title ?: "")
                    put("updatable", true)
                    put("reopen", "close")
                    put("enableFloat", false)
                    put("islandFirstFloat", false)

                    // 焦点通知数据（展开态生效）
                    put("baseInfo", JSONObject().apply {
                        put("type", 2)
                        put("title", title ?: "")
                        put("content", text ?: "")
                    })

                    // 岛数据
                    put("param_island", JSONObject().apply {
                        put("islandProperty", 1)
                        put("islandOrder", false)
                        put("highlightColor", "#FFFFFF")
                        // 大岛：a图文组件1（图+歌词左） + b文本组件（歌词右）
                        put("bigIslandArea", JSONObject().apply {
                            put("imageTextInfoLeft", JSONObject().apply {
                                put("type", 1)
                                put("picInfo", JSONObject().apply {
                                    put("type", 1)
                                    put("pic", "miui.focus.pic_cover")
                                })
                                put("textInfo", JSONObject().apply {
                                    put("title", iconText ?: "")
                                    put("content", "")
                                    put("narrowFont", false)
                                    put("showHighlightColor", true)
                                })
                            })
                            put("textInfo", JSONObject().apply {
                                put("frontTitle", "")
                                put("title", capsuleText ?: "")
                                put("content", "")
                                put("narrowFont", false)
                                put("showHighlightColor", true)
                            })
                        })
                        // 小岛
                        put("smallIslandArea", JSONObject().apply {
                            put("picInfo", JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_cover")
                            })
                        })
                    })
                })
            }

            extras.putString("miui.focus.param", fullFocusParam.toString())

            addActionBundlesToExtras(extras)
            addMediaPicMapToExtras(context, extras, picMap)

            extras.putBoolean("android.reduced.images", true)
            extras.putString("superIslandSourcePackage", context.packageName)
            extras.putString("app_package", context.packageName)
            extras.putBoolean("miui.isFocusNotification", true)
            extras.putBoolean("miui.showBadge", false)

            Logger.i(TAG, "添加媒体类型超级岛结构化数据成功")
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

            // 构建符合小米官方规范的完整miui.focus.param结构
            paramV2Raw?.let {
                try {
                    // 解析原始paramV2数据
                    val paramV2Json = JSONObject(it)

                    // 如果提供了右胶囊文本，更新imageTextInfoRight
                    if (bTitle != null || bContent != null) {
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
                    }

                    // 构建完整的焦点通知参数结构，包含外层scene、ticker等字段
                    val fullFocusParam =
                        JSONObject().apply {
                            put("protocol", 1)
                            put("scene", paramV2Json.optString("business", "default"))
                            put("ticker", title ?: "")
                            put("content", text ?: "")
                            put("timerType", 0)
                            put("timerWhen", 0)
                            put("timerSystemCurrent", 0)
                            put("enableFloat", false)
                            put("updatable", true)
                            put("param_v2", paramV2Json) // 将更新后的paramV2作为嵌套字段
                        }

                    extras.putString("miui.focus.param", fullFocusParam.toString())
                } catch (e: Exception) {
                    // 如果构建完整结构失败，回退到直接使用原始数据
                    extras.putString("miui.focus.param", it)
                }
            }

            addPicMapToExtras(extras, picMap)
            addActionBundlesToExtras(extras)

            // 添加应用信息，与原始通知保持一致
            extras.putBoolean("android.reduced.images", true)

            // 添加超级岛源包信息，与原始通知保持一致
            extras.putString("superIslandSourcePackage", context.packageName)

            // 包名信息
            extras.putString("app_package", context.packageName)

            Logger.i(TAG, "添加非媒体类型超级岛结构化数据成功")
        } catch (e: Exception) {
            Logger.w(TAG, "添加非媒体类型超级岛结构化数据失败 ${e.message}")
            e.printStackTrace()
        }
    }

    private fun addPicMapToExtras(
        extras: Bundle,
        picMap: Map<String, String>?,
    ) {
        picMap?.let { map ->
            map.forEach { (picKey, picUrl) ->
                if (picKey.startsWith("miui.focus.pic_")) {
                    extras.putString(picKey, picUrl)
                }
            }
            val picsBundle = Bundle()
            map.forEach { (picKey, picUrl) ->
                if (picKey.startsWith("miui.focus.pic_")) {
                    picsBundle.putString(picKey, picUrl)
                }
            }
            extras.putBundle("miui.focus.pics", picsBundle)
            Logger.i(TAG, "添加图片资源成功，共${map.size}个图片")
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
            map.forEach { (picKey, picUrl) ->
                if (!picKey.startsWith("miui.focus.pic_") || picUrl.isBlank()) return@forEach
                val bitmap = try {
                    ImageUtils.loadBitmap(context, picUrl)
                } catch (e: Exception) {
                    Logger.w(TAG, "媒体图片加载失败 ${picKey}: ${e.message}")
                    null
                }
                if (bitmap != null) {
                    picsBundle.putParcelable(picKey, Icon.createWithBitmap(bitmap))
                    count++
                }
            }
            if (count > 0) {
                extras.putBundle("miui.focus.pics", picsBundle)
                Logger.i(TAG, "媒体图片资源注入成功，共 $count 个图片")
            }
        }
    }

    private fun addActionBundlesToExtras(extras: Bundle) {
        extras.putBoolean("miui.showAction", true)
        val actionsBundle = Bundle()
        actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
        actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
        extras.putBundle("miui.focus.actions", actionsBundle)
    }
}
