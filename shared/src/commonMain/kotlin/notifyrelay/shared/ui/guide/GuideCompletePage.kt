package notifyrelay.shared.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GuideCompletePage(
    requiredGranted: Boolean,
    onBackToPermissions: () -> Unit,
    onEnter: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    GuideScaffold(delayMillis = 300) { revealed ->
        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(
            visible = revealed,
            enter =
                fadeIn(animationSpec = tween(durationMillis = 800)) +
                    slideInVertically(
                        animationSpec = tween(durationMillis = 800),
                        initialOffsetY = { it / 3 },
                    ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GuideAppLogoSlot(modifier = Modifier.size(92.dp))
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "设置完成",
                    style = MiuixTheme.textStyles.title1,
                    color = colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "NotifyRelay 已准备就绪",
                    style = MiuixTheme.textStyles.subtitle,
                    color = colorScheme.onBackgroundVariant,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (requiredGranted) {
                    onEnter()
                } else {
                    onBackToPermissions()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp),
            minWidth = 0.dp,
            minHeight = 50.dp,
        ) {
            Text(
                text = if (requiredGranted) "进入 NotifyRelay" else "返回权限设置",
                style = MiuixTheme.textStyles.button,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
