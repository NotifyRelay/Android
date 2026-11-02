package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.parseActionInfo
import kotlinx.serialization.json.JsonObject

// 图片信息模板：识别图形组件，显示应用图标或自定义图片
data class PicInfo(
    val type: Int, // 组件类型：1 appIcon，2 middle，3 large，5 倒计时带图
    val pic: String? = null, // 图片资源key（type=2/3时必传）
    val picDark: String? = null, // 深色模式图片资源key
    val actionInfo: ActionInfo? = null, // 操作信息
    val title: String? = null, // 组件文字（type=5时使用）
    val colorTitle: String? = null, // 文字颜色（type=5时使用）
)

// 解析图片信息组件（识别图形组件）
fun parsePicInfo(json: JsonObject): PicInfo =
    PicInfo(
        type = json.optIntOr("type", 1),
        pic = json.optStringOr("pic", "").takeIf { it.isNotEmpty() },
        picDark = json.optStringOr("picDark", "").takeIf { it.isNotEmpty() },
        actionInfo = json.optObjectOrNull("actionInfo")?.let { parseActionInfo(it) },
        title = json.optStringOr("title", "").takeIf { it.isNotEmpty() },
        colorTitle = json.optStringOr("colorTitle", "").takeIf { it.isNotEmpty() },
    )
