package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.TimerInfo
import github.xzynine.superislandui.model.components.parseActionInfo
import github.xzynine.superislandui.model.components.parseTimerInfo
import kotlinx.serialization.json.JsonObject

// 提示信息模板：按钮组件2和3，包含文本和按钮
data class HintInfo(
    val type: Int, // 组件类型：1按钮组件3，2按钮组件2
    val title: String? = null, // 主要文本：关键信息
    val timerInfo: TimerInfo? = null, // 时间信息
    val subTitle: String? = null, // 主要小文本2
    val content: String? = null, // 前置文本1或图文特殊标签文本
    val subContent: String? = null, // 前置文本2
    val picContent: String? = null, // 图文特殊标签图标资源key
    val colorTitle: String? = null, // 主要文本颜色
    val colorTitleDark: String? = null, // 深色模式主要文本颜色
    val colorSubTitle: String? = null, // 主要小文本2颜色
    val colorSubTitleDark: String? = null, // 深色模式主要小文本2颜色
    val colorContent: String? = null, // 前置文本1颜色
    val colorContentDark: String? = null, // 深色模式前置文本1颜色
    val colorSubContent: String? = null, // 前置文本2颜色
    val colorSubContentDark: String? = null, // 深色模式前置文本2颜色
    val colorContentBg: String? = null, // 图文特殊标签背景色
    val actionInfo: ActionInfo? = null, // 按钮操作信息
)

// 解析提示信息组件（按钮组件2和3）
fun parseHintInfo(json: JsonObject): HintInfo =
    HintInfo(
        type = json.optIntOr("type", 1),
        title = json.optStringOr("title", "").takeIf { it.isNotEmpty() },
        timerInfo = json.optObjectOrNull("timerInfo")?.let { parseTimerInfo(it) },
        subTitle = json.optStringOr("subTitle", "").takeIf { it.isNotEmpty() },
        content = json.optStringOr("content", "").takeIf { it.isNotEmpty() },
        subContent = json.optStringOr("subContent", "").takeIf { it.isNotEmpty() },
        picContent = json.optStringOr("picContent", "").takeIf { it.isNotEmpty() },
        colorTitle = json.optStringOr("colorTitle", "").takeIf { it.isNotEmpty() },
        colorTitleDark = json.optStringOr("colorTitleDark", "").takeIf { it.isNotEmpty() },
        colorSubTitle = json.optStringOr("colorSubTitle", "").takeIf { it.isNotEmpty() },
        colorSubTitleDark = json.optStringOr("colorSubTitleDark", "").takeIf { it.isNotEmpty() },
        colorContent = json.optStringOr("colorContent", "").takeIf { it.isNotEmpty() },
        colorContentDark = json.optStringOr("colorContentDark", "").takeIf { it.isNotEmpty() },
        colorSubContent = json.optStringOr("colorSubContent", "").takeIf { it.isNotEmpty() },
        colorSubContentDark = json.optStringOr("colorSubContentDark", "").takeIf { it.isNotEmpty() },
        colorContentBg = json.optStringOr("colorContentBg", "").takeIf { it.isNotEmpty() },
        actionInfo = json.optObjectOrNull("actionInfo")?.let { parseActionInfo(it) },
    )
