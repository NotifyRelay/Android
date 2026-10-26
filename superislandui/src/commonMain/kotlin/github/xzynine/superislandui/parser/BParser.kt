package github.xzynine.superislandui.parser

import github.xzynine.superislandui.floating.smallisland.right.BComponent
import github.xzynine.superislandui.floating.smallisland.right.BEmpty
import github.xzynine.superislandui.floating.smallisland.right.BFixedWidthDigitInfo
import github.xzynine.superislandui.floating.smallisland.right.BImageText2
import github.xzynine.superislandui.floating.smallisland.right.BImageText3
import github.xzynine.superislandui.floating.smallisland.right.BImageText6
import github.xzynine.superislandui.floating.smallisland.right.BPicInfo
import github.xzynine.superislandui.floating.smallisland.right.BProgressTextInfo
import github.xzynine.superislandui.floating.smallisland.right.BSameWidthDigitInfo
import github.xzynine.superislandui.floating.smallisland.right.BTextInfo
import github.xzynine.superislandui.json.optBooleanOr
import github.xzynine.superislandui.json.optIntOr
import github.xzynine.superislandui.json.optLongOr
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.model.components.TimerInfo
import kotlinx.serialization.json.JsonObject
import notifyrelay.base.log.Logger

/**
 * 解析 B区（优先识别 imageTextInfoRight 的 type，其次识别 text/digit/progress/pic）。
 * 若均未命中，返回 BEmpty。
 * @param bigIsland 大岛区域 JSON
 * @param picFunction 功能图标键（来自 highlightInfo.picFunction）
 * @param aodPic AOD 图片键
 */
fun parseBComponent(
    bigIsland: JsonObject?,
    picFunction: String? = null,
    aodPic: String? = null,
): BComponent? {
    val right = bigIsland?.optObjectOrNull("imageTextInfoRight")
    if (right != null) {
        val type = right.optIntOr("type", 0)
        val textInfo = right.optObjectOrNull("textInfo")
        val titleText = textInfo?.optStringOr("title", "")?.takeIf { it.isNotBlank() }
        val contentText = textInfo?.optStringOr("content", "")?.takeIf { it.isNotBlank() }
        val frontTitle = textInfo?.optStringOr("frontTitle", "")?.takeIf { it.isNotBlank() }
        val narrowFont = textInfo?.optBooleanOr("narrowFont", false) ?: false
        val showHighlightColor = textInfo?.optBooleanOr("showHighlightColor", false) ?: false

        val picInfo = right.optObjectOrNull("picInfo")
        val picTypeOk = (picInfo?.optIntOr("type", 0) == 1)
        val picRaw = picInfo?.optStringOr("pic", "")?.takeIf { it.isNotBlank() }
        val picKey: String? = resolvePicKey(picRaw, picFunction, aodPic)

        return when (type) {
            2 -> {
                val title = titleText ?: return BEmpty // 必传
                if (!picTypeOk || picKey == null) return BEmpty // 必传
                BImageText2(
                    frontTitle = frontTitle,
                    title = title,
                    content = contentText,
                    narrowFont = narrowFont,
                    showHighlightColor = showHighlightColor,
                    picKey = picKey,
                )
            }
            3 -> {
                val title = titleText ?: return BEmpty // 必传
                if (!picTypeOk || picKey == null) return BEmpty // 必传
                BImageText3(
                    title = title,
                    narrowFont = narrowFont,
                    showHighlightColor = showHighlightColor,
                    picKey = picKey,
                )
            }
            // 组件4为系统侧（充电/省电）专用，不走通知数据；本项目不复刻，直接视为空
            4 -> BEmpty
            6 -> {
                // 图文组件6：必传内容 = 数字（大字 textInfo.title）+ 图片（数字最多 3 个）
                val title = titleText ?: return BEmpty // 必传
                // 观察日志：计划对 title 落地「纯数字且长度<=3」硬校验，但需先统计真实数据中
                // type=6 的 title 实际取值，确认不会出现「带符号/百分号」的合法值（如 "100%"）被误杀。
                // 确认后再放开下方约束；当前仅记录，不改变解析行为。
                Logger.d("BParser", "imageText6 title 观测值: \"$title\"（拟校验：纯数字 && 长度<=3）")
                // 组件6要求静态图标：picInfo.type==4 且 picKey 必传
                val staticIcon = (picInfo?.optIntOr("type", 0) == 4)
                if (!staticIcon || picKey == null) return BEmpty
                BImageText6(
                    title = title,
                    narrowFont = narrowFont,
                    showHighlightColor = showHighlightColor,
                    picKey = picKey,
                )
            }
            else -> BEmpty
        }
    }

    bigIsland?.optObjectOrNull("textInfo")?.let { ti ->
        val title = ti.optStringOr("title", "").takeIf { it.isNotBlank() } ?: return BEmpty
        val frontTitle = ti.optStringOr("frontTitle", "").takeIf { it.isNotBlank() }
        val content = ti.optStringOr("content", "").takeIf { it.isNotBlank() }
        val narrowFont = ti.optBooleanOr("narrowFont", false)
        val showHighlightColor = ti.optBooleanOr("showHighlightColor", false)
        return BTextInfo(
            frontTitle = frontTitle,
            title = title,
            content = content,
            narrowFont = narrowFont,
            showHighlightColor = showHighlightColor,
        )
    }

    bigIsland?.optObjectOrNull("fixedWidthDigitInfo")?.let { fi ->
        val digit = fi.optStringOr("digit", "").takeIf { it.isNotBlank() } ?: return@let
        val content = fi.optStringOr("content", "").takeIf { it.isNotBlank() }
        val showHighlightColor = fi.optBooleanOr("showHighlightColor", false)
        return BFixedWidthDigitInfo(
            digit = digit,
            content = content,
            showHighlightColor = showHighlightColor,
        )
    }

    bigIsland?.optObjectOrNull("sameWidthDigitInfo")?.let { si ->
        // 先尝试解析 timerInfo（兼容旧 key "timer"）
        val timerObj = si.optObjectOrNull("timerInfo") ?: si.optObjectOrNull("timer")
        val timer =
            timerObj?.let { to ->
                val typeExists = "timerType" in to
                if (!typeExists) {
                    null
                } else {
                    val timerType = to.optIntOr("timerType")
                    val timerWhen = to.optLongOr("timerWhen", 0)
                    val timerTotal = to.optLongOr("timerTotal", 0)
                    val timerSystemCurrent = to.optLongOr("timerSystemCurrent", 0)
                    TimerInfo(
                        timerType = timerType,
                        timerWhen = timerWhen,
                        timerTotal = timerTotal,
                        timerSystemCurrent = timerSystemCurrent,
                    )
                }
            }

        // 若无 timerInfo 或不合法，则回退到 digit
        val digit = si.optStringOr("digit", "").takeIf { it.isNotBlank() }

        // 二选一：timer 或 digit 至少一个存在
        if (timer == null && digit == null) return@let

        val content = si.optStringOr("content", "").takeIf { it.isNotBlank() }
        val showHighlightColor = si.optBooleanOr("showHighlightColor", false)
        return BSameWidthDigitInfo(
            digit = digit,
            timer = timer,
            content = content,
            showHighlightColor = showHighlightColor,
        )
    }

    bigIsland?.optObjectOrNull("progressTextInfo")?.let { root ->
        // textInfo
        val ti = root.optObjectOrNull("textInfo")
        val frontTitle = ti?.optStringOr("frontTitle", "")?.takeIf { it.isNotBlank() }
        val title = ti?.optStringOr("title", "")?.takeIf { it.isNotBlank() }
        val content = ti?.optStringOr("content", "")?.takeIf { it.isNotBlank() }
        val narrowFont = ti?.optBooleanOr("narrowFont", false) ?: false
        val showHighlightColor = ti?.optBooleanOr("showHighlightColor", false) ?: false

        // progressInfo (required)
        val pInfo = root.optObjectOrNull("progressInfo") ?: return@let
        val progress = pInfo.optIntOr("progress", -1)
        if (progress !in 0..100) return@let
        val colorReach = pInfo.optStringOr("colorReach", "").takeIf { it.isNotBlank() }
        val colorUnReach = pInfo.optStringOr("colorUnReach", "").takeIf { it.isNotBlank() }
        val isCCW = pInfo.optBooleanOr("isCCW", false)

        // picInfo (optional but if present must be type=1 & pic required)
        val picObj = root.optObjectOrNull("picInfo")
        val picRaw =
            picObj?.let { po ->
                val typeOk = po.optIntOr("type", 0) == 1
                val key = po.optStringOr("pic", "").takeIf { it.isNotBlank() }
                if (typeOk) key else null
            }
        val picKey: String? = resolvePicKey(picRaw, picFunction, aodPic)

        return BProgressTextInfo(
            frontTitle = frontTitle,
            title = title,
            content = content,
            narrowFont = narrowFont,
            showHighlightColor = showHighlightColor,
            progress = progress,
            colorReach = colorReach,
            colorUnReach = colorUnReach,
            isCCW = isCCW,
            picKey = picKey,
        )
    }

    bigIsland?.optObjectOrNull("picInfo")?.let { pi ->
        val type = pi.optIntOr("type", -1)
        if (type != 1 && type != 4) return@let
        val picRaw = pi.optStringOr("pic", "").takeIf { it.isNotBlank() } ?: return@let
        // 图标选择优先级：
        // 1. pic 字段（如果是 miui.focus.pic_ 前缀）
        // 2. picFunction（来自 highlightInfo.picFunction）
        // 3. aodPic
        val picKey: String? = resolvePicKey(picRaw, picFunction, aodPic, default = picRaw)
        return picKey?.let { BPicInfo(picKey = it, type = type) }
    }

    return BEmpty
}

/**
 * 解析 picKey 的辅助函数
 * 图标选择优先级：
 * 1. picRaw 字段（如果是 miui.focus.pic_ 前缀）
 * 2. picFunction（来自 highlightInfo.picFunction）
 * 3. aodPic
 * @param default 如果没有匹配的前缀，返回的默认值
 */
private fun resolvePicKey(
    picRaw: String?,
    picFunction: String?,
    aodPic: String?,
    default: String? = null,
): String? =
    when {
        picRaw != null && picRaw.startsWith("miui.focus.pic_") -> picRaw
        picFunction != null && picFunction.startsWith("miui.focus.pic_") -> picFunction
        aodPic != null && aodPic.startsWith("miui.focus.pic_") -> aodPic
        else -> default
    }
