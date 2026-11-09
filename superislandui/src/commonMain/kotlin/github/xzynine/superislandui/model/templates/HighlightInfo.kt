package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optBooleanOr
import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optNonEmptyString
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.model.components.TimerInfo
import github.xzynine.superislandui.model.components.parseTimerInfo
import kotlinx.serialization.json.JsonObject

// 高亮信息模板：强调图文组件，强调显示数据或内容
data class HighlightInfo(
    val title: String? = null, // 强调文本：需要强调的数据或内容
    val timerInfo: TimerInfo? = null, // 时间信息
    val content: String? = null, // 辅助文本1：补充信息
    val picFunction: String? = null, // 功能图标资源key
    val picFunctionDark: String? = null, // 深色模式功能图标
    val subContent: String? = null, // 辅助文本2：状态信息
    val type: Int? = null, // 是否隐藏辅助文本1：1隐藏
    val colorTitle: String? = null, // 强调文本颜色
    val colorTitleDark: String? = null, // 深色模式强调文本颜色
    val colorContent: String? = null, // 辅助文本1颜色
    val colorContentDark: String? = null, // 深色模式辅助文本1颜色
    val colorSubContent: String? = null, // 辅助文本2颜色
    val colorSubContentDark: String? = null, // 深色模式辅助文本2颜色
    val bigImageLeft: String? = null, // 大岛区域左侧图片
    val bigImageRight: String? = null, // 大岛区域右侧图片
    val iconOnly: Boolean = false, // 是否仅展示图标
)

// 解析高亮信息组件（强调图文组件）
fun parseHighlightInfo(json: JsonObject): HighlightInfo =
    HighlightInfo(
        title = json.optNonEmptyString("title"),
        timerInfo = json.optObjectOrNull("timerInfo")?.let { parseTimerInfo(it) },
        content = json.optNonEmptyString("content"),
        picFunction = json.optNonEmptyString("picFunction"),
        picFunctionDark = json.optNonEmptyString("picFunctionDark"),
        subContent = json.optNonEmptyString("subContent"),
        type = json.optIntOr("type", -1).takeIf { it != -1 },
        colorTitle = json.optNonEmptyString("colorTitle"),
        colorTitleDark = json.optNonEmptyString("colorTitleDark"),
        colorContent = json.optNonEmptyString("colorContent"),
        colorContentDark = json.optNonEmptyString("colorContentDark"),
        colorSubContent = json.optNonEmptyString("colorSubContent"),
        colorSubContentDark = json.optNonEmptyString("colorSubContentDark"),
        bigImageLeft = json.optNonEmptyString("bigImageLeft"),
        bigImageRight = json.optNonEmptyString("bigImageRight"),
        iconOnly = json.optBooleanOr("iconOnly", false),
    )
