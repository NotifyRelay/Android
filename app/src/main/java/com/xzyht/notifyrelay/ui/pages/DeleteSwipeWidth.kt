package com.xzyht.notifyrelay.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 滑动删除暴露宽度（80dp）及其像素值，供 [NotificationHistoryScreen] / [UISuperIslandHistory] 共用。
 *
 * @return `first` 为宽度的像素值（Float），`second` 为宽度本身（[Dp]）。
 */
@Composable
internal fun rememberDeleteSwipeWidth(): Pair<Float, Dp> {
    val density = LocalDensity.current
    return with(density) { 80.dp.toPx() } to 80.dp
}
