package com.xzyht.notifyrelay.feature.device.model

import org.json.JSONObject

/** 通知数据 JSON 构造工具。 */
object NotificationJsonBuilder {
    /** 构造 json 格式的通知数据。 */
    fun buildNotificationJson(
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
    ): String {
        val json = JSONObject()
        json.put("packageName", packageName)
        json.put("appName", appName ?: packageName)
        json.put("title", title ?: "")
        json.put("text", text ?: "")
        json.put("time", time)
        return json.toString()
    }
}
