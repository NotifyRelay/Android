package github.xzynine.superislandui.diff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DiffSystem {
    data class State(
        val title: String?,
        val text: String?,
        val paramV2Raw: String?,
        val pics: Map<String, String>,
    ) {
        /** 输出 JSON 树；需要文本时用 toOrgJsonString()（除号转义与 Android 内置 JSONObject 一致）。 */
        fun toJson(): JsonObject =
            buildJsonObject {
                if (!title.isNullOrBlank()) put("title", title)
                if (!text.isNullOrBlank()) put("text", text)
                if (!paramV2Raw.isNullOrBlank()) put("param_v2_raw", paramV2Raw)
                if (pics.isNotEmpty()) put("pics", JsonObject(pics.mapValues { JsonPrimitive(it.value) }))
            }
    }
}
