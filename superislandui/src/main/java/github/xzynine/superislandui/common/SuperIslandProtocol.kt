package github.xzynine.superislandui.common

import github.xzynine.superislandui.diff.DiffSystem
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 超级岛同步协议。
 * - 提供稳定的"特征键名/键值"（si_feature_id），用于标识同一座"岛"的一次会话。
 * - 支持首包全量(SI_FULL)、后续差异包(SI_DELTA)、结束包(SI_END)。
 * - ACK hash 由 [enableAck] 控制，默认开启。
 */
object SuperIslandProtocol {
    const val FEATURE_KEY_NAME = "si_feature_id"
    const val TERMINATE_VALUE = "__END__"

    /**
     * 计算"岛"的特征ID。
     * - 基于 paramV2/title/text 等稳定内容字段生成特征。
     * - 如果提供了 instanceId（例如接收端的 sbnKey），会把它包含进特征以确保同内容的不同通知能被区分。
     */
    fun computeFeatureId(
        superPkg: String?,
        paramV2Raw: String?,
        title: String?,
        text: String?,
        instanceId: String? = null
    ): String {
        val keyParts = mutableListOf<String>()
        keyParts += (superPkg ?: "")
        try {
            if (!paramV2Raw.isNullOrBlank()) {
                val root = JSONObject(paramV2Raw)
                val chatInfo = root.optJSONObject("chatInfo")
                val baseInfo = root.optJSONObject("baseInfo")
                val highlight = root.optJSONObject("highlightInfo")
                when {
                    chatInfo != null -> {
                        val t = chatInfo.optString("title").takeIf { it.isNotBlank() }
                        if (!t.isNullOrBlank()) keyParts += "chat:" + t
                    }
                    baseInfo != null -> {
                        val t = baseInfo.optString("title").takeIf { it.isNotBlank() }
                        val c = baseInfo.optString("content").takeIf { it.isNotBlank() }
                        if (!t.isNullOrBlank()) keyParts += "baseT:" + t
                        if (!c.isNullOrBlank()) keyParts += "baseC:" + c
                    }
                    highlight != null -> {
                        val t = highlight.optString("title").takeIf { it.isNotBlank() }
                        if (!t.isNullOrBlank()) keyParts += "hi:" + t
                    }
                }
            }
        } catch (_: Exception) {}
        if (keyParts.size <= 1) {
            if (!title.isNullOrBlank()) keyParts += ("t:" + title)
            if (!text.isNullOrBlank()) keyParts += ("c:" + text)
        }
        if (!instanceId.isNullOrBlank()) {
            keyParts += "id:" + instanceId
        }
        val raw = keyParts.joinToString("|")
        return sha1(raw)
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return bytes.joinToString("") { b -> ((b.toInt() and 0xFF).toString(16)).padStart(2, '0') }
    }

    fun buildFullPayload(
        superPkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        featureId: String,
        state: DiffSystem.State,
        enableAck: Boolean = true
    ): JSONObject {
        val obj = JSONObject().apply {
            put("packageName", superPkg)
            put("appName", appName ?: superPkg)
            put("time", time)
            put("isLocked", isLocked)
            put("featureKeyName", FEATURE_KEY_NAME)
            put("featureKeyValue", featureId)
        }
        val data = state.toJson()
        for (k in data.keys()) {
            obj.put(k, data.get(k))
        }
        if (enableAck) obj.put("hash", DiffSystem.sha256(obj.toString()))
        return obj
    }

    fun buildDeltaPayload(
        superPkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        featureId: String,
        diff: DiffSystem.Diff,
        enableAck: Boolean = true
    ): JSONObject {
        val obj = JSONObject().apply {
            put("packageName", superPkg)
            put("appName", appName ?: superPkg)
            put("time", time)
            put("isLocked", isLocked)
            put("featureKeyName", FEATURE_KEY_NAME)
            put("featureKeyValue", featureId)
            put("changes", diff.toJson())
        }
        if (enableAck) obj.put("hash", DiffSystem.sha256(obj.toString()))
        return obj
    }

    fun buildEndPayload(
        superPkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        featureId: String,
        enableAck: Boolean = true
    ): JSONObject {
        val obj = JSONObject().apply {
            put("packageName", superPkg)
            put("appName", appName ?: superPkg)
            put("time", time)
            put("isLocked", isLocked)
            put("terminateValue", TERMINATE_VALUE)
            put("featureKeyName", FEATURE_KEY_NAME)
            put("featureKeyValue", featureId)
        }
        if (enableAck) obj.put("hash", DiffSystem.sha256(obj.toString()))
        return obj
    }
}
