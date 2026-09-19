package com.xzyht.notifyrelay.ui.pages.superisland

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.image.ImageUtils
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

internal const val SUPER_ISLAND_IMAGE_MAX_DIMENSION = 320

@Composable
internal fun SuperIslandHistoryImage(
    imageKey: String,
    data: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val context = LocalContext.current

    val bitmap by produceState(initialValue = SuperIslandImageCache.get(data), key1 = data) {
        val cached = SuperIslandImageCache.get(data)
        if (cached != null) {
            value = cached
            return@produceState
        }

        val loaded =
            withContext(Dispatchers.IO) {
                try {
                    val resolved =
                        try {
                            SuperIslandImageStore.resolve(context, data) ?: data
                        } catch (_: Exception) {
                            data
                        }

                    val decoded =
                        when {
                            ImageUtils.isDataUrl(resolved) -> ImageUtils.decodeDataUrlToBitmap(context, resolved)
                            resolved.startsWith("http", ignoreCase = true) -> downloadBitmap(context, resolved)
                            else -> null
                        }
                    decoded?.let { SuperIslandImageCache.put(data, it) }
                } catch (_: Exception) {
                    null
                }
            }

        value = loaded
    }

    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = imageKey,
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = data.take(120),
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant)
                        .padding(8.dp),
            )
        }
        if (imageKey.isNotBlank()) {
            Text(
                text = imageKey,
                style = textStyles.body2,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun rememberAppIconBitmap(packageName: String?): ImageBitmap? {
    val target = remember(packageName) { packageName?.takeIf { it.isNotBlank() } }
    val context = LocalContext.current
    val iconUpdateKey by AppRepository.iconUpdates.collectAsState()
    val bitmapState =
        produceState<ImageBitmap?>(initialValue = null, key1 = target, key2 = iconUpdateKey) {
            if (target == null) {
                value = null
                return@produceState
            }
            val cached = AppRepository.getExternalAppIcon(context, target)
            if (cached != null) {
                value = cached.asImageBitmap()
                return@produceState
            }
            val fetched =
                withContext(Dispatchers.IO) {
                    AppRepository.getAppIconWithAutoRequest(context, target)
                }
            value = fetched?.asImageBitmap()
        }
    return bitmapState.value
}

@Composable
internal fun SuperIslandAppIcon(
    iconBitmap: ImageBitmap?,
    iconPackage: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = iconPackage,
            modifier =
                modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        val fallback =
            remember(iconPackage) {
                iconPackage
                    ?.substringAfterLast('.')
                    ?.takeLast(2)
                    ?.uppercase(Locale.getDefault())
                    ?: "APP"
            }
        Box(
            modifier =
                modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = fallback,
                style = textStyles.footnote1,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private suspend fun downloadBitmap(
    context: Context,
    urlString: String,
): Bitmap? =
    try {
        ImageUtils.loadBitmap(context, urlString)
    } catch (_: Exception) {
        null
    }
