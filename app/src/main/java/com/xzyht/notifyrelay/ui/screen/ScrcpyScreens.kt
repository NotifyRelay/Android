package com.xzyht.notifyrelay.ui.screen

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyAdvancedPage
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyUiViewModel
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyVirtualButtonOrderPage
import notifyrelay.shared.ui.navigation.Navigator

@Composable
fun ScrcpyAdvancedScreen(
    navigator: Navigator,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scrcpyViewModel = remember { ScrcpyUiViewModel.getInstance(app) }
    ScrcpyAdvancedPage(
        onBack = { navigator.pop() },
        viewModel = scrcpyViewModel,
    )
}

@Composable
fun ScrcpyVirtualButtonOrderScreen(
    navigator: Navigator,
) {
    ScrcpyVirtualButtonOrderPage(
        onBack = { navigator.pop() },
    )
}
