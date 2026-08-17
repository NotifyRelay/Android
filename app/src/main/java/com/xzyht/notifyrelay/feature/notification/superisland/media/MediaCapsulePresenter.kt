package com.xzyht.notifyrelay.feature.notification.superisland.media

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
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
        val paramV2Raw = buildParamV2(title.orEmpty(), text.orEmpty())
        val resolvedPicMap = picMap ?: buildDefaultPicMap(coverUrl)
        FloatingReplicaManager.showFloating(
            context = context,
            sourceId = sourceId,
            title = title,
            text = text,
            paramV2Raw = paramV2Raw,
            picMap = resolvedPicMap,
            appName = appName,
        )
    }

    fun buildParamV2(
        title: String,
        text: String,
    ): String =
        JSONObject()
            .apply {
                put("business", "media")
                put(
                    "baseInfo",
                    JSONObject().apply {
                        put("title", title)
                        put("content", text)
                    },
                )
                put(
                    "param_island",
                    JSONObject().apply {
                        put(
                            "bigIslandArea",
                            JSONObject().apply {
                                put("type", "media")
                            },
                        )
                    },
                )
            }.toString()

    private fun buildDefaultPicMap(coverUrl: String?): Map<String, String>? {
        if (coverUrl.isNullOrBlank()) return null
        return mapOf(
            "miui.focus.pic_cover" to coverUrl,
            "miui.focus.pic_app_icon" to coverUrl,
        )
    }
}
