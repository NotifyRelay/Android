package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.StateFlow
import notifyrelay.base.util.image.toBitmapOrDefault
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.shared.feature.appslist.AppIconPort

/**
 * 以 [AppIconRepository] 实现 [AppIconPort]。
 *
 * 需在应用启动时以进程 Context 创建并注册到 `AppIconPortRegistry`。
 */
class AndroidAppIconPort(
    private val context: Context,
) : AppIconPort {
    override val iconUpdates: StateFlow<Pair<String, Long>?> get() = AppIconRepository.iconUpdates

    override suspend fun localIcon(packageName: String): ImageBitmap? = AppIconRepository.getAppIconAsync(context, packageName)?.asImageBitmap()

    override suspend fun externalIcon(packageName: String): ImageBitmap? = AppIconRepository.getExternalAppIcon(context, packageName)?.asImageBitmap()

    override suspend fun externalIcons(packageNames: List<String>): Map<String, ImageBitmap?> =
        AppIconRepository
            .getExternalAppIcons(context, packageNames)
            .mapValues { (_, icon) -> icon?.asImageBitmap() }

    override suspend fun iconWithAutoRequest(
        packageName: String,
        sourceDevice: DeviceInfo?,
    ): ImageBitmap? =
        AppIconRepository
            .getAppIconWithAutoRequest(context, packageName, sourceDevice = sourceDevice)
            ?.asImageBitmap()

    override fun defaultIcon(size: Int): ImageBitmap? =
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

    override suspend fun cacheExternalIcon(
        packageName: String,
        icon: ImageBitmap?,
        deviceUuid: String,
    ) {
        AppIconRepository.cacheExternalAppIcon(
            context,
            packageName,
            icon?.asAndroidBitmap(),
            deviceUuid,
        )
    }
}
