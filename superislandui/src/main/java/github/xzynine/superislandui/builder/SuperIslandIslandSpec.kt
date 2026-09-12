package github.xzynine.superislandui.builder

import github.xzynine.superislandui.model.components.TimerInfo
import org.json.JSONObject

/**
 * `param_island`（岛数据）类型化模型。
 *
 * 覆盖大岛（A/B 区）与小岛内容，配合 [SuperIslandParamBuilder] 即可用类型化模型构建整棵
 * `param_island`，无需手写 JSON。空值字段一律不写出。
 */

/** 拖拽分享数据 */
data class ShareDataSpec(
    val pic: String? = null,
    val title: String? = null,
    val content: String? = null,
    val shareContent: String? = null,
)

/** 图标/图片信息（大岛、小岛通用） */
data class IslandPicInfoSpec(
    val type: Int = 1,
    val pic: String? = null,
    val picDark: String? = null,
)

/** 文本信息（大岛 A/B 区） */
data class IslandTextInfoSpec(
    val frontTitle: String? = null,
    val title: String? = null,
    val content: String? = null,
    val narrowFont: Boolean = false,
    val showHighlightColor: Boolean = false,
)

/** 图文组件（imageTextInfoLeft / imageTextInfoRight） */
data class ImageTextInfoSpec(
    val type: Int,
    val picInfo: IslandPicInfoSpec? = null,
    val textInfo: IslandTextInfoSpec? = null,
)

/** 环形进度信息（进度文本/图标组合组件用） */
data class IslandProgressInfoSpec(
    val progress: Int,
    val colorReach: String? = null,
    val colorUnReach: String? = null,
    val isCCW: Boolean = false,
)

/** 定宽数字文本组件 */
data class FixedWidthDigitInfoSpec(
    val digit: String,
    val content: String? = null,
    val showHighlightColor: Boolean = false,
)

/** 等宽数字文本组件 */
data class SameWidthDigitInfoSpec(
    val digit: String? = null,
    val timerInfo: TimerInfo? = null,
    val content: String? = null,
    val showHighlightColor: Boolean = false,
)

/** 进度文本组件 */
data class ProgressTextInfoSpec(
    val textInfo: IslandTextInfoSpec? = null,
    val progressInfo: IslandProgressInfoSpec,
    val picInfo: IslandPicInfoSpec? = null,
)

/** 图标组合组件 */
data class CombinePicInfoSpec(
    val picInfo: IslandPicInfoSpec,
    val progressInfo: IslandProgressInfoSpec,
    val smallPicInfo: IslandPicInfoSpec? = null,
)

/** 大岛内容 */
data class BigIslandAreaSpec(
    val imageTextInfoLeft: ImageTextInfoSpec? = null,
    val imageTextInfoRight: ImageTextInfoSpec? = null,
    val textInfo: IslandTextInfoSpec? = null,
    val picInfo: IslandPicInfoSpec? = null,
    val fixedWidthDigitInfo: FixedWidthDigitInfoSpec? = null,
    val sameWidthDigitInfo: SameWidthDigitInfoSpec? = null,
    val progressTextInfo: ProgressTextInfoSpec? = null,
    val combinePicInfo: CombinePicInfoSpec? = null,
)

/** 小岛内容 */
data class SmallIslandAreaSpec(
    val picInfo: IslandPicInfoSpec? = null,
    val combinePicInfo: CombinePicInfoSpec? = null,
    val imageTextInfoRight: ImageTextInfoSpec? = null,
)

/** 岛数据（param_island） */
data class ParamIslandSpec(
    val islandProperty: Int? = null,
    val islandOrder: Boolean? = null,
    val islandTimeout: Int? = null,
    val dismissIsland: Boolean? = null,
    val highlightColor: String? = null,
    val bigIslandArea: BigIslandAreaSpec? = null,
    val smallIslandArea: SmallIslandAreaSpec? = null,
    val shareData: ShareDataSpec? = null,
)

// ---- 序列化 ----

fun ShareDataSpec.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("pic", pic)
        putOpt("title", title)
        putOpt("content", content)
        putOpt("shareContent", shareContent)
    }

fun IslandPicInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        put("type", type)
        putOpt("pic", pic)
        putOpt("picDark", picDark)
    }

fun IslandTextInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("frontTitle", frontTitle)
        putOpt("title", title)
        putOpt("content", content)
        if (narrowFont) put("narrowFont", true)
        if (showHighlightColor) put("showHighlightColor", true)
    }

fun ImageTextInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        put("type", type)
        picInfo?.let { put("picInfo", it.toJson()) }
        textInfo?.let { put("textInfo", it.toJson()) }
    }

fun IslandProgressInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        put("progress", progress)
        putOpt("colorReach", colorReach)
        putOpt("colorUnReach", colorUnReach)
        if (isCCW) put("isCCW", true)
    }

fun FixedWidthDigitInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        put("digit", digit)
        putOpt("content", content)
        if (showHighlightColor) put("showHighlightColor", true)
    }

fun SameWidthDigitInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        putOpt("digit", digit)
        timerInfo?.let { put("timerInfo", it.toJson()) }
        putOpt("content", content)
        if (showHighlightColor) put("showHighlightColor", true)
    }

fun ProgressTextInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        textInfo?.let { put("textInfo", it.toJson()) }
        put("progressInfo", progressInfo.toJson())
        picInfo?.let { put("picInfo", it.toJson()) }
    }

fun CombinePicInfoSpec.toJson(): JSONObject =
    JSONObject().apply {
        put("picInfo", picInfo.toJson())
        put("progressInfo", progressInfo.toJson())
        smallPicInfo?.let { put("smallPicInfo", it.toJson()) }
    }

fun BigIslandAreaSpec.toJson(): JSONObject =
    JSONObject().apply {
        imageTextInfoLeft?.let { put("imageTextInfoLeft", it.toJson()) }
        imageTextInfoRight?.let { put("imageTextInfoRight", it.toJson()) }
        textInfo?.let { put("textInfo", it.toJson()) }
        picInfo?.let { put("picInfo", it.toJson()) }
        fixedWidthDigitInfo?.let { put("fixedWidthDigitInfo", it.toJson()) }
        sameWidthDigitInfo?.let { put("sameWidthDigitInfo", it.toJson()) }
        progressTextInfo?.let { put("progressTextInfo", it.toJson()) }
        combinePicInfo?.let { put("combinePicInfo", it.toJson()) }
    }

fun SmallIslandAreaSpec.toJson(): JSONObject =
    JSONObject().apply {
        picInfo?.let { put("picInfo", it.toJson()) }
        combinePicInfo?.let { put("combinePicInfo", it.toJson()) }
        imageTextInfoRight?.let { put("imageTextInfoRight", it.toJson()) }
    }

fun ParamIslandSpec.toJson(): JSONObject =
    JSONObject().apply {
        islandProperty?.let { put("islandProperty", it) }
        islandOrder?.let { put("islandOrder", it) }
        islandTimeout?.let { put("islandTimeout", it) }
        dismissIsland?.let { put("dismissIsland", it) }
        putOpt("highlightColor", highlightColor)
        bigIslandArea?.let { put("bigIslandArea", it.toJson()) }
        smallIslandArea?.let { put("smallIslandArea", it.toJson()) }
        shareData?.let { put("shareData", it.toJson()) }
    }
