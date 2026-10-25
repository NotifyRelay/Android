package github.xzynine.superislandui.common

/** 文本度量结果。ascent 为负值（基线上方），descent 为正值。 */
internal data class TextMetrics(
    val ascent: Float,
    val descent: Float,
    val width: Float,
)

/**
 * 位图绘制端口。
 *
 * 尺寸、缩放与缓存策略由 commonMain 决策，绘制原语交由平台实现。
 */
internal interface ImageCodec {
    /** 解析颜色文本；无法解析返回 null。 */
    fun parseColorOrNull(text: String): Int?

    /** 度量文本的 ascent / descent / width（按给定字号与水平缩放）。 */
    fun measureText(
        text: String,
        fontSize: Float,
        textScaleX: Float,
    ): TextMetrics

    fun createBitmap(
        width: Int,
        height: Int,
    ): PlatformBitmap

    fun bitmapWidth(bitmap: PlatformBitmap): Int

    fun bitmapHeight(bitmap: PlatformBitmap): Int

    fun isRecycled(bitmap: PlatformBitmap): Boolean

    fun recycle(bitmap: PlatformBitmap)

    fun eraseToTransparent(bitmap: PlatformBitmap)

    fun scaleBitmap(
        bitmap: PlatformBitmap,
        width: Int,
        height: Int,
    ): PlatformBitmap

    /**
     * 在 [target] 上绘制文本；[album] 非空时按 [albumWidth] x [albumHeight] 缩放后绘制在左侧。
     */
    fun drawTextBitmap(
        target: PlatformBitmap,
        text: String,
        fontSize: Float,
        textScaleX: Float,
        baseline: Float,
        album: PlatformBitmap?,
        albumWidth: Int,
        albumHeight: Int,
        textX: Float,
    )

    /** 在 [target] 上绘制环形进度。 */
    fun drawProgressBitmap(
        target: PlatformBitmap,
        progress: Int,
        colorReachArgb: Int,
        colorUnReachArgb: Int,
        isCCW: Boolean,
    )
}

internal expect val imageCodec: ImageCodec
