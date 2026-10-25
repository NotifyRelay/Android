package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.json.optArrayOrNull
import github.xzynine.superislandui.json.optObjectOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// 文本按钮模板：按钮组�?，纯文字按钮
data class TextButton(
    val actions: List<ActionInfo>, // 文字按钮列表�?-2个）
)

// 解析文本按钮组件（按钮组�?�?
fun parseTextButton(json: JsonObject): TextButton = TextButton(actions = json.optArrayOrNull("actions")?.let { parseActions(it) } ?: emptyList())

// 解析操作列表（按钮组件）
fun parseActions(jsonArray: JsonArray): List<ActionInfo> {
    val list = mutableListOf<ActionInfo>()
    for (i in 0 until jsonArray.size) {
        jsonArray.optObjectOrNull(i)?.let { parseActionInfo(it) }?.let { list.add(it) }
    }
    return list
}

// 按钮UI构建已移除，仅保留数据解析逻辑
