package com.xzyht.notifyrelay.feature.notification.superisland.notification

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 位图文本度量器。
 *
 * `BitmapUtils` 位于 commonMain、无法自建字体解析器（Android 上解析器需要 Context），
 * 故由本模块按固定密度（1x，位图以像素计）构造并缓存一个实例供其使用。
 */
internal object BitmapTextMeasurer {
    /** 位图绘制用的固定密度：位图尺寸与字号都以像素计，与设备无关。 */
    private val bitmapDensity = Density(density = 1f, fontScale = 1f)

    @Volatile
    private var cached: TextMeasurer? = null

    /** 取共享的度量器实例；首次调用时按 [context] 的应用上下文构造。 */
    fun get(context: Context): TextMeasurer =
        cached ?: synchronized(this) {
            cached ?: TextMeasurer(
                defaultFontFamilyResolver = createFontFamilyResolver(context.applicationContext),
                defaultDensity = bitmapDensity,
                defaultLayoutDirection = LayoutDirection.Ltr,
            ).also { cached = it }
        }
}
