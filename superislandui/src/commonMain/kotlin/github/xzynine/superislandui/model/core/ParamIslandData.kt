package github.xzynine.superislandui.model.core

import github.xzynine.superislandui.floating.smallisland.core.BigIslandArea
import github.xzynine.superislandui.floating.smallisland.core.parseBigIslandArea
import github.xzynine.superislandui.json.optArrayOrNull
import github.xzynine.superislandui.json.optObjectOrNull
import github.xzynine.superislandui.json.optStringOr
import github.xzynine.superislandui.json.optStringOrNull
import github.xzynine.superislandui.model.components.ProgressInfo
import github.xzynine.superislandui.model.components.parseProgressInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 解析 param_island 节点，提取小�?大岛摘要所需的基础文本与图标信息�?
 */
data class ParamIsland(
    val smallIslandArea: SmallIslandArea? = null,
    val bigIslandArea: BigIslandArea? = null,
)

data class SmallIslandArea(
    val primaryText: String? = null,
    val secondaryText: String? = null,
    val iconKey: String? = null,
    val progressInfo: ProgressInfo? = null,
)

fun parseParamIsland(
    json: JsonObject,
    picFunction: String? = null,
    aodPic: String? = null,
): ParamIsland {
    val small =
        json.optObjectOrNull("smallIslandArea")?.let { parseSmallIslandArea(it) }
            ?: json.optObjectOrNull("smallIsland")?.let { parseSmallIslandArea(it) }
    val bigJson = json.optObjectOrNull("bigIslandArea") ?: json.optObjectOrNull("bigIsland")
    val big = parseBigIslandArea(bigJson, picFunction, aodPic)
    return ParamIsland(smallIslandArea = small, bigIslandArea = big)
}

private fun parseSmallIslandArea(obj: JsonObject): SmallIslandArea {
    val primary = extractPrimaryText(obj)
    val secondary = extractSecondaryText(obj)
    val icon = extractIconKey(obj)
    val combine = obj.optObjectOrNull("combinePicInfo")
    val combineIcon =
        combine
            ?.optObjectOrNull("picInfo")
            ?.optStringOr("pic", "")
            ?.takeIf { it.isNotBlank() }
    val progressInfo =
        combine
            ?.optObjectOrNull("progressInfo")
            ?.let { parseProgressInfo(it) }
    val finalIcon = combineIcon ?: icon
    return SmallIslandArea(
        primaryText = primary,
        secondaryText = secondary,
        iconKey = finalIcon,
        progressInfo = progressInfo,
    )
}

private fun extractPrimaryText(obj: JsonObject): String? {
    val direct = firstString(obj, PRIMARY_TEXT_KEYS)
    if (direct != null) return direct
    for (nestedKey in NESTED_TEXT_KEYS) {
        obj.optObjectOrNull(nestedKey)?.let { nested ->
            val value = extractPrimaryText(nested)
            if (!value.isNullOrBlank()) return value
        }
    }
    val fromArray = firstFromArrays(obj, ARRAY_KEYS) { extractPrimaryText(it) }
    return fromArray?.takeIf { it.isNotBlank() }
}

private fun extractSecondaryText(obj: JsonObject): String? {
    val direct = firstString(obj, SECONDARY_TEXT_KEYS)
    if (direct != null) return direct
    for (nestedKey in NESTED_TEXT_KEYS) {
        obj.optObjectOrNull(nestedKey)?.let { nested ->
            val value = extractSecondaryText(nested)
            if (!value.isNullOrBlank()) return value
        }
    }
    val fromArray = firstFromArrays(obj, ARRAY_KEYS) { extractSecondaryText(it) }
    return fromArray?.takeIf { it.isNotBlank() }
}

private fun extractIconKey(obj: JsonObject): String? {
    val direct = firstString(obj, ICON_TEXT_KEYS)
    if (!direct.isNullOrBlank()) return direct
    for (nestedKey in ICON_OBJECT_KEYS) {
        obj.optObjectOrNull(nestedKey)?.let { nested ->
            val nestedValue = extractIconKey(nested)
            if (!nestedValue.isNullOrBlank()) return nestedValue
        }
    }
    val fromArray = firstFromArrays(obj, ARRAY_KEYS) { extractIconKey(it) }
    return fromArray?.takeIf { it.isNotBlank() }
}

private fun firstString(
    obj: JsonObject,
    keys: Array<String>,
): String? {
    for (key in keys) {
        val value = obj.optStringOrNull(key)
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun <T> firstFromArrays(
    obj: JsonObject,
    arrayKeys: Array<String>,
    extractor: (JsonObject) -> T?,
): T? {
    for (arrayKey in arrayKeys) {
        val arr = obj.optArrayOrNull(arrayKey) ?: continue
        val result = firstFromArray(arr, extractor)
        if (result != null) return result
    }
    return null
}

private fun <T> firstFromArray(
    array: JsonArray,
    extractor: (JsonObject) -> T?,
): T? {
    for (index in 0 until array.size) {
        val child = array.optObjectOrNull(index) ?: continue
        val value = extractor(child)
        if (value != null) return value
    }
    return null
}

private val PRIMARY_TEXT_KEYS =
    arrayOf(
        "title",
        "primaryText",
        "frontTitle",
        "mainText",
        "mainTitle",
        "largeText",
        "bigText",
        "text",
    )

private val SECONDARY_TEXT_KEYS =
    arrayOf(
        "content",
        "secondaryText",
        "subTitle",
        "subContent",
        "afterText",
        "tailText",
    )

private val ICON_TEXT_KEYS =
    arrayOf(
        "icon",
        "iconKey",
        "pic",
        "picContent",
        "picFunction",
        "picIcon",
        "picUrl",
        "src",
    )

private val ICON_OBJECT_KEYS =
    arrayOf(
        "iconInfo",
        "picInfo",
        "icon",
        "animIconInfo",
        "imageInfo",
        "imageIcon",
    )

private val NESTED_TEXT_KEYS =
    arrayOf(
        "textInfo",
        "leftTextInfo",
        "iconTextInfo",
        "imageTextInfoLeft",
        "imageTextInfoRight",
        "imageTextInfo",
        "smallTextInfo",
    )

private val ARRAY_KEYS =
    arrayOf(
        "components",
        "componentList",
        "items",
        "subItems",
    )
