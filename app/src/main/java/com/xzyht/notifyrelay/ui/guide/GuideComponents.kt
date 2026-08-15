package com.xzyht.notifyrelay.ui.guide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.xzyht.notifyrelay.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideGlowBackground(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "guideGlow")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guideGlowDrift"
    )
    val secondaryDrift by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guideGlowSecondaryDrift"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.95f
        val firstCenter = Offset(
            x = size.width * (0.15f + 0.70f * drift),
            y = size.height * (0.10f + 0.28f * (1f - drift))
        )
        val secondCenter = Offset(
            x = size.width * (0.10f + 0.80f * secondaryDrift),
            y = size.height * (0.58f + 0.32f * drift)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = firstCenter,
                radius = radius
            ),
            radius = radius,
            center = firstCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colorScheme.secondary.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = secondCenter,
                radius = radius * 0.72f
            ),
            radius = radius * 0.72f,
            center = secondCenter
        )
    }
}

@Composable
internal fun GuideAppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)

    // 与设置-关于页保持一致：通过 PackageManager 读取系统实际的应用图标，
    // 避免直接加载 adaptive-icon XML（painterResource 不支持该类型）。
    val appIcon = remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap.asImageBitmap()
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        }.getOrNull()
    }

    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = "应用图标",
            modifier = modifier.clip(shape)
        )
    } else {
        // 兜底：读取失败时使用可被 painterResource 支持的矢量图层拼出图标
        Box(modifier = modifier.clip(shape)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
internal fun GuidePageHeader(
    stepLabel: String,
    title: String,
    subtitle: String
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = stepLabel,
            style = MiuixTheme.textStyles.footnote1,
            color = colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun GuidePermissionItem(
    title: String,
    summary: String,
    granted: Boolean,
    onClick: () -> Unit,
    grantedText: String = "已授权",
    pendingText: String = "去开启",
    extraContent: @Composable () -> Unit = {}
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (granted) colorScheme.primary else colorScheme.outline)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (granted) grantedText else pendingText,
                style = MiuixTheme.textStyles.body2,
                color = if (granted) colorScheme.primary else colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = summary,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 20.dp)
        )
        extraContent()
    }
    HorizontalDivider(color = colorScheme.dividerLine, thickness = 1.dp)
}

@Composable
internal fun GuideSectionLabel(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(modifier = modifier.padding(top = 16.dp, bottom = 10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun GuidePageFooter(
    hint: String?,
    nextText: String,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    hintColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (hint != null) {
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote1,
                color = hintColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = "上一步",
                onClick = onBack,
                modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 50.dp),
                minWidth = 88.dp,
                minHeight = 50.dp
            )
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 50.dp),
                minWidth = 0.dp,
                minHeight = 50.dp
            ) {
                Text(
                    text = nextText,
                    style = MiuixTheme.textStyles.button
                )
            }
        }
    }
}
