package notifyrelay.shared.ui.guide

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 引导页应用 Logo 的平台实现在启动时提供。 */
val LocalGuideAppLogo = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

@Composable
fun GuideGlowBackground(modifier: Modifier = Modifier) {
    val colorScheme = MiuixTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "guideGlow")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 7200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "guideGlowDrift",
    )
    val secondaryDrift by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 9600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "guideGlowSecondaryDrift",
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.95f
        val firstCenter =
            Offset(
                x = size.width * (0.15f + 0.70f * drift),
                y = size.height * (0.10f + 0.28f * (1f - drift)),
            )
        val secondCenter =
            Offset(
                x = size.width * (0.10f + 0.80f * secondaryDrift),
                y = size.height * (0.58f + 0.32f * drift),
            )

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            colorScheme.primary.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                    center = firstCenter,
                    radius = radius,
                ),
            radius = radius,
            center = firstCenter,
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            colorScheme.secondary.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    center = secondCenter,
                    radius = radius * 0.72f,
                ),
            radius = radius * 0.72f,
            center = secondCenter,
        )
    }
}

@Composable
fun GuidePageHeader(
    stepLabel: String,
    title: String,
    subtitle: String,
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = stepLabel,
            style = MiuixTheme.textStyles.footnote1,
            color = colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            color = colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun GuidePermissionItem(
    title: String,
    summary: String,
    granted: Boolean,
    onClick: () -> Unit,
    grantedText: String = "已授权",
    pendingText: String = "去开启",
    extraContent: @Composable () -> Unit = {},
) {
    val colorScheme = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        summary = summary,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        startAction = {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (granted) colorScheme.primary else colorScheme.outline),
            )
        },
        endActions = {
            Text(
                text = if (granted) grantedText else pendingText,
                style = MiuixTheme.textStyles.body2,
                color = if (granted) colorScheme.primary else colorScheme.error,
            )
        },
        bottomAction = extraContent,
        onClick = onClick,
    )
    HorizontalDivider(color = colorScheme.dividerLine, thickness = 1.dp)
}

@Composable
fun GuideSectionLabel(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    Column(modifier = modifier.padding(top = 16.dp, bottom = 10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title3,
            color = colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MiuixTheme.textStyles.body2,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun GuidePageFooter(
    hint: String?,
    nextText: String,
    nextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    hintColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (hint != null) {
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote1,
                color = hintColor,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = "上一步",
                onClick = onBack,
                modifier = Modifier.defaultMinSize(minWidth = 88.dp, minHeight = 50.dp),
                minWidth = 88.dp,
                minHeight = 50.dp,
            )
            Button(
                onClick = onNext,
                enabled = nextEnabled,
                modifier =
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 50.dp),
                minWidth = 0.dp,
                minHeight = 50.dp,
            ) {
                Text(
                    text = nextText,
                    style = MiuixTheme.textStyles.button,
                )
            }
        }
    }
}

/** 绘制应用 Logo；平台未提供实现时不渲染。 */
@Composable
fun GuideAppLogoSlot(modifier: Modifier = Modifier) {
    LocalGuideAppLogo.current?.invoke(modifier)
}
