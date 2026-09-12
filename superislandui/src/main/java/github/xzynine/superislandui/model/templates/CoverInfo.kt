package github.xzynine.superislandui.model.templates

import org.json.JSONObject

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
fun parseCoverInfo(json: JSONObject): CoverInfo =
    CoverInfo(
        picCover = json.optString("picCover", "").takeIf { it.isNotEmpty() },
        title = json.optString("title", "").takeIf { it.isNotEmpty() },
        content = json.optString("content", "").takeIf { it.isNotEmpty() },
        subContent = json.optString("subContent", "").takeIf { it.isNotEmpty() },
        colorTitle = json.optString("colorTitle", "").takeIf { it.isNotEmpty() },
        colorTitleDark = json.optString("colorTitleDark", "").takeIf { it.isNotEmpty() },
        colorContent = json.optString("colorContent", "").takeIf { it.isNotEmpty() },
        colorContentDark = json.optString("colorContentDark", "").takeIf { it.isNotEmpty() },
        colorSubContent = json.optString("colorSubContent", "").takeIf { it.isNotEmpty() },
        colorSubContentDark = json.optString("colorSubContentDark", "").takeIf { it.isNotEmpty() },
    )
