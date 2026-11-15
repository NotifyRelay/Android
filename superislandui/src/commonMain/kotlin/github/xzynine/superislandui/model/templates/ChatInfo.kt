package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optNonEmptyString
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.model.components.TimerInfo
import github.xzynine.superislandui.model.components.parseTimerInfo
import kotlinx.serialization.json.JsonObject

// 聊天信息模板：IM图文组件，显示头像、主要文本、次要文�?
data class ChatInfo(
    val picProfile: String? = null, // 头像图片资源key
    val picProfileDark: String? = null, // 深色模式头像图片
    val appIconPkg: String? = null, // 自定义应用图标包�?
    val title: String? = null, // 主要文本：关键信�?
    val content: String? = null, // 次要文本：补充描�?
    val timerInfo: TimerInfo? = null, // 时间信息
    val colorTitle: String? = null, // 主要文本颜色
    val colorTitleDark: String? = null, // 深色模式主要文本颜色
    val colorContent: String? = null, // 次要文本颜色
    val colorContentDark: String? = null, // 深色模式次要文本颜色
)

// 解析聊天信息组件（IM图文组件�?
fun parseChatInfo(json: JsonObject): ChatInfo =
    ChatInfo(
        picProfile = json.optNonEmptyString("picProfile"),
        picProfileDark = json.optNonEmptyString("picProfileDark"),
        appIconPkg = json.optNonEmptyString("appIconPkg"),
        title = json.optNonEmptyString("title"),
        content = json.optNonEmptyString("content"),
        timerInfo = json.optObjectOrNull("timerInfo")?.let { parseTimerInfo(it) },
        colorTitle = json.optNonEmptyString("colorTitle"),
        colorTitleDark = json.optNonEmptyString("colorTitleDark"),
        colorContent = json.optNonEmptyString("colorContent"),
        colorContentDark = json.optNonEmptyString("colorContentDark"),
    )
