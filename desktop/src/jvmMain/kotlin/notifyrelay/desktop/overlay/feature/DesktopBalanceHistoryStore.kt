package notifyrelay.desktop.overlay.feature

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.overlay.api.BalanceHistoryItem

/**
 * 余额历史的持久化编解码。
 *
 * 以 JSON 数组字符串存入 [Storage]；解码失败时返回空列表并记录告警。
 */
internal object DesktopBalanceHistoryStore {
    private const val TAG = "BalanceHistory"

    private const val KEY = "desktop_overlay_balance_history"

    private val json = Json { ignoreUnknownKeys = true }

    /** 读取历史；无记录或内容非法时返回空列表。 */
    fun load(): List<BalanceHistoryItem> {
        val text = runCatching { Storage.getString(KEY, "") }.getOrDefault("")
        if (text.isBlank()) return emptyList()
        return runCatching {
            val root = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
            root.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                BalanceHistoryItem(
                    time = runCatching { obj["time"]?.jsonPrimitive?.long }.getOrNull() ?: return@mapNotNull null,
                    balance = runCatching { obj["balance"]?.jsonPrimitive?.double }.getOrNull() ?: return@mapNotNull null,
                    change = runCatching { obj["change"]?.jsonPrimitive?.double }.getOrNull() ?: 0.0,
                    changeType = (obj["changeType"] as? JsonPrimitive)?.content ?: "init",
                    mergeCount = runCatching { obj["mergeCount"]?.jsonPrimitive?.long }.getOrNull()?.toInt() ?: 1,
                )
            }
        }.getOrElse {
            Logger.w(TAG, "余额历史解码失败，按空列表处理", it)
            emptyList()
        }
    }

    /** 写入历史。 */
    fun save(items: List<BalanceHistoryItem>) {
        runCatching {
            val text =
                buildJsonArray {
                    items.forEach { item ->
                        add(
                            buildJsonObject {
                                put("time", item.time)
                                put("balance", item.balance)
                                put("change", item.change)
                                put("changeType", item.changeType)
                                put("mergeCount", item.mergeCount)
                            },
                        )
                    }
                }.toString()
            Storage.putString(KEY, text)
        }.onFailure { Logger.w(TAG, "余额历史落盘失败", it) }
    }
}
