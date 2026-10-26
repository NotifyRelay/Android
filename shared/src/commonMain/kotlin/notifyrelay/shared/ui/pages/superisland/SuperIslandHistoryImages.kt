package notifyrelay.shared.ui.pages.superisland

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import notifyrelay.shared.ui.port.LocalImageDecodePort
import notifyrelay.shared.ui.viewmodel.AppIconViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val SUPER_ISLAND_IMAGE_MAX_DIMENSION = 320

@Composable
fun SuperIslandHistoryImage(
    imageKey: String,
    data: String,
    modifier: Modifier = Modifier,
    viewModel: AppIconViewModel = viewModel(),
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val imagePort = LocalImageDecodePort.current ?: error("LocalImageDecodePort 未提供")

    val imageBitmap by produceState(initialValue = imagePort.cached(data), key1 = data) {
        val cached = imagePort.cached(data)
        if (cached != null) {
            value = cached
            return@produceState
        }
        val loaded = imagePort.decode(data)
        if (loaded != null) imagePort.putCache(data, loaded)
        value = loaded
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shown = imageBitmap
        if (shown != null) {
            Image(
                bitmap = shown,
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
fun rememberAppIconBitmap(packageName: String?): ImageBitmap? {
    val target = remember(packageName) { packageName?.takeIf { it.isNotBlank() } }
    val viewModel: AppIconViewModel = viewModel()
    val iconUpdateKey by viewModel.iconUpdatesFlow.collectAsState()
    val bitmapState =
        produceState<ImageBitmap?>(initialValue = null, key1 = target, key2 = iconUpdateKey) {
            if (target == null) {
                value = null
                return@produceState
            }
            val cached = viewModel.externalIcon(target) { value = it }
            if (value != null) return@produceState
            viewModel.iconWithAutoRequest(target) { value = it }
        }
    return bitmapState.value
}

@Composable
fun SuperIslandAppIcon(
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
                    ?.uppercase()
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
