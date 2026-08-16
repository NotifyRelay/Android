package github.xzynine.superislandui.diff

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

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

    data class Diff(
        val title: String? = null,
        val text: String? = null,
        val paramV2Raw: String? = null,
        val picsChanged: Map<String, String> = emptyMap(),
        val picsRemoved: List<String> = emptyList(),
    ) {
        fun isEmpty(): Boolean = title == null && text == null && paramV2Raw == null && picsChanged.isEmpty() && picsRemoved.isEmpty()

        fun toJson(): JSONObject =
            JSONObject().apply {
                if (title != null) put("title", title)
                if (text != null) put("text", text)
                if (paramV2Raw != null) put("param_v2_raw", paramV2Raw)
                if (picsChanged.isNotEmpty()) put("pics", JSONObject(picsChanged))
                if (picsRemoved.isNotEmpty()) put("pics_removed", JSONArray(picsRemoved))
            }
    }

    fun diff(
        old: State?,
        new: State,
    ): Diff {
        if (old == null) {
            return Diff(
                title = new.title,
                text = new.text,
                paramV2Raw = new.paramV2Raw,
                picsChanged = new.pics,
            )
        }
        var t: String? = null
        var c: String? = null
        var p2: String? = null
        if ((old.title ?: "") != (new.title ?: "")) t = new.title ?: ""
        if ((old.text ?: "") != (new.text ?: "")) c = new.text ?: ""
        val oldP2 = old.paramV2Raw ?: ""
        val newP2 = new.paramV2Raw ?: ""
        if (oldP2 != newP2) p2 = new.paramV2Raw
        val changed = mutableMapOf<String, String>()
        val removed = mutableListOf<String>()
        for ((k, v) in new.pics) {
            val ov = old.pics[k]
            if (ov == null || ov != v) changed[k] = v
        }
        for (k in old.pics.keys) {
            if (!new.pics.containsKey(k)) removed += k
        }
        return Diff(t, c, p2, changed, removed)
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { b -> ((b.toInt() and 0xFF).toString(16)).padStart(2, '0') }
    }
}
