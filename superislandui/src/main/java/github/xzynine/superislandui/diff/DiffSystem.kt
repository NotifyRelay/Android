package github.xzynine.superislandui.diff

import org.json.JSONObject

object DiffSystem {
    data class State(
        val title: String?,
        val text: String?,
        val paramV2Raw: String?,
        val pics: Map<String, String>,
    ) {
        fun toJson(): JSONObject =
            JSONObject().apply {
                if (!title.isNullOrBlank()) put("title", title)
                if (!text.isNullOrBlank()) put("text", text)
                if (!paramV2Raw.isNullOrBlank()) put("param_v2_raw", paramV2Raw)
                if (pics.isNotEmpty()) put("pics", JSONObject(pics))
            }
    }
}
