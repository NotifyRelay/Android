package github.xzynine.superislandui.common

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import notifyrelay.base.log.Logger
import notifyrelay.base.util.measureTime

/**
 * 位图工具类，用于处理文本到位图的转换等操作。
 *
 * 尺寸、水平缩放与缓存池策略在公共源集决策，绘制与度量经 [ImageCodec] 下移到平台。
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"

    private const val DEFAULT_FONT_SIZE = 40f
    private const val FONT_SIZE_SCALE = 2f
    private const val EQUIVALENT_LENGTH_BASE = 2f
    private const val EQUIVALENT_LENGTH_STEP = 0.1f
    private const val MIN_FONT_SCALE = 0.5f
    private const val MIN_TEXT_SCALE_X = 0.5f

    private const val MAX_BITMAP_SIZE = 500
    private const val MAX_TEXT_LENGTH_FOR_ALBUM = 6

    private const val HEIGHT_PADDING = 10
    private const val ALBUM_HEIGHT_PADDING = 10
    private const val LEFT_PADDING = 10f
    private const val ALBUM_DRAW_X = 5f
    private const val ALBUM_DRAW_Y = 5f
    private const val ALBUM_TEXT_SPACING = 15f
    private const val ALBUM_FIXED_PADDING = 20
    private const val ALBUM_FIXED_PADDING_F = 20f

    private const val PROGRESS_SIZE = 100

    private const val MAX_CACHE_SIZE = 10

    private const val DEFAULT_UNREACH_COLOR = "#888888"
    private const val DEFAULT_REACH_COLOR = "#00FF00"
    private const val FALLBACK_UNREACH_ARGB = 0xFF888888.toInt()
    private const val FALLBACK_REACH_ARGB = 0xFF00FF00.toInt()

    private val cacheLock = SynchronizedObject()

    // Bitmap 缓存池，支持不同尺寸的 bitmap 复用
    private val bitmapCache = mutableMapOf<Int, MutableList<PlatformBitmap>>()

    /**
     * 将文本转换为位图
     * @param text 要转换的文本
     * @param forceFontSize 强制字体大小，null 表示使用默认大小
     * @param albumBitmap 专辑图 bitmap，可为 null
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(
        text: String,
        forceFontSize: Float? = null,
        albumBitmap: PlatformBitmap? = null,
    ): PlatformBitmap? =
        measureTime(TAG, "textToBitmap") {
            try {
                if (text.isBlank()) {
                    Logger.w(TAG, "文本为空，无法生成位图")
                    return@measureTime null
                }

                val fontSize = (forceFontSize ?: DEFAULT_FONT_SIZE) * FONT_SIZE_SCALE

                // 字符权重缓存
                val charWeightCache = mutableMapOf<Char, Float>()

                fun calculateEquivalentLength(target: String): Float {
                    var length = 0f
                    for (char in target) {
                        length += charWeightCache.getOrPut(char) { if (char in 'a'..'z') 0.5f else 1f }
                    }
                    return length
                }

                val textEquivalentLength = calculateEquivalentLength(text)
                var currentFontSize = fontSize
                if (textEquivalentLength > EQUIVALENT_LENGTH_BASE) {
                    val scaleFactor = 1.0f - (textEquivalentLength - EQUIVALENT_LENGTH_BASE) * EQUIVALENT_LENGTH_STEP
                    currentFontSize = fontSize * scaleFactor.coerceAtLeast(MIN_FONT_SCALE)
                }

                val metrics = imageCodec.measureText(text, currentFontSize, 1f)
                val baseline = -metrics.ascent
                var textWidth = metrics.width
                val height = (baseline + metrics.descent + HEIGHT_PADDING).toInt()

                // 计算真实的专辑图宽度
                var scaledAlbumWidth = 0
                val albumHeight = height - ALBUM_HEIGHT_PADDING
                if (albumBitmap != null) {
                    val albumSourceHeight = imageCodec.bitmapHeight(albumBitmap)
                    if (albumHeight > 0 && albumSourceHeight > 0) {
                        val scaleFactor = albumHeight.toFloat() / albumSourceHeight
                        scaledAlbumWidth = (imageCodec.bitmapWidth(albumBitmap) * scaleFactor).toInt()
                    }
                }

                val widthWithAlbum = (textWidth + scaledAlbumWidth + ALBUM_FIXED_PADDING).toInt()
                val widthWithoutAlbum = (textWidth + LEFT_PADDING).toInt()

                if (widthWithoutAlbum <= 0 || height <= 0) {
                    Logger.w(TAG, "文本位图尺寸无效，width=$widthWithoutAlbum, height=$height")
                    return@measureTime null
                }

                val shouldAddAlbum =
                    albumBitmap != null &&
                        scaledAlbumWidth > 0 &&
                        widthWithAlbum <= MAX_BITMAP_SIZE &&
                        text.length <= MAX_TEXT_LENGTH_FOR_ALBUM

                var finalWidth = if (shouldAddAlbum) widthWithAlbum else widthWithoutAlbum
                finalWidth = finalWidth.coerceAtMost(MAX_BITMAP_SIZE)
                val finalHeight = height.coerceAtMost(MAX_BITMAP_SIZE)

                // 确保文本能完全显示在 finalWidth 内
                val availableTextWidth =
                    if (shouldAddAlbum) {
                        finalWidth - scaledAlbumWidth - ALBUM_FIXED_PADDING_F
                    } else {
                        finalWidth - LEFT_PADDING
                    }

                // 如果文本宽度超过可用宽度，水平缩放文字（变为窄体）
                var textScaleX = 1f
                if (textWidth > availableTextWidth) {
                    textScaleX = (availableTextWidth / textWidth).coerceAtLeast(MIN_TEXT_SCALE_X)
                    textWidth = imageCodec.measureText(text, currentFontSize, textScaleX).width
                }

                val image = getOrCreateBitmap(finalWidth, finalHeight)

                var textX = LEFT_PADDING
                if (shouldAddAlbum) {
                    textX = scaledAlbumWidth + ALBUM_TEXT_SPACING
                }

                imageCodec.drawTextBitmap(
                    target = image,
                    text = text,
                    fontSize = currentFontSize,
                    textScaleX = textScaleX,
                    baseline = baseline + ALBUM_DRAW_Y,
                    album = if (shouldAddAlbum) albumBitmap else null,
                    albumWidth = if (shouldAddAlbum) scaledAlbumWidth else 0,
                    albumHeight = if (shouldAddAlbum) albumHeight else 0,
                    textX = textX,
                )
                Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x$finalHeight")
                return@measureTime image
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "生成文本位图失败: ${e.message}")
                return@measureTime null
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "生成文本位图失败: ${e.message}")
                return@measureTime null
            } catch (e: Exception) {
                Logger.e(TAG, "生成文本位图未预期异常", e)
                throw e
            }
        }

    /**
     * 将进度数据转换为环形进度圈位图
     * @param progress 进度值 (0-100)
     * @param colorReach 已达到部分的颜色
     * @param colorUnReach 未达到部分的颜色
     * @param isCCW 是否逆时针绘制
     * @return 生成的位图，失败返回 null
     */
    fun progressToBitmap(
        progress: Int,
        colorReach: String? = null,
        colorUnReach: String? = null,
        isCCW: Boolean = false,
    ): PlatformBitmap? =
        measureTime(TAG, "progressToBitmap") {
            try {
                if (progress !in 0..100) {
                    Logger.w(TAG, "进度值无效，progress=$progress")
                    return@measureTime null
                }

                val bitmap = getOrCreateBitmap(PROGRESS_SIZE, PROGRESS_SIZE)

                val colorUnReachArgb = resolveColor(colorUnReach, DEFAULT_UNREACH_COLOR, FALLBACK_UNREACH_ARGB, "未达到部分")
                val colorReachArgb = resolveColor(colorReach, DEFAULT_REACH_COLOR, FALLBACK_REACH_ARGB, "已达到部分")

                imageCodec.drawProgressBitmap(
                    target = bitmap,
                    progress = progress,
                    colorReachArgb = colorReachArgb,
                    colorUnReachArgb = colorUnReachArgb,
                    isCCW = isCCW,
                )

                Logger.d(TAG, "生成进度位图成功，进度: $progress%")
                return@measureTime bitmap
            } catch (e: Exception) {
                Logger.w(TAG, "生成进度位图失败: ${e.message}", e)
                return@measureTime null
            }
        }

    private fun resolveColor(
        text: String?,
        defaultText: String,
        fallbackArgb: Int,
        label: String,
    ): Int {
        if (text != null) {
            val parsed = imageCodec.parseColorOrNull(text)
            if (parsed != null) return parsed
            Logger.w(TAG, "解析$label 颜色失败: $text")
        }
        return imageCodec.parseColorOrNull(defaultText) ?: fallbackArgb
    }

    private fun getOrCreateBitmap(
        width: Int,
        height: Int,
    ): PlatformBitmap =
        synchronized(cacheLock) {
            val key = width * 10000 + height // 简单的尺寸键值

            // 从缓存池中查找合适尺寸的 bitmap
            val cached = bitmapCache[key]
            if (cached != null) {
                val iterator = cached.iterator()
                while (iterator.hasNext()) {
                    val bitmap = iterator.next()
                    if (!imageCodec.isRecycled(bitmap)) {
                        iterator.remove()
                        imageCodec.eraseToTransparent(bitmap)
                        return@synchronized bitmap
                    }
                }
            }

            // 创建新 bitmap
            val newBitmap = imageCodec.createBitmap(width, height)

            // 管理缓存大小
            val list = bitmapCache.getOrPut(key) { mutableListOf() }
            list.add(newBitmap)
            if (list.size > MAX_CACHE_SIZE) {
                val oldest = list.removeAt(0)
                if (!imageCodec.isRecycled(oldest)) {
                    imageCodec.recycle(oldest)
                }
            }

            newBitmap
        }

    /**
     * 清理位图缓存池（供公平运行内存回调使用，不释放可复用位图）。
     */
    fun clearCache() {
        synchronized(cacheLock) {
            for (bitmaps in bitmapCache.values) {
                for (bitmap in bitmaps) {
                    if (!imageCodec.isRecycled(bitmap)) {
                        imageCodec.recycle(bitmap)
                    }
                }
                bitmaps.clear()
            }
            bitmapCache.clear()
        }
    }
}
