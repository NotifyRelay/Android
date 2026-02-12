package com.xzyht.notifyrelay.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner

@Composable
fun ProvideNavigationEventDispatcherOwner(content: @Composable () -> Unit) {
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(true, null)
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
        content()
    }
}
