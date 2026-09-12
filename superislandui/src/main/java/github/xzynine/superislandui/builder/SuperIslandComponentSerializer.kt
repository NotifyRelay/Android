package github.xzynine.superislandui.builder

import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.components.AnimTextInfo
import github.xzynine.superislandui.model.components.MultiProgressInfo
import github.xzynine.superislandui.model.components.ProgressInfo
import github.xzynine.superislandui.model.components.TextButton
import github.xzynine.superislandui.model.components.TimerInfo
import github.xzynine.superislandui.model.templates.BaseInfo
import github.xzynine.superislandui.model.templates.ChatInfo
import github.xzynine.superislandui.model.templates.CoverInfo
import github.xzynine.superislandui.model.templates.HighlightInfo
import github.xzynine.superislandui.model.templates.HighlightInfoV3
import github.xzynine.superislandui.model.templates.HintInfo
import github.xzynine.superislandui.model.templates.IconTextInfo
import github.xzynine.superislandui.model.templates.PicInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 类型化组件模型 → 规范 JSON 的序列化。
 *
 * 与各模型的 `parseXxx(...)` 对称，便于 [SuperIslandParamBuilder] 直接接受类型化组件，
 * 从而在**编译期**由类型保证组件结构正确（减少字符串魔法）。空值字段一律不写出。
 */

fun TimerInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("timerType", timerType)
        put("timerWhen", timerWhen)
        put("timerTotal", timerTotal)
        put("timerSystemCurrent", timerSystemCurrent)
    }

fun ActionInfo.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("action", action)
        putOpt("actionIcon", actionIcon)
        putOpt("actionIconDark", actionIconDark)
        putOpt("actionTitle", actionTitle)
        putOpt("actionTitleColor", actionTitleColor)
        putOpt("actionTitleColorDark", actionTitleColorDark)
        putOpt("actionBgColor", actionBgColor)
        putOpt("actionBgColorDark", actionBgColorDark)
        actionIntentType?.let { put("actionIntentType", it) }
        putOpt("actionIntent", actionIntent)
        clickWithCollapse?.let { put("clickWithCollapse", it) }
        type?.let { put("type", it) }
        progressInfo?.let { put("progressInfo", it.toJson()) }
    }

fun List<ActionInfo>.toJsonArray(): JSONArray =
    JSONArray().apply {
        forEach { put(it.toJson()) }
    }

fun ProgressInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("progress", progress)
        putOpt("colorProgress", colorProgress)
        putOpt("colorProgressEnd", colorProgressEnd)
        putOpt("picForward", picForward)
        putOpt("picMiddle", picMiddle)
        putOpt("picMiddleUnselected", picMiddleUnselected)
        putOpt("picEnd", picEnd)
        putOpt("picEndUnselected", picEndUnselected)
        isCCW?.let { put("isCCW", it) }
        isAutoProgress?.let { put("isAutoProgress", it) }
    }

fun MultiProgressInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("title", title)
        put("progress", progress)
        putOpt("color", color)
        points?.let { put("points", it) }
        putOpt("picForward", picForward)
        putOpt("picForwardWait", picForwardWait)
        putOpt("picForwardBox", picForwardBox)
        putOpt("picMiddle", picMiddle)
        putOpt("picMiddleUnselected", picMiddleUnselected)
        putOpt("picEnd", picEnd)
        putOpt("picEndUnselected", picEndUnselected)
    }

fun BaseInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("type", type)
        putOpt("title", title)
        putOpt("subTitle", subTitle)
        putOpt("extraTitle", extraTitle)
        putOpt("specialTitle", specialTitle)
        putOpt("content", content)
        putOpt("subContent", subContent)
        putOpt("picFunction", picFunction)
        putOpt("picFunctionDark", picFunctionDark)
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorSubTitle", colorSubTitle)
        putOpt("colorSubTitleDark", colorSubTitleDark)
        putOpt("colorExtraTitle", colorExtraTitle)
        putOpt("colorExtraTitleDark", colorExtraTitleDark)
        putOpt("colorSpecialTitle", colorSpecialTitle)
        putOpt("colorSpecialTitleDark", colorSpecialTitleDark)
        putOpt("colorSpecialBg", colorSpecialBg)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
        putOpt("colorSubContent", colorSubContent)
        putOpt("colorSubContentDark", colorSubContentDark)
        showDivider?.let { put("showDivider", it) }
        showContentDivider?.let { put("showContentDivider", it) }
    }

fun ChatInfo.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("picProfile", picProfile)
        putOpt("picProfileDark", picProfileDark)
        putOpt("appIconPkg", appIconPkg)
        putOpt("title", title)
        putOpt("content", content)
        timerInfo?.let { put("timerInfo", it.toJson()) }
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
    }

fun HighlightInfo.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("title", title)
        timerInfo?.let { put("timerInfo", it.toJson()) }
        putOpt("content", content)
        putOpt("picFunction", picFunction)
        putOpt("picFunctionDark", picFunctionDark)
        putOpt("subContent", subContent)
        type?.let { put("type", it) }
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
        putOpt("colorSubContent", colorSubContent)
        putOpt("colorSubContentDark", colorSubContentDark)
    }

fun PicInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("type", type)
        putOpt("pic", pic)
        putOpt("picDark", picDark)
        actionInfo?.let { put("actionInfo", it.toJson()) }
        putOpt("title", title)
        putOpt("colorTitle", colorTitle)
    }

fun HintInfo.toJson(): JSONObject =
    JSONObject().apply {
        put("type", type)
        putOpt("title", title)
        timerInfo?.let { put("timerInfo", it.toJson()) }
        putOpt("subTitle", subTitle)
        putOpt("content", content)
        putOpt("subContent", subContent)
        putOpt("picContent", picContent)
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorSubTitle", colorSubTitle)
        putOpt("colorSubTitleDark", colorSubTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
        putOpt("colorSubContent", colorSubContent)
        putOpt("colorSubContentDark", colorSubContentDark)
        putOpt("colorContentBg", colorContentBg)
        actionInfo?.let { put("actionInfo", it.toJson()) }
    }

fun AnimTextInfo.toJson(): JSONObject =
    JSONObject().apply {
        put(
            "animIconInfo",
            JSONObject().apply {
                put("src", icon.src)
                putOpt("srcDark", icon.srcDark)
            },
        )
        putOpt("title", title)
        putOpt("content", content)
        timerInfo?.let { put("timerInfo", it.toJson()) }
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
    }

fun TextButton.toJson(): JSONObject =
    JSONObject().apply {
        put("actions", actions.toJsonArray())
    }

fun IconTextInfo.toJson(): JSONObject =
    JSONObject().apply {
        animIconInfo?.let { info ->
            put(
                "animIconInfo",
                JSONObject().apply {
                    put("type", info.type)
                    putOpt("src", info.src)
                    putOpt("srcDark", info.srcDark)
                },
            )
        }
        putOpt("title", title)
        putOpt("content", content)
        putOpt("subContent", subContent)
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
    }

fun CoverInfo.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("picCover", picCover)
        putOpt("title", title)
        putOpt("content", content)
        putOpt("subContent", subContent)
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
        putOpt("colorSubContent", colorSubContent)
        putOpt("colorSubContentDark", colorSubContentDark)
    }

fun HighlightInfoV3.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("primaryText", primaryText)
        putOpt("secondaryText", secondaryText)
        if (showSecondaryLine) put("showSecondaryLine", true)
        putOpt("highLightText", highLightText)
        putOpt("primaryColor", primaryColor)
        putOpt("secondaryColor", secondaryColor)
        putOpt("highLightTextColor", highLightTextColor)
        putOpt("highLightbgColor", highLightbgColor)
        putOpt("primaryColorDark", primaryColorDark)
        putOpt("secondaryColorDark", secondaryColorDark)
        putOpt("highLightTextColorDark", highLightTextColorDark)
        putOpt("highLightbgColorDark", highLightbgColorDark)
        actionInfo?.let { put("actionInfo", it.toJson()) }
    }
