package github.xzynine.superislandui.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

// 复现 Android SDK 内置 JSONObject 的 optXxx 取值语义，供 commonMain 的只读解析代码使用：
// - 缺失键：optString/optInt/optLong/optBoolean 返回 fallback，optObject/optArray 返回 null；
// - JSON null：optString 返回 "null"（JSON.toString(JSONObject.NULL)），其余返回 fallback；
// - 非字符串取字符串：字符串原值，其它类型取 JSON 字面量文本（嵌套对象/数组取紧凑 JSON 文本）；
// - 数字取整：数字取整数值，字符串按 Double.parseDouble 解析后截断，解析失败返回 fallback；
// - 布尔：布尔原值，字符串按 Boolean.parseBoolean 规则（仅忽略大小写的 "true" 为 true）。

fun JsonObject.optStringOr(
    key: String,
    fallback: String = "",
): String = optStringOrNull(key) ?: fallback

fun JsonObject.optStringOrNull(key: String): String? =
    this[key]?.let { element ->
        (element as? JsonPrimitive)?.content ?: element.toString()
    }

fun JsonObject.optIntOr(
    key: String,
    fallback: Int = 0,
): Int = this[key].asPrimitiveContent()?.toDoubleOrNull()?.toInt() ?: fallback

fun JsonObject.optLongOr(
    key: String,
    fallback: Long = 0L,
): Long {
    val primitive = this[key] as? JsonPrimitive ?: return fallback
    if (primitive is JsonNull) return fallback
    val value =
        if (primitive.isString) {
            primitive.content.toDoubleOrNull()?.toLong()
        } else {
            primitive.content.toLongOrNull() ?: primitive.content.toDoubleOrNull()?.toLong()
        }
    return value ?: fallback
}

fun JsonObject.optBooleanOr(
    key: String,
    fallback: Boolean = false,
): Boolean {
    val primitive = this[key] as? JsonPrimitive ?: return fallback
    if (primitive is JsonNull) return fallback
    val value =
        if (primitive.isString) {
            primitive.content.equals("true", ignoreCase = true)
        } else {
            primitive.content.toBooleanStrictOrNull()
        }
    return value ?: fallback
}

fun JsonObject.optObjectOrNull(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.optArrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

fun JsonArray.optObjectOrNull(index: Int): JsonObject? = getOrNull(index) as? JsonObject

// 复现 Android 内置 JSONObject 的 putOpt(name, value) 语义：value 为 null 时**不写入**该键（缺失即等价）；
// 与 put(name, null) 的「删除键」语义不同，后者由调用方显式删除（本文件不提供，构建期从不写 null）。
fun JsonObjectBuilder.putOpt(
    key: String,
    value: String?,
) {
    if (value != null) put(key, value)
}

fun JsonObjectBuilder.putOpt(
    key: String,
    value: Int?,
) {
    if (value != null) put(key, value)
}

fun JsonObjectBuilder.putOpt(
    key: String,
    value: Long?,
) {
    if (value != null) put(key, value)
}

fun JsonObjectBuilder.putOpt(
    key: String,
    value: Boolean?,
) {
    if (value != null) put(key, value)
}

fun JsonObjectBuilder.putOpt(
    key: String,
    value: JsonElement?,
) {
    if (value != null) put(key, value)
}

// 以紧凑 JSON 文本输出，字节与 Android 运行时内置 JSONObject 一致：
// - 键序：两者均为插入序（平台实现为 LinkedHashMap，kotlinx JsonObject 亦保序）；
// - 转义：平台实现（AOSP JSONStringer.string）额外把 "/" 写成 "\/"，kotlinx 默认不转义；
//   其余集合一致（"、\、U+0000–U+001F 的 \b \t \n \f \r 短转义与 \u00XX 小写十六进制）；
// - 数字：调用方只写 Int/Long/Boolean/String，平台 numberToString 与 JsonPrimitive 文本一致。
// 紧凑 JSON 的结构字符为 {}[],:，裸 "/" 只可能出现在字符串字面量内，故全文替换不影响结构。
fun JsonElement.toOrgJsonString(): String = toString().replace("/", "\\/")

private fun JsonElement?.asPrimitiveContent(): String? = (this as? JsonPrimitive)?.content
