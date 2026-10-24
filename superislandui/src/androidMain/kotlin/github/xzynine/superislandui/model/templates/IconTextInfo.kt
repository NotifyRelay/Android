package github.xzynine.superislandui.model.templates

import org.json.JSONObject

// 新图文组件：iconTextInfo（OS3 新增模板 14 / 14-2 / 16 / 17 / 18 / 22）
// 图片（animIconInfo）+ 主要文本（title）+ 次要文本1（content）+ 次要文本2（subContent）
data class IconTextInfo(
    val animIconInfo: IconTextAnimIcon? = null, // 图片
    val title: String? = null, // 主要文本
    val content: String? = null, // 次要文本1
    val subContent: String? = null, // 次要文本2
    val colorTitle: String? = null,
    val colorTitleDark: String? = null,
    val colorContent: String? = null,
    val colorContentDark: String? = null,
)

// 新图文组件内的图片资源（animIconInfo）
data class IconTextAnimIcon(
    val type: Int = 0, // 图片类型：0 静态图片（默认）
    val src: String? = null, // pics Bundle 中 key
    val srcDark: String? = null, // 深色模式静态图
)

// 解析新图文组件（iconTextInfo）
fun parseIconTextInfo(json: JSONObject): IconTextInfo =
    IconTextInfo(
        animIconInfo =
            json.optJSONObject("animIconInfo")?.let { obj ->
                IconTextAnimIcon(
                    type = obj.optInt("type", 0),
                    src = obj.optString("src", "").takeIf { it.isNotEmpty() },
                    srcDark = obj.optString("srcDark", "").takeIf { it.isNotEmpty() },
                )
            },
        title = json.optString("title", "").takeIf { it.isNotEmpty() },
        content = json.optString("content", "").takeIf { it.isNotEmpty() },
        subContent = json.optString("subContent", "").takeIf { it.isNotEmpty() },
        colorTitle = json.optString("colorTitle", "").takeIf { it.isNotEmpty() },
        colorTitleDark = json.optString("colorTitleDark", "").takeIf { it.isNotEmpty() },
        colorContent = json.optString("colorContent", "").takeIf { it.isNotEmpty() },
        colorContentDark = json.optString("colorContentDark", "").takeIf { it.isNotEmpty() },
    )
