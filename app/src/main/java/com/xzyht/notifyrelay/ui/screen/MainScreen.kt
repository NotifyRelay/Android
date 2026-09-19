package com.xzyht.notifyrelay.ui.screen

import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.xzyht.notifyrelay.ui.activity.MainActivity
import com.xzyht.notifyrelay.ui.navigation.Navigator
import com.xzyht.notifyrelay.ui.navigation.Route
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(navigator: Navigator) {
    val colorScheme = MiuixTheme.colorScheme

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activity = LocalActivity.current as? MainActivity
    val showBanner = activity?.showAutoStartBanner?.value == true
    val bannerMsg = activity?.bannerMessage?.value
    val context = LocalContext.current

    val deviceListState = remember { DeviceListScreenState() }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    // 单一真源：选中页直接由 pagerState.currentPage 派生。
    // 原先另有独立的 rememberSaveable selectedTab + LaunchedEffect(currentPage) → selectedTab 单向同步，
    // 两者一旦不同步（如旋转/进程恢复路径差异）会出现底栏高亮与当前页不符。pagerState 自身已由
    // rememberSaveable + DefaultPagerState.Saver 保存 currentPage，故派生即可同时满足状态恢复与一致性。
    val selectedTab = pagerState.currentPage
    val coroutineScope = rememberCoroutineScope()

    MainScreenBackHandler(selectedTab, pagerState, navigator, deviceListState)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(showBanner, bannerMsg, snackbarHostState) {
        withContext(NonCancellable) {
            snackbarHostState.newestSnackbarData()?.dismiss()
        }
        val message = bannerMsg
        if (!showBanner || message.isNullOrBlank()) return@LaunchedEffect

        val result =
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "前往设置",
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
        if (result == SnackbarResult.ActionPerformed) {
            IntentUtils.startActivity(
                context,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
                true,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(
                    state = snackbarHostState,
                    content = { data ->
                        Snackbar(
                            data = data,
                            colors =
                                SnackbarDefaults.snackbarColors(
                                    containerColor = MiuixTheme.colorScheme.error,
                                    contentColor = MiuixTheme.colorScheme.onError,
                                    actionContainerColor = MiuixTheme.colorScheme.onError,
                                    actionContentColor = MiuixTheme.colorScheme.error,
                                    dismissActionContentColor = MiuixTheme.colorScheme.onError,
                                ),
                        )
                    },
                )
            },
            bottomBar = {
                NavigationBar(
                    color = colorScheme.background,
                    modifier =
                        Modifier
                            .height(75.dp)
                            .navigationBarsPadding(),
                ) {
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedTab == 0,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        icon = MiuixIcons.Community,
                        label = "历史",
                    )
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedTab == 1,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        icon = MiuixIcons.Settings,
                        label = "设备互联与增强",
                    )
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        selected = selectedTab == 2,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(2)
                            }
                        },
                        icon = MiuixIcons.Tune,
                        label = "设置",
                    )
                }
            },
            containerColor = colorScheme.background,
        ) { paddingValues ->
            if (isLandscape) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                            .padding(paddingValues),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .background(colorScheme.background),
                    ) {
                        DeviceListScreen(navigator, deviceListState)
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        userScrollEnabled = false,
                    ) { page ->
                        when (page) {
                            0 -> HistoryScreen(navigator)
                            1 -> DeviceForwardScreen()
                            2 -> SettingsScreen()
                        }
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                            .padding(paddingValues),
                ) {
                    DeviceListScreen(navigator, deviceListState)
                    HorizontalPager(
                        state = pagerState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        userScrollEnabled = false,
                    ) { page ->
                        when (page) {
                            0 -> HistoryScreen(navigator)
                            1 -> DeviceForwardScreen()
                            2 -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    selectedTab: Int,
    pagerState: PagerState,
    navigator: Navigator,
    deviceListState: DeviceListScreenState,
) {
    val activity = LocalActivity.current as? MainActivity
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val exitInterval = 2000L
    val coroutineScope = rememberCoroutineScope()

    val isBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main &&
                navigator.backStackSize() == 1
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isBackHandlerEnabled,
        onBackCompleted = {
            if (deviceListState.hasAnyDialogShowing()) {
                deviceListState.dismissAllDialogs()
            } else if (selectedTab != 0) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(0)
                }
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < exitInterval) {
                    activity?.finish()
                } else {
                    ToastUtils.showShortToast(activity ?: return@NavigationBackHandler, "再次返回以退出应用")
                    backPressedTime = currentTime
                }
            }
        },
    )
}
