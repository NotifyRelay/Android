package github.xzynine.superislandui.model.templates

import github.xzynine.superislandui.json.optStringOr
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
        picCover = json.optStringOr("picCover", "").takeIf { it.isNotEmpty() },
        title = json.optStringOr("title", "").takeIf { it.isNotEmpty() },
        content = json.optStringOr("content", "").takeIf { it.isNotEmpty() },
        subContent = json.optStringOr("subContent", "").takeIf { it.isNotEmpty() },
        colorTitle = json.optStringOr("colorTitle", "").takeIf { it.isNotEmpty() },
        colorTitleDark = json.optStringOr("colorTitleDark", "").takeIf { it.isNotEmpty() },
        colorContent = json.optStringOr("colorContent", "").takeIf { it.isNotEmpty() },
        colorContentDark = json.optStringOr("colorContentDark", "").takeIf { it.isNotEmpty() },
        colorSubContent = json.optStringOr("colorSubContent", "").takeIf { it.isNotEmpty() },
        colorSubContentDark = json.optStringOr("colorSubContentDark", "").takeIf { it.isNotEmpty() },
    )
