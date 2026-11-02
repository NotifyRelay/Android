package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.json.optBooleanOr
import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optStringOr
import kotlinx.serialization.json.JsonObject

// 进度信息：定义进度条的样式和状�?
data class ProgressInfo(
    val progress: Int, // 当前进度百分�?
    val colorProgress: String? = null, // 进度条起始颜�?
    val colorProgressEnd: String? = null, // 进度条结束颜�?
    val picForward: String? = null, // 前进图形资源key
    val picMiddle: String? = null, // 中间节点选中状态资源key
    val picMiddleUnselected: String? = null, // 中间节点未选中状态资源key
    val picEnd: String? = null, // 目标点选中状态资源key
    val picEndUnselected: String? = null, // 目标点未选中状态资源key
    val isCCW: Boolean? = null, // 是否逆时针旋�?
    val isAutoProgress: Boolean? = null, // 是否自动更新进度
)

// 解析进度信息组件
fun parseProgressInfo(json: JsonObject): ProgressInfo =
    ProgressInfo(
        progress = json.optIntOr("progress", 0),
        colorProgress = json.optStringOr("colorProgress", "").takeIf { it.isNotEmpty() },
        colorProgressEnd = json.optStringOr("colorProgressEnd", "").takeIf { it.isNotEmpty() },
        picForward = json.optStringOr("picForward", "").takeIf { it.isNotEmpty() },
        picMiddle = json.optStringOr("picMiddle", "").takeIf { it.isNotEmpty() },
        picMiddleUnselected = json.optStringOr("picMiddleUnselected", "").takeIf { it.isNotEmpty() },
        picEnd = json.optStringOr("picEnd", "").takeIf { it.isNotEmpty() },
        picEndUnselected = json.optStringOr("picEndUnselected", "").takeIf { it.isNotEmpty() },
        isCCW = json.optBooleanOr("isCCW", false),
        isAutoProgress = json.optBooleanOr("isAutoProgress", false),
    )
