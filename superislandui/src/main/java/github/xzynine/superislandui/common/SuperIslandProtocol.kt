package github.xzynine.superislandui.common

import github.xzynine.superislandui.diff.DiffSystem
import org.json.JSONObject

/**
 * 超级岛同步协议。
 * - 提供稳定的"特征键名/键值"（si_feature_id），用于标识同一座"岛"的一次会话。
 * - 支持首包全量(SI_FULL)、后续差异包(SI_DELTA)、结束包(SI_END)。
 * - ACK hash 由 [enableAck] 控制，默认开启。
 */
object SuperIslandProtocol {
    const val FEATURE_KEY_NAME = "si_feature_id"
    const val TERMINATE_VALUE = "__END__"

    data class PayloadOptions(
        val routingField: String = "featureKeyName",
        val routingValue: String = FEATURE_KEY_NAME,
        val routingIdField: String = "featureKeyValue",
        val routingIdValue: String = "",
        val subtypeField: String? = null,
        val subtypeValue: String? = null,
        val extraFields: Map<String, String> = emptyMap(),
        val enableAck: Boolean = true
    ) {
        companion object {
            val SUPER_ISLAND = PayloadOptions()
            val MEDIA_FULL = PayloadOptions(
                routingField = "type", routingValue = "MEDIA_PLAY",
                routingIdValue = "media_global",
                subtypeField = "mediaType", subtypeValue = "FULL",
                enableAck = false
            )
            val MEDIA_DELTA = PayloadOptions(
                routingField = "type", routingValue = "MEDIA_PLAY",
                routingIdValue = "media_global",
                subtypeField = "mediaType", subtypeValue = "DELTA",
                enableAck = false
            )
            val MEDIA_END = PayloadOptions(
                routingField = "type", routingValue = "MEDIA_PLAY",
                routingIdValue = "media_global",
                subtypeField = "mediaType", subtypeValue = "END",
                extraFields = mapOf("terminateValue" to TERMINATE_VALUE),
                enableAck = false
            )
        }
    }

    fun buildPayload(
        pkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        content: JSONObject,
        options: PayloadOptions
    ): JSONObject {
        val obj = JSONObject().apply {
            put("packageName", pkg)
            put("appName", appName ?: pkg)
            put("time", time)
            put("isLocked", isLocked)
            put(options.routingField, options.routingValue)
            if (options.routingIdValue.isNotEmpty()) {
                put(options.routingIdField, options.routingIdValue)
            }
            options.subtypeField?.let { f ->
                options.subtypeValue?.let { v -> put(f, v) }
            }
            for ((k, v) in options.extraFields) put(k, v)
        }
        for (k in content.keys()) {
            obj.put(k, content.get(k))
        }
        if (options.enableAck) obj.put("hash", DiffSystem.sha256(obj.toString()))
        return obj
    }

    fun buildFullPayload(
        superPkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        featureId: String,
        state: DiffSystem.State,
        enableAck: Boolean = true
    ): JSONObject = buildPayload(
        superPkg, appName, time, isLocked, state.toJson(),
        PayloadOptions(routingIdValue = featureId, enableAck = enableAck)
    )

    fun buildDeltaPayload(
        superPkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        featureId: String,
        diff: DiffSystem.Diff,
        enableAck: Boolean = true
    ): JSONObject = buildPayload(
        superPkg, appName, time, isLocked,
        JSONObject().apply { put("changes", diff.toJson()) },
        PayloadOptions(routingIdValue = featureId, enableAck = enableAck)
    )

    fun buildEndPayload(
        superPkg: String,
        appName: String?,
        time: Long,
        isLocked: Boolean,
        featureId: String,
        enableAck: Boolean = true
    ): JSONObject = buildPayload(
        superPkg, appName, time, isLocked, JSONObject(),
        PayloadOptions(
            routingIdValue = featureId, enableAck = enableAck,
            extraFields = mapOf("terminateValue" to TERMINATE_VALUE)
        )
    )
}
