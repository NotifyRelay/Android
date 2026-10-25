package github.xzynine.superislandui.common

import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.json.toOrgJsonString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import notifyrelay.shared.log.Logger

/**
 * miui.focus.param 摘要提取结果。
 *
 * 三个 raw 字段均为紧凑 JSON 文本，与 Android 内置 JSONObject.toString() 输出逐字节一致。
 */
data class IslandSummary(
    val title: String?,
    val text: String?,
    val paramV2Raw: String,
    val paramIslandRaw: String?,
    val bigIslandAreaRaw: String?,
)

/**
 * 从 miui.focus.param 原文提取摘要。
 *
 * 字段优先级：baseInfo.title/content → aodTitle → smallIslandArea.title/content。
 * verifyCode 非空时先回填 param_v2 中的 ****** 占位符，回填结果参与 raw 文本输出。
 * 原文不是 JSON 对象、或 param_v2/baseInfo/param_island 等字段不是对象时抛出异常，由调用方决定兜底。
 */
fun extractIslandSummary(
    paramV2Json: String,
    verifyCode: String?,
): IslandSummary {
    val root = Json.parseToJsonElement(paramV2Json).jsonObject
    val rawParamV2 =
        if (root.containsKey("param_v2")) {
            root.getValue("param_v2").jsonObject
        } else {
            root
        }
    val pv = if (verifyCode.isNullOrEmpty()) rawParamV2 else replaceVerifyCodePlaceholder(rawParamV2, verifyCode)

    var title: String? = null
    var text: String? = null

    // 提取 baseInfo (焦点通知数据) 中的 title/content
    if (pv.containsKey("baseInfo")) {
        val base = pv.getValue("baseInfo").jsonObject
        val baseTitle = base.optStringOr("title", "")
        if (baseTitle.isNotEmpty()) title = baseTitle
        val baseContent = base.optStringOr("content", "")
        if (baseContent.isNotEmpty()) text = baseContent
    }

    // aodTitle 优先用于息屏场景的摘要
    if (pv.containsKey("aodTitle") && (title == null || title.isEmpty())) {
        val aodTitle = pv.optStringOr("aodTitle", "")
        if (aodTitle.isNotEmpty()) title = aodTitle
    }

    var paramIslandRaw: String? = null
    var bigIslandAreaRaw: String? = null
    // param_island 中可能包含更丰富的摘要数据
    if (pv.containsKey("param_island")) {
        val island = pv.getValue("param_island").jsonObject
        paramIslandRaw = island.toOrgJsonString()
        // 从 island 的 smallIslandArea 提取简单文本
        if (island.containsKey("smallIslandArea")) {
            val small = island.getValue("smallIslandArea").jsonObject
            if (small.containsKey("title") && title.isNullOrEmpty()) {
                val smallTitle = small.optStringOr("title", "")
                if (smallTitle.isNotEmpty()) title = smallTitle
            }
            if (small.containsKey("content") && text.isNullOrEmpty()) {
                val smallContent = small.optStringOr("content", "")
                if (smallContent.isNotEmpty()) text = smallContent
            }
        }
        if (island.containsKey("bigIslandArea")) {
            bigIslandAreaRaw = island.getValue("bigIslandArea").jsonObject.toOrgJsonString()
        }
    }

    return IslandSummary(
        title = title,
        text = text,
        paramV2Raw = pv.toOrgJsonString(),
        paramIslandRaw = paramIslandRaw,
        bigIslandAreaRaw = bigIslandAreaRaw,
    )
}

/**
 * 将 param_v2 中的 ****** 占位符替换为真实验证码。
 * 锁屏态系统短信App会把验证码显示为 ******，真实值位于 verify_code 字段。
 */
private fun replaceVerifyCodePlaceholder(
    paramV2: JsonObject,
    verifyCode: String,
): JsonObject {
    var replaced = paramV2
    try {
        // iconTextInfo.title
        val iconTextInfo = replaced.optObjectOrNull("iconTextInfo")
        if (iconTextInfo != null) {
            val title = iconTextInfo.optStringOr("title", "")
            if (title.contains("******") || title.contains("****")) {
                val value = title.replace("******", verifyCode).replace("****", verifyCode)
                replaced = replaced.withField("iconTextInfo", iconTextInfo.withString("title", value))
                Logger.i("超级岛", "超级岛: 已回填 iconTextInfo.title 中的验证码占位符")
            }
        }

        // param_island.bigIslandArea.textInfo.title
        val island = replaced.optObjectOrNull("param_island")
        val bigIslandArea = island?.optObjectOrNull("bigIslandArea")
        val textInfo = bigIslandArea?.optObjectOrNull("textInfo")
        if (island != null && bigIslandArea != null && textInfo != null) {
            val title = textInfo.optStringOr("title", "")
            if (title.contains("******") || title.contains("****")) {
                val value = title.replace("******", verifyCode).replace("****", verifyCode)
                val newTextInfo = textInfo.withString("title", value)
                val newBigIslandArea = bigIslandArea.withField("textInfo", newTextInfo)
                replaced = replaced.withField("param_island", island.withField("bigIslandArea", newBigIslandArea))
                Logger.i("超级岛", "超级岛: 已回填 bigIslandArea.textInfo.title 中的验证码占位符")
            }
        }
    } catch (e: Exception) {
        Logger.w("超级岛", "超级岛: 验证码回填失败: ${e.message}")
    }
    return replaced
}

// 替换已有键时保持其插入位置（Map.plus 走 LinkedHashMap.put），与内置 JSONObject.put 的行为一致
private fun JsonObject.withField(
    key: String,
    value: JsonElement,
): JsonObject = JsonObject(this + (key to value))

private fun JsonObject.withString(
    key: String,
    value: String,
): JsonObject = withField(key, JsonPrimitive(value))
