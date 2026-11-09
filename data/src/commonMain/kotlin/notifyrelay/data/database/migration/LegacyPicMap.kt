package notifyrelay.data.database.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 解析旧 `picMap` 列：JSON 对象，键为图片键、值为图片 id 字符串。
 *
 * 该列在 v10 拆为关联表，v9→v10 迁移与旧导出文件导入都要读它。
 * 非法或空值退化为空表，使调用方继续处理其余记录而不是整批失败。
 *
 * @param raw 旧字段原文。
 * @returns 图片键到图片 id 的映射。
 */
fun parseLegacyPicMap(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    val parsed: Map<String, String> =
        runCatching {
            val obj = Json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching emptyMap<String, String>()
            obj.entries.associate { (key, value) -> key to value.toString().trim('"') }
        }.getOrDefault(emptyMap())
    return parsed
}
