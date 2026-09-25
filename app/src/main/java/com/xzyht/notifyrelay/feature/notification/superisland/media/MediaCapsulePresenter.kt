package com.xzyht.notifyrelay.feature.notification.superisland.media

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import github.xzynine.superislandui.builder.SuperIslandParamBuilder
import org.json.JSONObject

object MediaCapsulePresenter {
    fun show(
        context: Context,
        sourceId: String,
        title: String?,
        text: String?,
        appName: String? = null,
        picMap: Map<String, String>? = null,
        coverUrl: String? = null,
    ) {
        // 处理歌词拆分（逻辑统一在 LyricsSplitter，P3-1/D2）
        val split = LyricsSplitter.split(context, title)
        val iconText = split.iconText
        val capsuleText = split.capsuleText

        val paramV2Raw = buildParamV2(title.orEmpty(), text.orEmpty(), iconText, capsuleText)
        val resolvedPicMap = picMap ?: buildDefaultPicMap(coverUrl)
        FloatingReplicaManager.showFloating(
            context = context,
            sourceId = sourceId,
            title = title,
            text = text,
            paramV2Raw = paramV2Raw,
            picMap = resolvedPicMap,
            appName = appName,
            // 远端媒体有独立开关，不参与超级岛通道切换的重建
            cacheForChannelSwitch = false,
        )
    }

    fun buildParamV2(
        title: String,
        text: String,
        iconText: String = "",
        capsuleText: String = "",
    ): String {
        val bigIslandArea =
            JSONObject().apply {
                // 左侧：图文组件1（图+歌词左）
                put(
                    "imageTextInfoLeft",
                    JSONObject().apply {
                        put("type", 1)
                        put(
                            "picInfo",
                            JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_cover")
                            },
                        )
                        // 短文本（iconText 为空）时左侧为纯专辑图，不放文字
                        if (iconText.isNotEmpty()) {
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
                // 右侧：文本组件（歌词右）
                put(
                    "textInfo",
                    JSONObject().apply {
                        put("frontTitle", "")
                        put("title", capsuleText)
                        put("content", "")
                        put("narrowFont", false)
                        put("showHighlightColor", true)
                    },
                )
            }
        // 小岛：封面图（规范必传 smallIslandArea）
        val smallIslandArea =
            JSONObject().apply {
                put(
                    "picInfo",
                    JSONObject().apply {
                        put("type", 1)
                        put("pic", "miui.focus.pic_cover")
                    },
                )
            }

        // 由约束构建器生成合规 param_v2（business + param_island 为编译期必传项），
        // 保持原有契约：返回裸 param_v2 字符串（由上层统一包裹 param_v2）
        return SuperIslandParamBuilder
            .business("media")
            .island {
                bigIslandArea(bigIslandArea)
                smallIslandArea(smallIslandArea)
            }.component(
                "baseInfo",
                JSONObject().apply {
                    put("title", title)
                    put("content", text)
                },
            ).buildParamV2()
            .toString()
    }

    private fun buildDefaultPicMap(coverUrl: String?): Map<String, String>? {
        if (coverUrl.isNullOrBlank()) return null
        return mapOf(
            "miui.focus.pic_cover" to coverUrl,
            "miui.focus.pic_app_icon" to coverUrl,
        )
    }
}
