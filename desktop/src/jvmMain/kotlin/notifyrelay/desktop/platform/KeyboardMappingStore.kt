package notifyrelay.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger
import notifyrelay.overlay.api.KeyMapping

/**
 * 快捷键映射规则的持久化编解码。
 *
 * 以 JSON 数组字符串存入 [notifyrelay.data.Storage]；
 * 解码失败时返回空列表并记录告警，不抛异常。
 */
internal object KeyboardMappingStore {
    private const val TAG = "KeyMappingStore"

    private val json = Json { ignoreUnknownKeys = true }

    /** 编码为 JSON 数组字符串。 */
    fun encode(mappings: List<KeyMapping>): String =
        buildJsonArray {
            mappings.forEach { mapping ->
                add(
                    buildJsonObject {
                        put("id", mapping.id)
                        put("name", mapping.name)
                        put("sourceKeys", JsonArray(mapping.sourceKeys.map { JsonPrimitive(it) }))
                        put("targetKey", mapping.targetKey)
                        put("displayText", mapping.displayText)
                        put("enabled", mapping.enabled)
                    },
                )
            }
        }.toString()

    /** 解码 JSON 数组字符串；空串或非法内容返回空列表。 */
    fun decode(text: String): List<KeyMapping> {
        if (text.isBlank()) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
            root.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val sourceKeys =
                    (obj["sourceKeys"] as? JsonArray)
                        ?.mapNotNull { runCatching { it.jsonPrimitive.int }.getOrNull() }
                        .orEmpty()
                KeyMapping(
                    id = obj.stringOrEmpty("id"),
                    name = obj.stringOrEmpty("name"),
                    sourceKeys = sourceKeys,
                    targetKey = runCatching { obj["targetKey"]?.jsonPrimitive?.int }.getOrNull() ?: 0,
                    displayText = obj.stringOrEmpty("displayText"),
                    enabled = obj["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                )
            }
        }.getOrElse {
            Logger.w(TAG, "快捷键映射解码失败，按空列表处理", it)
            emptyList()
        }
    }

    /** 读取 JSON 对象的字符串字段；缺失或非字符串时返回空串。 */
    private fun JsonObject.stringOrEmpty(field: String): String = (this[field] as? JsonPrimitive)?.content ?: ""
}
