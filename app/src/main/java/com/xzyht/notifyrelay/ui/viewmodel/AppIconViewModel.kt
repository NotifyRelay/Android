package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.appslist.AppIconRepository
import kotlinx.coroutines.launch
import notifyrelay.base.util.image.toBitmapOrDefault

/**
 * 应用图标与默认图标的承载。
 *
 * 界面通过 [loadIcon] / [loadDefaultIcon] 取图，不再直接访问 [AppIconRepository] 与 PackageManager。
 */
class AppIconViewModel : ViewModel() {
    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    /** 取指定包名的图标；结果经 [onResult] 回调。 */
    fun loadIcon(
        context: Context,
        packageName: String,
        onResult: (ImageBitmap?) -> Unit,
    ) {
        iconCache[packageName]?.let {
            onResult(it)
            return
        }
        viewModelScope.launch {
            val bitmap =
                runCatching {
                    AppIconRepository.getAppIconAsync(context, packageName)?.asImageBitmap()
                }.getOrNull()
            iconCache[packageName] = bitmap
            onResult(bitmap)
        }
    }

    /** 取外部应用图标（可能有缓存）。 */
    fun externalIcon(
        context: android.content.Context,
        packageName: String,
        onResult: (android.graphics.Bitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { AppIconRepository.getExternalAppIcon(context, packageName) }.getOrNull())
        }
    }

    /** 取应用图标并在缺失时自动请求。 */
    fun iconWithAutoRequest(
        context: android.content.Context,
        packageName: String,
        onResult: (android.graphics.Bitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { AppIconRepository.getAppIconWithAutoRequest(context, packageName) }.getOrNull())
        }
    }

    /** 图标更新信号。 */
    val iconUpdatesFlow get() = AppIconRepository.iconUpdates

    /** 取系统默认应用图标（尺寸为 [size] 像素）。 */
    fun loadDefaultIcon(
        context: Context,
        size: Int = 48,
    ): ImageBitmap? =
        runCatching {
            val drawable = context.packageManager.defaultActivityIcon
            val bitmap =
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmapOrDefault(size)
                }
            bitmap.asImageBitmap()
        }.getOrNull()
}
