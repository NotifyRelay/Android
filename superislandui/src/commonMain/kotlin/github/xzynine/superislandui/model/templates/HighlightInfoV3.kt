package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optBooleanOr
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.parseActionInfo
import kotlinx.serialization.json.JsonObject

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
fun parseHighlightInfoV3(json: JsonObject): HighlightInfoV3 =
    HighlightInfoV3(
        primaryText = json.optStringOr("primaryText", "").takeIf { it.isNotEmpty() },
        secondaryText = json.optStringOr("secondaryText", "").takeIf { it.isNotEmpty() },
        showSecondaryLine = json.optBooleanOr("showSecondaryLine", false),
        highLightText = json.optStringOr("highLightText", "").takeIf { it.isNotEmpty() },
        primaryColor = json.optStringOr("primaryColor", "").takeIf { it.isNotEmpty() },
        secondaryColor = json.optStringOr("secondaryColor", "").takeIf { it.isNotEmpty() },
        highLightTextColor = json.optStringOr("highLightTextColor", "").takeIf { it.isNotEmpty() },
        highLightbgColor = json.optStringOr("highLightbgColor", "").takeIf { it.isNotEmpty() },
        primaryColorDark = json.optStringOr("primaryColorDark", "").takeIf { it.isNotEmpty() },
        secondaryColorDark = json.optStringOr("secondaryColorDark", "").takeIf { it.isNotEmpty() },
        highLightTextColorDark = json.optStringOr("highLightTextColorDark", "").takeIf { it.isNotEmpty() },
        highLightbgColorDark = json.optStringOr("highLightbgColorDark", "").takeIf { it.isNotEmpty() },
        actionInfo = json.optObjectOrNull("actionInfo")?.let { parseActionInfo(it) },
    )
