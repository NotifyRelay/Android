package com.xzyht.notifyrelay.ui.pages.remoteapps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DisplayNavigationBar(
    displays: List<DisplayInfo>,
    selectedDisplayId: Int,
    onDisplaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme

    Card(
        modifier =
            modifier
                .padding(horizontal = 16.dp),
        colors =
            CardDefaults.defaultColors(
                color = colorScheme.surface.copy(alpha = 0.95f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            displays.forEach { display ->
                val isSelected = display.id == selectedDisplayId
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onDisplaySelected(display.id) }
                            .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (display.isBuiltIn) MiuixIcons.Settings else MiuixIcons.ScreenMirroring,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = display.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                    )
                    Text(
                        text = "#${display.id}",
                        fontSize = 10.sp,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}
