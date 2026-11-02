package notifyrelay.desktop.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import notifyrelay.base.log.Logger

/**
 * 罗技设备自定义名的持久化编解码。
 *
 * 以 JSON 对象（`设备标识 → 名称`）存入 [notifyrelay.data.Storage]；
 * 解码失败时返回空表并记录告警，不抛异常。
 */
internal object LogiDeviceNameStore {
    private const val TAG = "LogiDeviceName"

    private val json = Json { ignoreUnknownKeys = true }

    /** 编码为 JSON 对象字符串。 */
    fun encode(overrides: Map<String, String>): String =
        buildJsonObject {
            overrides.forEach { (deviceId, name) -> put(deviceId, JsonPrimitive(name)) }
        }.toString()

    /** 解码 JSON 对象字符串；空串或非法内容返回空表。 */
    fun decode(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return runCatching {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyMap()
            root
                .mapNotNull { (deviceId, element) ->
                    val name = (element as? JsonPrimitive)?.content ?: return@mapNotNull null
                    if (name.isBlank()) null else deviceId to name
                }.toMap()
        }.getOrElse {
            Logger.w(TAG, "设备名覆盖解码失败，按空表处理", it)
            emptyMap()
        }
    }
}
