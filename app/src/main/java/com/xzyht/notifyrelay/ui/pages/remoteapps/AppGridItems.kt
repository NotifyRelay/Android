package com.xzyht.notifyrelay.ui.pages.remoteapps

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppInfo
import com.xzyht.notifyrelay.ui.viewmodel.LocalAppInfo
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LocalAppItem(
    app: LocalAppInfo,
    iconBitmap: ImageBitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme

    Column(
        modifier =
            Modifier
                .width(72.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.appName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.appName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AppItem(
    app: RemoteAppInfo,
    iconBitmap: Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme

    Column(
        modifier =
            Modifier
                .width(72.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colorScheme.onSurfaceSecondary,
                    )
                }
            }
            if (app.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (app.isPinned) {
                Icon(
                    imageVector = MiuixIcons.Pin,
                    contentDescription = "已置顶",
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .background(
                                colorScheme.primary,
                                CircleShape,
                            ).padding(2.dp),
                    tint = colorScheme.onPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.appName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun openLocalApp(
    context: Context,
    app: LocalAppInfo,
    displayId: Int,
) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        intent?.let {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // minSdk = 31（> O），makeBasic/launchDisplayId 恒可用，无需版本判断
            val options = ActivityOptions.makeBasic()
            options.launchDisplayId = displayId
            context.startActivity(intent, options.toBundle())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
