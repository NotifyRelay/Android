package notifyrelay.shared.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.shared.ui.pages.remoteapps.DisplayNavigationBar
import notifyrelay.shared.ui.pages.remoteapps.LocalAppLaunchPort
import notifyrelay.shared.ui.pages.remoteapps.LocalAppsContent
import notifyrelay.shared.ui.pages.remoteapps.LocalDisplayPort
import notifyrelay.shared.ui.pages.remoteapps.RemoteAppsContent
import notifyrelay.shared.ui.pages.remoteapps.RemoteAppsMenuHost
import notifyrelay.shared.ui.pages.remoteapps.rememberRemoteAppsPageState
import notifyrelay.shared.ui.viewmodel.LocalAppsViewModel
import notifyrelay.shared.ui.viewmodel.RemoteAppsViewModel
import notifyrelay.shared.ui.viewmodel.localAppsViewModel
import notifyrelay.shared.ui.viewmodel.remoteAppsViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Replace

@Composable
fun RemoteAppsPage(
    deviceUuid: String?,
    deviceIp: String?,
    modifier: Modifier = Modifier,
    remoteViewModel: RemoteAppsViewModel = remoteAppsViewModel(),
    localViewModel: LocalAppsViewModel = localAppsViewModel(),
) {
    val launchPort = LocalAppLaunchPort.current
    val isLocalMode = deviceUuid == null || deviceIp == null

    val remoteState by remoteViewModel.state.collectAsState()
    val localState by localViewModel.state.collectAsState()

    val pageState = rememberRemoteAppsPageState(isLocalMode = isLocalMode, displayPort = LocalDisplayPort.current)
    var showMenuForApp by pageState.showMenuForApp

    val searchQuery = if (isLocalMode) localState.searchQuery else remoteState.searchQuery

    val displays = pageState.displays
    var selectedDisplayId by pageState.selectedDisplayId

    LaunchedEffect(isLocalMode, deviceIp, deviceUuid) {
        if (isLocalMode) {
            localViewModel.loadApps()
        } else {
            remoteViewModel.loadApps(deviceUuid)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        if (isLocalMode) {
                            localViewModel.searchApps(newValue)
                        } else {
                            remoteViewModel.searchApps(newValue)
                        }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    label = "搜索应用",
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        if (isLocalMode) {
                            localViewModel.loadApps()
                        } else {
                            remoteViewModel.refreshApps()
                        }
                    },
                    enabled = if (isLocalMode) !localState.isLoading else !remoteState.isLoading,
                ) {
                    if (if (isLocalMode) localState.isLoading else remoteState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Replace,
                            contentDescription = "刷新",
                        )
                    }
                }
            }

            if (isLocalMode) {
                LocalAppsContent(
                    state = localState,
                    onAppClick = { app ->
                        launchPort?.launchLocalApp(app.packageName, selectedDisplayId)
                    },
                    onAppLongClick = { showMenuForApp = it },
                )
            } else {
                RemoteAppsContent(
                    state = remoteState,
                    onAppClick = { app ->
                        remoteViewModel.openApp(app, useScrcpyStartApp = true)
                    },
                    onAppLongClick = { showMenuForApp = it },
                    onRefresh = { remoteViewModel.refreshApps() },
                )
            }
        }

        if (isLocalMode && displays.size > 1) {
            DisplayNavigationBar(
                displays = displays,
                selectedDisplayId = selectedDisplayId,
                onDisplaySelected = { selectedDisplayId = it },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
            )
        }
    }

    RemoteAppsMenuHost(
        showMenuForApp = showMenuForApp,
        remoteViewModel = remoteViewModel,
        onDismiss = { showMenuForApp = null },
    )
}
