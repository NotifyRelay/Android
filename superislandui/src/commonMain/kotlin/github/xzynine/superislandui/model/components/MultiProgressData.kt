package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optStringOr
import kotlinx.serialization.json.JsonObject

// 多进度信息：多段进度条组�?
data class MultiProgressInfo(
    val title: String, // 进度描述文本
    val progress: Int, // 当前进度百分�?
    val color: String? = null, // 进度条颜�?
    val points: Int? = null, // 节点数量�?-4�?
    val picForward: String? = null, // 进度指示�?
    val picForwardWait: String? = null, // 目标指示�?
    val picForwardBox: String? = null, // 进度条背景块
    val picMiddle: String? = null, // 激活的中间节点
    val picMiddleUnselected: String? = null, // 未激活的中间节点
    val picEnd: String? = null, // 激活的末尾节点
    val picEndUnselected: String? = null, // 未激活的末尾节点
)

// 解析多进度信息组�?
fun parseMultiProgressInfo(json: JsonObject): MultiProgressInfo {
    val middleUnselected =
        sequenceOf(
            json.optStringOr("picMiddleUnselected", ""),
            json.optStringOr("picMiddelUnselected", ""),
        ).firstOrNull { it.isNotEmpty() }
    return MultiProgressInfo(
        title = json.optStringOr("title", ""),
        progress = json.optIntOr("progress", 0),
        color = json.optStringOr("color", "").takeIf { it.isNotEmpty() },
        points = json.optIntOr("points", -1).takeIf { it != -1 },
        picForward = json.optStringOr("picForward", "").takeIf { it.isNotEmpty() },
        picForwardWait = json.optStringOr("picForwardWait", "").takeIf { it.isNotEmpty() },
        picForwardBox = json.optStringOr("picForwardBox", "").takeIf { it.isNotEmpty() },
        picMiddle = json.optStringOr("picMiddle", "").takeIf { it.isNotEmpty() },
        picMiddleUnselected = middleUnselected,
        picEnd = json.optStringOr("picEnd", "").takeIf { it.isNotEmpty() },
        picEndUnselected = json.optStringOr("picEndUnselected", "").takeIf { it.isNotEmpty() },
    )
}

// 根据 ProgressInfo 构造多节点进度信息
fun ProgressInfo.toMultiProgressInfo(
    title: String? = null,
    pointsOverride: Int? = null,
): MultiProgressInfo? {
    val hasNodeAssets =
        listOf(picMiddle, picMiddleUnselected, picEnd, picEndUnselected, picForward)
            .any { !it.isNullOrBlank() }
    if (!hasNodeAssets) return null

    val resolvedColor = colorProgress ?: colorProgressEnd
    return MultiProgressInfo(
        title = title?.trim().orEmpty(),
        progress = progress,
        color = resolvedColor,
        points = pointsOverride,
        picForward = picForward,
        picForwardWait = null,
        picForwardBox = null,
        picMiddle = picMiddle,
        picMiddleUnselected = picMiddleUnselected,
        picEnd = picEnd,
        picEndUnselected = picEndUnselected,
    )
}
