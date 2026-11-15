package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optNonEmptyString
import kotlinx.serialization.json.JsonObject

// 封面组件：coverInfo（OS3 新增模板 19）
// 封面图片（picCover）+ 主要文本（title）+ 次要文本1（content）+ 次要文本2（subContent）
data class CoverInfo(
    val picCover: String? = null, // 封面图片资源key
    val title: String? = null, // 主要文本
    val content: String? = null, // 次要文本1
    val subContent: String? = null, // 次要文本2
    val colorTitle: String? = null,
    val colorTitleDark: String? = null,
    val colorContent: String? = null,
    val colorContentDark: String? = null,
    val colorSubContent: String? = null,
    val colorSubContentDark: String? = null,
)

// 解析封面组件（coverInfo）
fun parseCoverInfo(json: JsonObject): CoverInfo =
    CoverInfo(
        picCover = json.optNonEmptyString("picCover"),
        title = json.optNonEmptyString("title"),
        content = json.optNonEmptyString("content"),
        subContent = json.optNonEmptyString("subContent"),
        colorTitle = json.optNonEmptyString("colorTitle"),
        colorTitleDark = json.optNonEmptyString("colorTitleDark"),
        colorContent = json.optNonEmptyString("colorContent"),
        colorContentDark = json.optNonEmptyString("colorContentDark"),
        colorSubContent = json.optNonEmptyString("colorSubContent"),
        colorSubContentDark = json.optNonEmptyString("colorSubContentDark"),
    )
