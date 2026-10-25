package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import kotlinx.serialization.json.JsonObject

// 动画文本组件：animTextInfo（独立于 HighlightInfo�?
data class AnimIconInfo(
    val src: String,
    val srcDark: String? = null,
)

data class AnimTextInfo(
    val icon: AnimIconInfo,
    val title: String? = null, // 主要文本（与 timerInfo 至少二选一�?
    val content: String? = null, // 次要文本
    val timerInfo: TimerInfo? = null, // 计时信息
    val colorTitle: String? = null,
    val colorTitleDark: String? = null,
    val colorContent: String? = null,
    val colorContentDark: String? = null,
)

fun parseAnimTextInfo(json: JsonObject): AnimTextInfo? {
    val iconObj = json.optObjectOrNull("animIconInfo") ?: return null
    val src = iconObj.optStringOr("src", "").takeIf { it.isNotBlank() } ?: return null
    val srcDark = iconObj.optStringOr("srcDark", "").takeIf { it.isNotBlank() }

    val title = json.optStringOr("title", "").takeIf { it.isNotBlank() }
    val tInfo = json.optObjectOrNull("timerInfo")?.let { parseTimerInfo(it) }
    if (title.isNullOrBlank() && tInfo == null) return null // 至少二选一

    return AnimTextInfo(
        icon = AnimIconInfo(src = src, srcDark = srcDark),
        title = title,
        content = json.optStringOr("content", "").takeIf { it.isNotBlank() },
        timerInfo = tInfo,
        colorTitle = json.optStringOr("colorTitle", "").takeIf { it.isNotBlank() },
        colorTitleDark = json.optStringOr("colorTitleDark", "").takeIf { it.isNotBlank() },
        colorContent = json.optStringOr("colorContent", "").takeIf { it.isNotBlank() },
        colorContentDark = json.optStringOr("colorContentDark", "").takeIf { it.isNotBlank() },
    )
}
