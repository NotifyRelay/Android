package com.xzyht.notifyrelay.ui.pages.history

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DeleteButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = {
            onClick()
        },
        modifier =
            modifier
                .fillMaxHeight()
                .width(80.dp),
        backgroundColor = MiuixTheme.colorScheme.error,
        cornerRadius = 8.dp,
        minHeight = 40.dp,
        minWidth = 80.dp,
    ) {
        Icon(
            imageVector = MiuixIcons.Delete,
            contentDescription = "删除",
            modifier = Modifier.size(24.dp),
        )
    }
}
