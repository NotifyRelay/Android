package github.xzynine.superislandui.model.components

import org.json.JSONObject

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
fun parseProgressInfo(json: JSONObject): ProgressInfo =
    ProgressInfo(
        progress = json.optInt("progress", 0),
        colorProgress = json.optString("colorProgress", "").takeIf { it.isNotEmpty() },
        colorProgressEnd = json.optString("colorProgressEnd", "").takeIf { it.isNotEmpty() },
        picForward = json.optString("picForward", "").takeIf { it.isNotEmpty() },
        picMiddle = json.optString("picMiddle", "").takeIf { it.isNotEmpty() },
        picMiddleUnselected = json.optString("picMiddleUnselected", "").takeIf { it.isNotEmpty() },
        picEnd = json.optString("picEnd", "").takeIf { it.isNotEmpty() },
        picEndUnselected = json.optString("picEndUnselected", "").takeIf { it.isNotEmpty() },
        isCCW = json.optBoolean("isCCW", false),
        isAutoProgress = json.optBoolean("isAutoProgress", false),
    )
