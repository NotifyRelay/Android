package com.xzyht.notifyrelay.feature.notification.superisland.lifecyle

import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import notifyrelay.base.util.Logger
import org.json.JSONObject

/**
 * 超级岛结构化数据注入工具类
 * 负责为通知添加符合小米官方规范的超级岛结构化数据
 */
object SuperIslandStructuredDataHelper {
    private const val TAG = "SuperIslandStructuredDataHelper"

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
        isSuperIslandSpecInjectionEnabled: Boolean = true
    ) {
        try {
            // 获取通知的extras，用于添加结构化数据
            val extras = builder.extras

            // 检查超级岛规范信息注入是否开启
            if (isSuperIslandSpecInjectionEnabled) {
                // 构建符合小米官方规范的完整miui.focus.param结构
                paramV2Raw?.let { rawData ->
                    try {
                        // 解析原始paramV2数据
                        val paramV2Json = JSONObject(rawData)

                        // 构建完整的焦点通知参数结构，包含外层scene、ticker等字段
                        // 从paramV2Json中直接获取baseInfo，确保与FloatingReplicaManager一致
                        val baseInfoJson = paramV2Json.optJSONObject("baseInfo")
                        val tickerValue = baseInfoJson?.optString("title", "") ?: ""
                        val contentValue = baseInfoJson?.optString("content", "") ?: ""

                        val fullFocusParam = JSONObject().apply {
                            put("protocol", 1)
                            put("scene", paramV2Json.optString("business", "default"))
                            put("ticker", tickerValue)
                            put("content", contentValue)
                            put("timerType", 0)
                            put("timerWhen", 0)
                            put("timerSystemCurrent", 0)
                            put("enableFloat", false)
                            put("updatable", true)
                            put("reopen", paramV2Json.optString("reopen", "close"))
                            put("timeout", paramV2Json.optInt("timeout", 720))
                            put("filterWhenNoPermission", paramV2Json.optBoolean("filterWhenNoPermission", false))
                            put("islandFirstFloat", paramV2Json.optBoolean("islandFirstFloat", false))
                            put("param_v2", paramV2Json) // 将原始paramV2作为嵌套字段
                        }

                        extras.putString("miui.focus.param", fullFocusParam.toString())
                        Logger.i(TAG, "添加miui.focus.param成功")
                    } catch (e: Exception) {
                        // 如果构建完整结构失败，回退到直接使用原始数据
                        extras.putString("miui.focus.param", rawData)
                        Logger.w(TAG, "构建完整焦点通知参数结构失败，回退到原始数据: ${e.message}")
                    }
                }

                // 按照小米官方文档规范，将每个图片资源作为单独的extra添加
                picMap?.let { map ->
                    map.forEach { (picKey, picUrl) ->
                        // 确保key以"miui.focus.pic_"前缀开头，符合小米规范
                        if (picKey.startsWith("miui.focus.pic_")) {
                                // 解析图片绑定ID，获取实际的图片数据
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
                    Logger.i(TAG, "添加图片资源成功，共${map.size}个图片")
                }

                // 添加焦点通知必要的额外字段
                extras.putBoolean("miui.showAction", true)

                // 添加模拟的action字段
                val actionsBundle = Bundle()
                actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
                actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
                extras.putBundle("miui.focus.actions", actionsBundle)

                // 添加原始通知中存在的其他字段，这些可能影响UI显示
                // 对于计时器类通知，添加计时器相关字段
                val titleValue = title ?: ""
                if (titleValue.contains("计时") || titleValue.contains("秒表")) {
                    extras.putBoolean("android.chronometerCountDown", false)
                    extras.putBoolean("android.showChronometer", true)
                }

                // 添加应用信息，与原始通知保持一致
                extras.putBoolean("android.reduced.images", true)

                // 添加超级岛源包信息，与原始通知保持一致
                extras.putString("superIslandSourcePackage", context.packageName)

                // 添加包名信息，与原始通知保持一致
                extras.putString("app_package", context.packageName)

                // 添加MIUI焦点通知所需的额外字段
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
     * @param title 通知标题
     * @param text 通知内容
     * @param picMap 图片映射
     */
    fun addMediaSuperIslandStructuredData(
        builder: NotificationCompat.Builder,
        context: Context,
        title: String?,
        text: String?,
        picMap: Map<String, String>?
    ) {
        try {
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
            picMap?.let { map: Map<String, String> ->
                // 按照小米官方文档规范，将每个图片资源作为单独的extra添加
                map.forEach { (picKey, picUrl) ->
                    if (picKey.startsWith("miui.focus.pic_")) {
                        val actualPicUrl: String = SuperIslandImageStore.resolve(context, picUrl) ?: picUrl
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
        bContent: String? = null
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
                        put("param_v2", paramV2Json) // 将更新后的paramV2作为嵌套字段
                    }

                    extras.putString("miui.focus.param", fullFocusParam.toString())
                } catch (e: Exception) {
                    // 如果构建完整结构失败，回退到直接使用原始数据
                    extras.putString("miui.focus.param", it)
                }
            }

            // 按照小米官方文档规范，将每个图片资源作为单独的extra添加
            picMap?.let { map ->
                map.forEach { (picKey, picUrl) ->
                    // 确保key以"miui.focus.pic_"前缀开头，符合小米规范
                    if (picKey.startsWith("miui.focus.pic_")) {
                            // 解析图片绑定ID，获取实际的图片数据
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

            // 添加焦点通知必要的额外字段
            extras.putBoolean("miui.showAction", true)

            // 添加模拟的action字段
            val actionsBundle = Bundle()
            actionsBundle.putString("miui.focus.action_1", "dummy_action_1")
            actionsBundle.putString("miui.focus.action_2", "dummy_action_2")
            extras.putBundle("miui.focus.actions", actionsBundle)

            // 添加应用信息，与原始通知保持一致
            extras.putBoolean("android.reduced.images", true)

            // 添加超级岛源包信息，与原始通知保持一致
            extras.putString("superIslandSourcePackage", context.packageName)

            // 包名信息
            extras.putString("app_package", context.packageName)

            Logger.i(TAG, "添加非媒体类型超级岛结构化数据成功")
        } catch (e: Exception) {
            Logger.w(TAG, "添加非媒体类型超级岛结构化数据失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
