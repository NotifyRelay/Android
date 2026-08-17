package com.xzyht.notifyrelay.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideWelcomePage(
    onStart: () -> Unit,
    reauth: Boolean = false,
) {
    val colorScheme = MiuixTheme.colorScheme
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(350)
        revealed = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GuideGlowBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = revealed,
                enter =
                    fadeIn(animationSpec = tween(durationMillis = 900)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 900),
                            initialOffsetY = { it / 3 },
                        ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GuideAppLogo(modifier = Modifier.size(92.dp))
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "NotifyRelay",
                        style = MiuixTheme.textStyles.title1,
                        color = colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "跨设备通知转发 · 设备互联",
                        style = MiuixTheme.textStyles.subtitle,
                        color = colorScheme.onBackgroundVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text =
                            if (reauth) {
                                "版本更新后系统收回了通知访问授权，仅需重新开启即可恢复全部功能"
                            } else {
                                "首次使用前，需要完成几项简单设置"
                            },
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            AnimatedVisibility(
                visible = revealed,
                enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 450)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(52.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary)
                                .clickable(onClick = onStart),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.guide_icon_arrow),
                            contentDescription = "开始",
                            modifier = Modifier.size(width = 31.dp, height = 22.dp),
                            tint = colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "开始",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
