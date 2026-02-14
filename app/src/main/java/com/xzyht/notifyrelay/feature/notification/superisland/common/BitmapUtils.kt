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
     * @param forceFontSize 强制字体大小，null 表示使用自适应算法
     * @return 生成的位图，失败返回 null
     */
    fun textToBitmap(text: String, forceFontSize: Float? = null): Bitmap? {
        try {
            // 检查文本是否为空
            if (text.isBlank()) {
                Logger.w(TAG, "文本为空，无法生成位图")
                return null
            }
            
            // 自适应字体大小算法
            // 基础大小：40f. 最小大小：20f.
            // 衰减：超过10个字符后每字符减少0.8f.
            val length = text.length
            
            val fontSize = forceFontSize ?: if (length <= 10) {
                40f
            } else {
                (40f - (length - 10) * 0.8f).coerceAtLeast(20f)
            }
            
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = fontSize
                color = Color.WHITE
                textAlign = Paint.Align.LEFT
                typeface = Typeface.DEFAULT_BOLD
            }
            
            val baseline = -paint.ascent() // ascent() 为负值
            // 为紧凑裁剪中的宽字符添加更多缓冲区
            val width = (paint.measureText(text) + 10).toInt() 
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
            canvas.drawText(text, 5f, baseline, paint)
            Logger.d(TAG, "生成文本位图成功，尺寸: ${finalWidth}x${finalHeight}")
            return image
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
