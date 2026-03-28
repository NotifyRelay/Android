package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun SectionSmallTitle(text: String, showLeadingSpacer: Boolean = true) {
    if (showLeadingSpacer) {
        Spacer(Modifier.height(UiSpacing.SectionTitleLeadingGap))
    }
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(16.dp, 8.dp)
    )
}
