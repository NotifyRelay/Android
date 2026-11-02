package github.xzynine.superislandui.model.components

import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optLongOr
import kotlinx.serialization.json.JsonObject

// 定时器信息：用于计时功能，支持倒计时、正计时�?
data class TimerInfo(
    val timerType: Int, // 计时类型�?2倒计时暂停，-1倒计时开始，0默认�?正计时开始，2正计时暂�?
    val timerWhen: Long, // 计时起点时间戳（毫秒�?
    val timerTotal: Long, // 计时总进�?
    val timerSystemCurrent: Long, // 计时系统当前时间戳（毫秒�?
)

// 解析定时器信息组�?
fun parseTimerInfo(json: JsonObject): TimerInfo =
    TimerInfo(
        timerType = json.optIntOr("timerType", 0),
        timerWhen = json.optLongOr("timerWhen", 0),
        timerTotal = json.optLongOr("timerTotal", 0),
        timerSystemCurrent = json.optLongOr("timerSystemCurrent", 0),
    )
