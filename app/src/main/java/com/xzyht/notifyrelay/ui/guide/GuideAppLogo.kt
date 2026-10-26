package com.xzyht.notifyrelay.ui.guide

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.R
import notifyrelay.base.util.image.toBitmapOrDefault

@Composable
internal fun GuideAppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)

    // 与设置-关于页保持一致：通过 PackageManager 读取系统实际的应用图标，
    // 避免直接加载 adaptive-icon XML（painterResource 不支持该类型）。
    val appIcon =
        remember(context) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                if (drawable is BitmapDrawable) {
                    drawable.bitmap.asImageBitmap()
                } else {
                    drawable.toBitmapOrDefault(96).asImageBitmap()
                }
            }.getOrNull()
        }

    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = "应用图标",
            modifier = modifier.clip(shape),
        )
    } else {
        // 兜底：读取失败时使用可被 painterResource 支持的矢量图层拼出图标
        Box(modifier = modifier.clip(shape)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
