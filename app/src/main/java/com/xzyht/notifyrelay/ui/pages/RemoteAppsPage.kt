package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzyht.notifyrelay.ui.pages.remoteapps.DisplayNavigationBar
import com.xzyht.notifyrelay.ui.pages.remoteapps.LocalAppsContent
import com.xzyht.notifyrelay.ui.pages.remoteapps.RemoteAppsContent
import com.xzyht.notifyrelay.ui.pages.remoteapps.RemoteAppsMenuHost
import com.xzyht.notifyrelay.ui.pages.remoteapps.openLocalApp
import com.xzyht.notifyrelay.ui.pages.remoteapps.rememberRemoteAppsPageState
import com.xzyht.notifyrelay.ui.viewmodel.LocalAppsViewModel
import com.xzyht.notifyrelay.ui.viewmodel.RemoteAppsViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RemoteAppsPage(
    deviceUuid: String?,
    deviceIp: String?,
    modifier: Modifier = Modifier,
    remoteViewModel: RemoteAppsViewModel = viewModel(),
    localViewModel: LocalAppsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val isLocalMode = deviceUuid == null || deviceIp == null

    val remoteState by remoteViewModel.state.collectAsState()
    val localState by localViewModel.state.collectAsState()

    val pageState = rememberRemoteAppsPageState(isLocalMode = isLocalMode, context = context)
    var searchQuery by pageState.searchQuery
    var showMenuForApp by pageState.showMenuForApp
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val displays = pageState.displays
    var selectedDisplayId by pageState.selectedDisplayId

    LaunchedEffect(isLocalMode, deviceIp, deviceUuid) {
        if (isLocalMode) {
            localViewModel.loadApps(context)
        } else {
            remoteViewModel.loadApps(context, deviceUuid!!)
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
                        searchQuery = newValue
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
                            localViewModel.loadApps(context)
                        } else {
                            remoteViewModel.refreshApps(context)
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
                    apps =
                        localState.apps.filter {
                            searchQuery.isBlank() ||
                                it.appName.contains(searchQuery, ignoreCase = true) ||
                                it.packageName.contains(searchQuery, ignoreCase = true)
                        },
                    isLoading = localState.isLoading,
                    error = localState.error,
                    onAppClick = { app ->
                        openLocalApp(context, app, selectedDisplayId)
                    },
                    onAppLongClick = { showMenuForApp = it },
                )
            } else {
                RemoteAppsContent(
                    state = remoteState,
                    searchQuery = searchQuery,
                    onAppClick = { app ->
                        remoteViewModel.openApp(context, app, deviceIp!!, useScrcpyStartApp = true)
                    },
                    onAppLongClick = { showMenuForApp = it },
                    onRefresh = { remoteViewModel.refreshApps(context) },
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
        context = context,
        remoteViewModel = remoteViewModel,
        onDismiss = { showMenuForApp = null },
    )
}
