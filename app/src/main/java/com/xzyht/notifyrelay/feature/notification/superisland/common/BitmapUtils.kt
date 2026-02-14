package com.xzyht.notifyrelay.feature.notification.superisland.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import notifyrelay.base.util.Logger


/**
 * 位图工具类，用于处理文本到位图的转换等操作
 */
object BitmapUtils {
    private const val TAG = "BitmapUtils"
    
    /**
     * 将文本转换为位图
     * @param text 要转换的文本
     * @param forceFontSize 强制字体大小，null 表示使用默认大小
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(text: String, forceFontSize: Float? = null): Bitmap? {
        try {
            // 检查文本是否为空
            if (text.isBlank()) {
                Logger.w(TAG, "文本为空，无法生成位图")
                return null
            }
            
            val fontSize = forceFontSize ?: 40f
            
            // 计算等价字符长度（小写英语字符为0.5个等价字符）
            fun calculateEquivalentLength(text: String): Float {
                var length = 0f
                for (char in text) {
                    if (char in 'a'..'z') {
                        length += 0.5f
                    } else {
                        length += 1f
                    }
                }
                return length
            }
            
            // 查找第一行的分割点（等价字符长度不超过7）
            fun findSplitPoint(text: String): Int {
                var equivalentLength = 0f
                for (i in text.indices) {
                    val char = text[i]
                    val charLength = if (char in 'a'..'z') 0.5f else 1f
                    if (equivalentLength + charLength > 7) {
                        return i
                    }
                    equivalentLength += charLength
                }
                return text.length
            }
            
            val splitPoint = findSplitPoint(text)
            val firstLineText = text.substring(0, splitPoint)
            val secondLineText = text.substring(splitPoint)
            
            // 处理单行或多行逻辑
            if (secondLineText.isBlank()) {
                // 单行文本
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = fontSize
                    color = Color.WHITE
                    textAlign = Paint.Align.LEFT
                    typeface = Typeface.DEFAULT_BOLD
                }
                
                val baseline = -paint.ascent() // ascent() 为负值
                // 为紧凑裁剪中的宽字符添加更多缓冲区
                val width = (paint.measureText(firstLineText) + 10).toInt() 
                val height = (baseline + paint.descent() + 5).toInt()
                
                // 空或无效尺寸的安全检查
                if (width <= 0 || height <= 0) {
                    Logger.w(TAG, "文本位图尺寸无效，width=$width, height=$height")
                    return null
                }

                // 确保尺寸在合理范围内
                val maxSize = 500
                val finalWidth = width.coerceAtMost(maxSize)
                val finalHeight = height.coerceAtMost(maxSize)
                
                val image = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image)
                // 绘制时添加小的左内边距
                canvas.drawText(firstLineText, 5f, baseline, paint)
                Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x${finalHeight}")
                return image
            } else {
                // 多行文本：两行字体大小相同，总高度与单行相同
                val lineFontSize = fontSize * 0.6f // 调整字体大小以适应两行
                
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = lineFontSize
                    color = Color.WHITE
                    textAlign = Paint.Align.LEFT
                    typeface = Typeface.DEFAULT_BOLD
                }
                
                val lineBaseline = -linePaint.ascent()
                
                // 计算宽度：取第一行和第二行中较宽的一个
                val firstLineWidth = linePaint.measureText(firstLineText)
                val secondLineWidth = linePaint.measureText(secondLineText)
                val width = (Math.max(firstLineWidth, secondLineWidth) + 10).toInt()
                
                // 计算高度：保持和单行高度相同
                val singleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = fontSize
                    typeface = Typeface.DEFAULT_BOLD
                }
                val height = (-singleLinePaint.ascent() + singleLinePaint.descent() + 5).toInt()
                
                // 空或无效尺寸的安全检查
                if (width <= 0 || height <= 0) {
                    Logger.w(TAG, "文本位图尺寸无效，width=$width, height=$height")
                    return null
                }

                // 确保尺寸在合理范围内
                val maxSize = 500
                val finalWidth = width.coerceAtMost(maxSize)
                val finalHeight = height.coerceAtMost(maxSize)
                
                val image = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(image)
                
                // 计算两行的垂直位置，确保完整显示
                val lineHeight = height * 0.5f
                val firstLineY = lineHeight * 0.8f // 第一行垂直居中
                val secondLineY = lineHeight + (lineHeight * 0.8f) // 第二行垂直居中
                
                // 绘制第一行
                canvas.drawText(firstLineText, 5f, firstLineY, linePaint)
                
                // 绘制第二行
                canvas.drawText(secondLineText, 5f, secondLineY, linePaint)
                
                Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x${finalHeight}")
                return image
            }
        } catch (e: Exception) {
            Logger.w(TAG, "生成文本位图失败: ${e.message}")
            e.printStackTrace()
            return null
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
        try {
            // 检查进度值是否有效
            if (progress < 0 || progress > 100) {
                Logger.w(TAG, "进度值无效，progress=$progress")
                return null
            }
            
            val size = 100 // 位图大小
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // 背景透明
            canvas.drawColor(Color.TRANSPARENT)
            
            // 计算进度角度
            val sweepAngle = (progress / 100f) * 360f
            val startAngle = if (isCCW) 90f else -90f
            
            // 绘制未达到的部分
            val paintUnReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                color = colorUnReach?.let { 
                    try {
                        Color.parseColor(it)
                    } catch (e: Exception) {
                        Logger.w(TAG, "解析未达到部分颜色失败: ${e.message}")
                        Color.parseColor("#888888") // 默认灰色
                    }
                } ?: Color.parseColor("#888888") // 默认灰色
            }
            
            val centerX = size / 2f
            val centerY = size / 2f
            val radius = (size - paintUnReach.strokeWidth) / 2f
            
            // 绘制未达到的圆环
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, 360f, false, paintUnReach
            )
            
            // 绘制已达到的部分
            val paintReach = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                color = colorReach?.let { 
                    try {
                        Color.parseColor(it)
                    } catch (e: Exception) {
                        Logger.w(TAG, "解析已达到部分颜色失败: ${e.message}")
                        Color.parseColor("#00FF00") // 默认绿色
                    }
                } ?: Color.parseColor("#00FF00") // 默认绿色
            }
            
            // 绘制已达到的圆弧
            canvas.drawArc(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius,
                startAngle, sweepAngle, false, paintReach
            )
            
            Logger.d(TAG, "生成进度位图成功，进度: $progress%")
            return bitmap
        } catch (e: Exception) {
            Logger.w(TAG, "生成进度位图失败: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
