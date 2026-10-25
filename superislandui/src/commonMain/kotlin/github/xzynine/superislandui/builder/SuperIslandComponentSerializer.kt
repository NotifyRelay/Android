package github.xzynine.superislandui.builder

import github.xzynine.superislandui.json.putOpt
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 类型化组件模型 → 规范 JSON 的序列化。
 *
 * 与各模型的 `parseXxx(...)` 对称，便于 [SuperIslandParamBuilder] 直接接受类型化组件，
 * 从而在**编译期**由类型保证组件结构正确（减少字符串魔法）。空值字段一律不写出。
 */

fun TimerInfo.toJson(): JsonObject =
    buildJsonObject {
        put("timerType", timerType)
        put("timerWhen", timerWhen)
        put("timerTotal", timerTotal)
        put("timerSystemCurrent", timerSystemCurrent)
    }

fun ActionInfo.toJson(): JsonObject =
    buildJsonObject {
        putOpt("action", action)
        putOpt("actionIcon", actionIcon)
        putOpt("actionIconDark", actionIconDark)
        putOpt("actionTitle", actionTitle)
        putOpt("actionTitleColor", actionTitleColor)
        putOpt("actionTitleColorDark", actionTitleColorDark)
        putOpt("actionBgColor", actionBgColor)
        putOpt("actionBgColorDark", actionBgColorDark)
        putOpt("actionIntentType", actionIntentType)
        putOpt("actionIntent", actionIntent)
        putOpt("clickWithCollapse", clickWithCollapse)
        putOpt("type", type)
        putOpt("progressInfo", progressInfo?.toJson())
    }

fun List<ActionInfo>.toJsonArray(): JsonArray = JsonArray(map { it.toJson() })

fun ProgressInfo.toJson(): JsonObject =
    buildJsonObject {
        put("progress", progress)
        putOpt("colorProgress", colorProgress)
        putOpt("colorProgressEnd", colorProgressEnd)
        putOpt("picForward", picForward)
        putOpt("picMiddle", picMiddle)
        putOpt("picMiddleUnselected", picMiddleUnselected)
        putOpt("picEnd", picEnd)
        putOpt("picEndUnselected", picEndUnselected)
        putOpt("isCCW", isCCW)
        putOpt("isAutoProgress", isAutoProgress)
    }

fun MultiProgressInfo.toJson(): JsonObject =
    buildJsonObject {
        put("title", title)
        put("progress", progress)
        putOpt("color", color)
        putOpt("points", points)
        putOpt("picForward", picForward)
        putOpt("picForwardWait", picForwardWait)
        putOpt("picForwardBox", picForwardBox)
        putOpt("picMiddle", picMiddle)
        putOpt("picMiddleUnselected", picMiddleUnselected)
        putOpt("picEnd", picEnd)
        putOpt("picEndUnselected", picEndUnselected)
    }

fun BaseInfo.toJson(): JsonObject =
    buildJsonObject {
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
        putOpt("showDivider", showDivider)
        putOpt("showContentDivider", showContentDivider)
    }

fun ChatInfo.toJson(): JsonObject =
    buildJsonObject {
        putOpt("picProfile", picProfile)
        putOpt("picProfileDark", picProfileDark)
        putOpt("appIconPkg", appIconPkg)
        putOpt("title", title)
        putOpt("content", content)
        putOpt("timerInfo", timerInfo?.toJson())
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
    }

fun HighlightInfo.toJson(): JsonObject =
    buildJsonObject {
        putOpt("title", title)
        putOpt("timerInfo", timerInfo?.toJson())
        putOpt("content", content)
        putOpt("picFunction", picFunction)
        putOpt("picFunctionDark", picFunctionDark)
        putOpt("subContent", subContent)
        putOpt("type", type)
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
        putOpt("colorSubContent", colorSubContent)
        putOpt("colorSubContentDark", colorSubContentDark)
        putOpt("bigImageLeft", bigImageLeft)
        putOpt("bigImageRight", bigImageRight)
        if (iconOnly) put("iconOnly", true)
    }

fun PicInfo.toJson(): JsonObject =
    buildJsonObject {
        put("type", type)
        putOpt("pic", pic)
        putOpt("picDark", picDark)
        putOpt("actionInfo", actionInfo?.toJson())
        putOpt("title", title)
        putOpt("colorTitle", colorTitle)
    }

fun HintInfo.toJson(): JsonObject =
    buildJsonObject {
        put("type", type)
        putOpt("title", title)
        putOpt("timerInfo", timerInfo?.toJson())
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
        putOpt("actionInfo", actionInfo?.toJson())
    }

fun AnimTextInfo.toJson(): JsonObject =
    buildJsonObject {
        put(
            "animIconInfo",
            buildJsonObject {
                put("src", icon.src)
                putOpt("srcDark", icon.srcDark)
            },
        )
        putOpt("title", title)
        putOpt("content", content)
        putOpt("timerInfo", timerInfo?.toJson())
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
    }

fun TextButton.toJson(): JsonObject =
    buildJsonObject {
        put("actions", actions.toJsonArray())
    }

fun IconTextInfo.toJson(): JsonObject =
    buildJsonObject {
        putOpt(
            "animIconInfo",
            animIconInfo?.let { info ->
                buildJsonObject {
                    put("type", info.type)
                    putOpt("src", info.src)
                    putOpt("srcDark", info.srcDark)
                }
            },
        )
        putOpt("title", title)
        putOpt("content", content)
        putOpt("subContent", subContent)
        putOpt("colorTitle", colorTitle)
        putOpt("colorTitleDark", colorTitleDark)
        putOpt("colorContent", colorContent)
        putOpt("colorContentDark", colorContentDark)
    }

fun CoverInfo.toJson(): JsonObject =
    buildJsonObject {
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

fun HighlightInfoV3.toJson(): JsonObject =
    buildJsonObject {
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
        putOpt("actionInfo", actionInfo?.toJson())
    }
