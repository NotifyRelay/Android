package github.xzynine.superislandui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import notifyrelay.base.util.Logger

/**
 * 位图工具类，用于处理文本到位图的转换等操作
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"
    
    /**
     * 性能监控工具
     */
    private inline fun <T> measureTime(operation: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        if (duration > 16) { // 超过一帧时间
            Logger.w(TAG, "$operation 耗时 ${duration}ms")
        }
        return result
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val progressPaintUnReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = "#888888".toColorInt()
    }
    
    private val progressPaintReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        color = "#00FF00".toColorInt()
    }
    
    private var reusableBitmap: Bitmap? = null
    private var reusableBitmapWidth = 0
    private var reusableBitmapHeight = 0
    
    // Bitmap缓存池，支持不同尺寸的bitmap复用
    private val bitmapCache = mutableMapOf<Int, MutableList<Bitmap>>()
    private const val MAX_CACHE_SIZE = 10 // 最大缓存数量
    
    /**
     * 将文本转换为位图
     * @param text 要转换的文本
     * @param forceFontSize 强制字体大小，null 表示使用默认大小
     * @param albumBitmap 专辑图 bitmap，可为 null
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(text: String, forceFontSize: Float? = null, albumBitmap: Bitmap? = null): Bitmap? {
        return measureTime("textToBitmap") {
            try {
                if (text.isBlank()) {
                    Logger.w(TAG, "文本为空，无法生成位图")
                    return@measureTime null
                }
                
                val fontSize = (forceFontSize ?: 40f) * 2f

                // 字符权重缓存
                val charWeightCache = mutableMapOf<Char, Float>()
                
                fun calculateEquivalentLength(text: String): Float {
                    var length = 0f
                    for (char in text) {
                        length += charWeightCache.getOrPut(char) {
                            if (char in 'a'..'z') 0.5f else 1f
                        }
                    }
                    return length
                }
                
                var currentFontSize = fontSize
                textPaint.textSize = currentFontSize
                
                val textEquivalentLength = calculateEquivalentLength(text)
                
                if (textEquivalentLength > 2) {
                    val scaleFactor = 1.0f - (textEquivalentLength - 2) * 0.1f
                    currentFontSize = fontSize * scaleFactor.coerceAtLeast(0.5f)
                    textPaint.textSize = currentFontSize
                }
                
                val baseline = -textPaint.ascent()
                var textWidth = textPaint.measureText(text)
                val height = (baseline + textPaint.descent() + 10).toInt()
                
                val maxSize = 500
                val maxTextLengthForAlbum = 6 // 最多6个字符时才添加专辑图
                
                // 计算真实的专辑图宽度
                var scaledAlbumWidth = 0
                if (albumBitmap != null) {
                    val albumHeight = height - 10
                    if (albumHeight > 0 && albumBitmap.height > 0) {
                        val scaleFactor = albumHeight.toFloat() / albumBitmap.height
                        scaledAlbumWidth = (albumBitmap.width * scaleFactor).toInt()
                    }
                }
                
                val widthWithAlbum = (textWidth + scaledAlbumWidth + 20).toInt()
                val widthWithoutAlbum = (textWidth + 10).toInt()
                
                if (widthWithoutAlbum <= 0 || height <= 0) {
                    Logger.w(TAG, "文本位图尺寸无效，width=$widthWithoutAlbum, height=$height")
                    return@measureTime null
                }
                
                val shouldAddAlbum = albumBitmap != null && 
                                    scaledAlbumWidth > 0 &&
                                    widthWithAlbum <= maxSize && 
                                    text.length <= maxTextLengthForAlbum
                
                var finalWidth = if (shouldAddAlbum) widthWithAlbum else widthWithoutAlbum
                finalWidth = finalWidth.coerceAtMost(maxSize)
                val finalHeight = height.coerceAtMost(maxSize)
                
                // 确保文本能完全显示在finalWidth内
                val availableTextWidth = if (shouldAddAlbum) {
                    finalWidth - scaledAlbumWidth - 20f // 20 = 5 (left padding) + 15 (spacing)
                } else {
                    finalWidth - 10f // 10 = left padding
                }
                
                // 如果文本宽度超过可用宽度，水平缩放文字（变为窄体）
                if (textWidth > availableTextWidth) {
                    val scaleFactor = availableTextWidth / textWidth
                    textPaint.textScaleX = scaleFactor.coerceAtLeast(0.5f) // 最小缩放0.5，避免文字过于压缩
                    // 重新计算缩放后的文本宽度
                    textWidth = textPaint.measureText(text)
                } else {
                    // 重置文本缩放为1.0
                    textPaint.textScaleX = 1.0f
                }
                
                val image = getOrCreateBitmap(finalWidth, finalHeight)
                val canvas = Canvas(image)
                
                var textX = 10f
                if (shouldAddAlbum) {
                    val albumHeight = height - 10
                    val scaleFactor = albumHeight.toFloat() / albumBitmap!!.height
                    val scaledWidth = (albumBitmap.width * scaleFactor).toInt()
                    
                    val scaledAlbumBitmap = Bitmap.createScaledBitmap(
                        albumBitmap, 
                        scaledWidth, 
                        albumHeight, 
                        false // 关闭filtering，提高性能
                    )
                    canvas.drawBitmap(scaledAlbumBitmap, 5f, 5f, null)
                    textX = scaledWidth + 15f
                }
                
                canvas.drawText(text, textX, baseline + 5f, textPaint)
                // 重置文本缩放为1.0，确保不影响后续绘制
                textPaint.textScaleX = 1.0f
                Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x${finalHeight}")
                return@measureTime image
            } catch (e: Exception) {
                Logger.w(TAG, "生成文本位图失败: ${e.message}")
                e.printStackTrace()
                return@measureTime null
            }
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
    fun progressToBitmap(progress: Int, colorReach: String? = null, colorUnReach: String? = null, isCCW: Boolean = false): Bitmap? {
        return measureTime("progressToBitmap") {
            try {
                if (progress !in 0..100) {
                    Logger.w(TAG, "进度值无效，progress=$progress")
                    return@measureTime null
                }
                
                val size = 100
                val bitmap = getOrCreateBitmap(size, size)
                val canvas = Canvas(bitmap)
                
                val sweepAngle = (progress / 100f) * 360f
                val startAngle = if (isCCW) 90f else -90f
                
                progressPaintUnReach.color = colorUnReach?.let { 
                    try {
                        it.toColorInt()
                    } catch (e: Exception) {
                        Logger.w(TAG, "解析未达到部分颜色失败: ${e.message}")
                        "#888888".toColorInt()
                    }
                } ?: "#888888".toColorInt()
                
                val centerX = size / 2f
                val centerY = size / 2f
                val radius = (size - progressPaintUnReach.strokeWidth) / 2f
                
                canvas.drawArc(
                    centerX - radius, centerY - radius,
                    centerX + radius, centerY + radius,
                    startAngle, 360f, false, progressPaintUnReach
                )
                
                progressPaintReach.color = colorReach?.let { 
                    try {
                        it.toColorInt()
                    } catch (e: Exception) {
                        Logger.w(TAG, "解析已达到部分颜色失败: ${e.message}")
                        "#00FF00".toColorInt()
                    }
                } ?: "#00FF00".toColorInt()
                
                canvas.drawArc(
                    centerX - radius, centerY - radius,
                    centerX + radius, centerY + radius,
                    startAngle, sweepAngle, false, progressPaintReach
                )
                
                Logger.d(TAG, "生成进度位图成功，进度: $progress%")
                return@measureTime bitmap
            } catch (e: Exception) {
                Logger.w(TAG, "生成进度位图失败: ${e.message}")
                e.printStackTrace()
                return@measureTime null
            }
        }
    }
    
    private fun getOrCreateBitmap(width: Int, height: Int): Bitmap {
        val key = width * 10000 + height // 简单的尺寸键值
        
        // 从缓存池中查找合适尺寸的bitmap
        bitmapCache[key]?.let {
            val iterator = it.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                if (!bitmap.isRecycled) {
                    iterator.remove()
                    bitmap.eraseColor(Color.TRANSPARENT)
                    return bitmap
                }
            }
        }
        
        // 创建新bitmap
        val newBitmap = createBitmap(width, height)
        
        // 管理缓存大小
        bitmapCache.getOrPut(key) { mutableListOf() }.apply {
            add(newBitmap)
            // 限制缓存数量
            if (size > MAX_CACHE_SIZE) {
                val oldest = removeAt(0)
                if (!oldest.isRecycled) {
                    oldest.recycle()
                }
            }
        }
        
        return newBitmap
    }
    
    fun releaseResources() {
        reusableBitmap?.recycle()
        reusableBitmap = null
        reusableBitmapWidth = 0
        reusableBitmapHeight = 0
        
        // 清理缓存池
        for (bitmaps in bitmapCache.values) {
            for (bitmap in bitmaps) {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            bitmaps.clear()
        }
        bitmapCache.clear()
    }
}
