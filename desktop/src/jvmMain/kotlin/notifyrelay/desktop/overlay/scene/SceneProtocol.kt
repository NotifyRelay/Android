package notifyrelay.desktop.overlay.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 协议版本；双方不一致时接收端明确拒绝而非解析错乱。 */
internal const val SCENE_PROTOCOL_VERSION = 1

/** 命令管道名：会话报文与场景文档均经此传输。 */
internal const val SCENE_PIPE_NAME = "NotifyRelayGamebarScene"

/** 心跳发送间隔（毫秒）。 */
internal const val SCENE_PING_INTERVAL_MS = 2_000L

/** 心跳判定超时（毫秒）：超过此时长未收到对端信号即判失效。 */
internal const val SCENE_HEARTBEAT_TIMEOUT_MS = 6_000L

/** 报文编解码；本模块未启用序列化编译器插件，故直接操作 JSON 树。 */
internal val sceneJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

/**
 * 命令管道上的一条报文。
 *
 * 每行为一条完整 JSON，以换行分隔；[type] 决定其余字段的解释方式。
 * 该封闭集合是双方唯一的约定，业务字段不出现于此。
 */
internal data class SceneMessage(
    val type: String,
    val protocolVersion: Int? = null,
    val reason: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val dpi: Float? = null,
    val theme: String? = null,
    val revision: Long? = null,
    val document: String? = null,
    val regions: List<SceneRegion>? = null,
    val key: String? = null,
    val phase: String? = null,
    /** 点击位置（dp）；用于把点击送回 Compose 的输入分发。 */
    val x: Float? = null,
    val y: Float? = null,
    /** 位图资源的内容标识；见 [TYPE_IMAGE]。 */
    val imageId: String? = null,
    /** 位图资源的 PNG 字节（base64）；随 [TYPE_IMAGE] 一次性完整传输。 */
    val imageData: String? = null,
) {
    /** 编码为单行 JSON；空字段直接省略，减少报文体积。 */
    fun toJson(): String =
        buildJsonObject {
            put("type", type)
            protocolVersion?.let { put("protocolVersion", it) }
            reason?.let { put("reason", it) }
            width?.let { put("width", it) }
            height?.let { put("height", it) }
            dpi?.let { put("dpi", it) }
            theme?.let { put("theme", it) }
            revision?.let { put("revision", it) }
            document?.let { put("document", it) }
            key?.let { put("key", it) }
            phase?.let { put("phase", it) }
            x?.let { put("x", it) }
            y?.let { put("y", it) }
            imageId?.let { put("imageId", it) }
            imageData?.let { put("imageData", it) }
            regions?.let { list ->
                put(
                    "regions",
                    JsonArray(
                        list.map { region ->
                            buildJsonObject {
                                put("key", region.key)
                                put("x", region.x)
                                put("y", region.y)
                                put("width", region.width)
                                put("height", region.height)
                            }
                        },
                    ),
                )
            }
        }.toString()

    /** 编码为一行（以换行结尾）。 */
    fun encodeLine(): String = toJson() + "\n"

    companion object {
        const val TYPE_HELLO = "HELLO"
        const val TYPE_WELCOME = "WELCOME"
        const val TYPE_REJECT = "REJECT"
        const val TYPE_SUBSCRIBE = "SUBSCRIBE"
        const val TYPE_SCENE = "SCENE"
        const val TYPE_PING = "PING"
        const val TYPE_PONG = "PONG"
        const val TYPE_CLICK = "CLICK"
        const val TYPE_ERROR = "ERROR"

        /**
         * 位图资源。
         *
         * 场景文档以 imageId 引用位图，本报文每次完整携带 PNG 数据：
         * 场景体积因此保持在几 KB，而图片按需整体传输，不依赖接收端缓存命中。
         */
        const val TYPE_IMAGE = "IMAGE"

        /** 由单行 JSON 解析报文；结构不合法时抛出，由调用方记录并丢弃。 */
        fun parse(line: String): SceneMessage {
            val root = sceneJson.parseToJsonElement(line).jsonObject
            return SceneMessage(
                type = root.stringOrNull("type").orEmpty(),
                protocolVersion = root.intOrNull("protocolVersion"),
                reason = root.stringOrNull("reason"),
                width = root.floatOrNull("width"),
                height = root.floatOrNull("height"),
                dpi = root.floatOrNull("dpi"),
                theme = root.stringOrNull("theme"),
                revision = root.longOrNull("revision"),
                document = root.stringOrNull("document"),
                key = root.stringOrNull("key"),
                phase = root.stringOrNull("phase"),
                x = root.floatOrNull("x"),
                y = root.floatOrNull("y"),
                imageId = root.stringOrNull("imageId"),
                imageData = root.stringOrNull("imageData"),
                regions = root["regions"]?.jsonArray?.map { it.toRegion() },
            )
        }

        private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.intOrNull(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

        private fun JsonObject.longOrNull(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

        private fun JsonObject.floatOrNull(name: String): Float? = (this[name] as? JsonPrimitive)?.floatOrNull

        private fun JsonElement.toRegion(): SceneRegion {
            val obj = jsonObject
            return SceneRegion(
                key = obj.stringOrNull("key").orEmpty(),
                x = obj.floatOrNull("x") ?: 0f,
                y = obj.floatOrNull("y") ?: 0f,
                width = obj.floatOrNull("width") ?: 0f,
                height = obj.floatOrNull("height") ?: 0f,
            )
        }
    }
}

/**
 * 一个可点击区域。
 *
 * 坐标单位与场景文档一致（dp），因此命中判定只需「窗口局部坐标减原点」，
 * 不需要任何像素与 dp 之间的换算。
 */
internal data class SceneRegion(
    val key: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
