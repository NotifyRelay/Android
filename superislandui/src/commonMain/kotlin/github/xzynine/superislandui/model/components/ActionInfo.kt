package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.json.optBooleanOr
import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import kotlinx.serialization.json.JsonObject

// 操作信息：定义按钮或操作的行为和样式
data class ActionInfo(
    val action: String? = null, // 系统预定义action的key
    val actionIcon: String? = null, // 按钮图标资源key
    val actionIconDark: String? = null, // 深色模式按钮图标
    val actionTitle: String? = null, // 按钮文本
    val actionTitleColor: String? = null, // 按钮文本颜色
    val actionTitleColorDark: String? = null, // 深色模式按钮文本颜色
    val actionBgColor: String? = null, // 按钮背景颜色
    val actionBgColorDark: String? = null, // 深色模式按钮背景颜色
    val actionIntentType: Int? = null, // 跳转类型：1 activity, 2 broadcast, 3 service
    val actionIntent: String? = null, // 跳转URI
    val clickWithCollapse: Boolean? = null, // 点击是否收起面板
    val type: Int? = null, // 按钮类型：0普通，1进度条文字
    val progressInfo: ProgressInfo? = null, // 进度按钮的进度信息
)

// 解析操作信息组件（按钮等）
fun parseActionInfo(json: JsonObject): ActionInfo =
    ActionInfo(
        action = json.optStringOr("action", "").takeIf { it.isNotEmpty() },
        actionIcon = json.optStringOr("actionIcon", "").takeIf { it.isNotEmpty() },
        actionIconDark = json.optStringOr("actionIconDark", "").takeIf { it.isNotEmpty() },
        actionTitle = json.optStringOr("actionTitle", "").takeIf { it.isNotEmpty() },
        actionTitleColor = json.optStringOr("actionTitleColor", "").takeIf { it.isNotEmpty() },
        actionTitleColorDark = json.optStringOr("actionTitleColorDark", "").takeIf { it.isNotEmpty() },
        actionBgColor = json.optStringOr("actionBgColor", "").takeIf { it.isNotEmpty() },
        actionBgColorDark = json.optStringOr("actionBgColorDark", "").takeIf { it.isNotEmpty() },
        actionIntentType = json.optIntOr("actionIntentType", -1).takeIf { it != -1 },
        actionIntent = json.optStringOr("actionIntent", "").takeIf { it.isNotEmpty() },
        clickWithCollapse = json.optBooleanOr("clickWithCollapse", false),
        type = json.optIntOr("type", -1).takeIf { it != -1 },
        progressInfo = json.optObjectOrNull("progressInfo")?.let { parseProgressInfo(it) },
    )

// 按钮UI构建已移除，仅保留数据解析逻辑
