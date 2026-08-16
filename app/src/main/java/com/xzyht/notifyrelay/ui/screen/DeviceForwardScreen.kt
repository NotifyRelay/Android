package com.xzyht.notifyrelay.ui.screen

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.notification.backend.RemoteFilterConfig
import com.xzyht.notifyrelay.ui.navigation.LocalNavigator
import com.xzyht.notifyrelay.ui.navigation.Navigator
import com.xzyht.notifyrelay.ui.navigation.Route
import com.xzyht.notifyrelay.ui.pages.ClipboardSyncPage
import com.xzyht.notifyrelay.ui.pages.MusicControlPage
import com.xzyht.notifyrelay.ui.pages.RemoteAppsPage
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyAdvancedPage
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyRootScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyScreenHost
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyUiViewModel
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyVirtualButtonOrderPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceForwardScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!RemoteFilterConfig.isLoaded) {
            RemoteFilterConfig.load(context)
            RemoteFilterConfig.isLoaded = true
        }
    }

    val tabTitles = listOf("剪贴板同步", "音乐控制", "屏幕镜像", "应用列表")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    val selectedTabIndex = pagerState.currentPage
    val colorScheme = MiuixTheme.colorScheme

    val deviceManager = remember { DeviceConnectionManagerSingleton.getDeviceManager(context) }
    val selectedDeviceState = GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceState.value

    val selectedDeviceInfo =
        selectedDevice?.let {
            val authInfo = deviceManager.getAuthenticatedDevices()[it.uuid]
            notifyrelay.data.model.SelectedDeviceInfo(
                displayName = it.displayName,
                ip = it.ip,
                deviceType = authInfo?.deviceType,
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(12.dp),
    ) {
        TabRowWithContour(
            tabs = tabTitles,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                coroutineScope.launch {
                    pagerState.scrollToPage(index)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors =
                TabRowDefaults.tabRowColors(
                    backgroundColor = colorScheme.surface,
                    contentColor = colorScheme.onSurface,
                    selectedBackgroundColor = colorScheme.primary,
                    selectedContentColor = colorScheme.onPrimary,
                ),
            minWidth = 100.dp,
            height = 48.dp,
            cornerRadius = 16.dp,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                when (page) {
                    0 -> ClipboardSyncPage()
                    1 -> MusicControlPage()
                    2 ->
                        ScrcpyScreenHost(
                            startScreen = ScrcpyRootScreen.Device,
                            selectedDevice = selectedDeviceInfo,
                            onOpenAdvanced = { navigator.push(Route.ScrcpyAdvanced) },
                        )
                    3 -> {
                        RemoteAppsPage(
                            deviceUuid = selectedDevice?.uuid,
                            deviceIp = selectedDeviceInfo?.ip,
                        )
                    }
                }
            }
        }
    }
}

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
