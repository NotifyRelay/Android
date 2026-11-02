package notifyrelay.shared.ui.screen

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.navigation.Route
import notifyrelay.shared.ui.pages.RemoteAppsPage
import notifyrelay.shared.ui.screen.GlobalSelectedDeviceHolder
import notifyrelay.shared.ui.viewmodel.DevicePortsViewModel
import notifyrelay.shared.ui.viewmodel.RemoteFilterViewModel
import notifyrelay.shared.ui.viewmodel.devicePortsViewModel
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceForwardScreen(
    portsViewModel: DevicePortsViewModel = devicePortsViewModel(),
    filterViewModel: RemoteFilterViewModel,
    clipboardSync: @Composable () -> Unit,
    musicControl: @Composable () -> Unit,
    scrcpyScreen: @Composable (notifyrelay.domain.feature.device.model.SelectedDeviceInfo?, () -> Unit) -> Unit,
) {
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!filterViewModel.isConfigLoaded()) {
            filterViewModel.ensureLoaded()
        }
    }

    val tabTitles = listOf("剪贴板同步", "音乐控制", "屏幕镜像", "应用列表")
    val pagerState = rememberPagerState(initialPage = 0) { tabTitles.size }
    val selectedTabIndex = pagerState.currentPage
    val colorScheme = MiuixTheme.colorScheme

    val deviceManager = remember { portsViewModel.adminPort() }
    val selectedDeviceState = GlobalSelectedDeviceHolder.current()
    val selectedDevice = selectedDeviceState.value

    val selectedDeviceInfo =
        selectedDevice?.let {
            val authInfo = deviceManager.getAuthenticatedDevices()[it.uuid]
            notifyrelay.domain.feature.device.model.SelectedDeviceInfo(
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
                    0 -> clipboardSync()
                    1 -> musicControl()
                    2 -> scrcpyScreen(selectedDeviceInfo) { navigator.push(Route.ScrcpyAdvanced) }
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
