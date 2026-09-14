package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.parseActionInfo
import org.json.JSONObject

// 按钮组件5：highlightInfoV3（OS3 新增模板 17 / 18 / 19）
// 高亮文本（primaryText）+ 补充文本（secondaryText）+ 文字标签（highLightText）+ 圆头图文按钮（actionInfo）
data class HighlightInfoV3(
    val primaryText: String? = null, // 高亮文本
    val secondaryText: String? = null, // 补充文本
    val showSecondaryLine: Boolean = false, // 补充文本是否划线
    val highLightText: String? = null, // 文字标签
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val highLightTextColor: String? = null,
    val highLightbgColor: String? = null,
    val primaryColorDark: String? = null,
    val secondaryColorDark: String? = null,
    val highLightTextColorDark: String? = null,
    val highLightbgColorDark: String? = null,
    val actionInfo: ActionInfo? = null, // 圆头图文按钮
)

// 解析按钮组件5（highlightInfoV3）
fun parseHighlightInfoV3(json: JSONObject): HighlightInfoV3 =
    HighlightInfoV3(
        primaryText = json.optString("primaryText", "").takeIf { it.isNotEmpty() },
        secondaryText = json.optString("secondaryText", "").takeIf { it.isNotEmpty() },
        showSecondaryLine = json.optBoolean("showSecondaryLine", false),
        highLightText = json.optString("highLightText", "").takeIf { it.isNotEmpty() },
        primaryColor = json.optString("primaryColor", "").takeIf { it.isNotEmpty() },
        secondaryColor = json.optString("secondaryColor", "").takeIf { it.isNotEmpty() },
        highLightTextColor = json.optString("highLightTextColor", "").takeIf { it.isNotEmpty() },
        highLightbgColor = json.optString("highLightbgColor", "").takeIf { it.isNotEmpty() },
        primaryColorDark = json.optString("primaryColorDark", "").takeIf { it.isNotEmpty() },
        secondaryColorDark = json.optString("secondaryColorDark", "").takeIf { it.isNotEmpty() },
        highLightTextColorDark = json.optString("highLightTextColorDark", "").takeIf { it.isNotEmpty() },
        highLightbgColorDark = json.optString("highLightbgColorDark", "").takeIf { it.isNotEmpty() },
        actionInfo = json.optJSONObject("actionInfo")?.let { parseActionInfo(it) },
    )
