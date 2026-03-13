package com.xzyht.notifyrelay.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.superisland.lifecyle.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.servers.appslist.AppRepository
import com.xzyht.notifyrelay.servers.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.ui.common.NotifyRelayTheme
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import com.xzyht.notifyrelay.ui.navigation.LocalNavigator
import com.xzyht.notifyrelay.ui.navigation.Route
import com.xzyht.notifyrelay.ui.navigation.rememberNavigator
import com.xzyht.notifyrelay.ui.screen.DeviceListScreen
import com.xzyht.notifyrelay.ui.screen.DeviceListScreenState
import com.xzyht.notifyrelay.ui.screen.DeviceForwardScreen
import com.xzyht.notifyrelay.ui.screen.HistoryScreen
import com.xzyht.notifyrelay.ui.screen.SettingsScreen
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ToastUtils
import notifyrelay.core.util.ServiceManager
import notifyrelay.data.config.DeviceInfoManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

class MainActivity : FragmentActivity() {
    internal var showAutoStartBanner = false
    internal var bannerMessage: String? = null
    
    private var backPressedTime: Long = 0
    private val EXIT_INTERVAL = 2000L

    private fun checkPermissionsAndStartServices() {
        showAutoStartBanner = false
        bannerMessage = null

        if (!PermissionHelper.checkAllPermissions(this)) {
            Logger.w("NotifyRelay", "必要权限未授权，跳转引导页")
            val intent = Intent(this, GuideActivity::class.java)
            intent.putExtra("from", "MainActivity")
            startActivity(intent)
            finish()
            return
        }

        val result = ServiceManager.startAllServices(this)
        val serviceStarted = result.first
        val errorMessage = result.second as? String
        if (errorMessage != null) {
            showAutoStartBanner = true
            bannerMessage = errorMessage
        }

        if (!serviceStarted) {
            showAutoStartBanner = true
            bannerMessage = "服务无法启动，可能因系统自启动/后台运行权限被拒绝。请前往系统设置手动允许自启动、后台运行和电池优化白名单，否则通知转发将无法正常工作。"
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndStartServices()
        if (checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED) {
            ClipboardSyncManager.startLogMonitoring(this)
        }
    }

    private val guideLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        recreate()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DeveloperModeActivity.initLogConfig(this)
        DeveloperModeActivity.initDebugUiConfig(this)

        PermissionHelper.AppForegroundDetector.initialize(this)

        WindowCompat.setDecorFitsSystemWindows(this.window, false)
        this.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            val navigator = rememberNavigator(Route.Main)
            val isDarkTheme = isSystemInDarkTheme()
            
            NotifyRelayTheme(darkTheme = isDarkTheme) {
                val colorScheme = MiuixTheme.colorScheme
                SetupSystemBars(isDarkTheme)
                
                CompositionLocalProvider(
                    LocalNavigator provides navigator
                ) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                    ) {
                        NavDisplay(
                            backStack = navigator.backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator()
                            ),
                            onBack = {
                                if (navigator.backStackSize() > 1) {
                                    navigator.pop()
                                }
                            },
                            entryProvider = entryProvider {
                                entry<Route.Main> { MainScreen(navigator) }
                                entry<Route.History> { HistoryScreen(navigator) }
                                entry<Route.Settings> { SettingsScreen(navigator) }
                            }
                        )
                    }
                }
            }
        }

        if (!PermissionHelper.checkAllPermissions(this)) {
            ToastUtils.showShortToast(this, "请先授权所有必要权限！")
            val intent = Intent(this, GuideActivity::class.java)
            intent.putExtra("from", "MainActivity")
            guideLauncher.launch(intent)
            return
        }

        DeviceConnectionManager.getInstance(this)
        DeviceInfoManager.generateDeviceInfoFile(this)
        LiveUpdatesNotificationManager.initialize(this)
        
        GlobalScope.launch {
            NotificationRepository.init(this@MainActivity)
            AppRepository.loadApps(this@MainActivity)
            startServicesAndUpdateBanner()
        }
    }
    
    private suspend fun startServicesAndUpdateBanner() {
        val result = ServiceManager.startAllServices(this)
        val serviceStarted = result.first
        val errorMessage = result.second as? String
        if (errorMessage != null) {
            withContext(Dispatchers.Main) {
                showAutoStartBanner = true
                bannerMessage = errorMessage
            }
        }

        if (!serviceStarted) {
            withContext(Dispatchers.Main) {
                showAutoStartBanner = true
                bannerMessage = "服务无法启动，可能因系统自启动/后台运行权限被拒绝。请前往系统设置手动允许自启动、后台运行和电池优化白名单，否则通知转发将无法正常工作。"
            }
        }
    }
}

@Composable
fun MainScreen(navigator: com.xzyht.notifyrelay.ui.navigation.Navigator) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val colorScheme = MiuixTheme.colorScheme
    
    val errorColor = Color(0xFFD32F2F)
    val onErrorColor = Color.White
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    val activity = LocalContext.current as? MainActivity
    val showBanner = activity?.showAutoStartBanner == true
    val bannerMsg = activity?.bannerMessage
    val context = LocalContext.current
    
    val deviceListState = remember { DeviceListScreenState() }
    
    MainScreenBackHandler(selectedTab, navigator, deviceListState) { selectedTab = 0 }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            popupHost = {},
            topBar = {
                if (showBanner && !bannerMsg.isNullOrBlank()) {
                    Surface(
                        color = errorColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = bannerMsg,
                                style = MiuixTheme.textStyles.body1,
                                color = onErrorColor,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    IntentUtils.startActivity(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null), true)
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("前往设置")
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    color = colorScheme.background,
                    modifier = Modifier
                        .height(58.dp)
                        .navigationBarsPadding()
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = MiuixIcons.Community,
                        label = "历史"
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = MiuixIcons.Settings,
                        label = "设备互联与增强"
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = MiuixIcons.Tune,
                        label = "设置"
                    )
                }
            },
            containerColor = colorScheme.background
        ) { paddingValues ->
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                        .padding(paddingValues)
                ) {
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .background(colorScheme.background)
                    ) {
                        DeviceListScreen(navigator, deviceListState)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (selectedTab) {
                            0 -> HistoryScreen(navigator)
                            1 -> DeviceForwardScreen(navigator)
                            2 -> SettingsScreen(navigator)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                        .padding(paddingValues)
                ) {
                    DeviceListScreen(navigator, deviceListState)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (selectedTab) {
                            0 -> HistoryScreen(navigator)
                            1 -> DeviceForwardScreen(navigator)
                            2 -> SettingsScreen(navigator)
                        }
                    }
                }
            }
        }
        MiuixPopupHost()
    }
}

@Composable
private fun MainScreenBackHandler(
    selectedTab: Int,
    navigator: com.xzyht.notifyrelay.ui.navigation.Navigator,
    deviceListState: DeviceListScreenState,
    onBackToFirstTab: () -> Unit
) {
    val activity = LocalContext.current as? MainActivity
    var backPressedTime by remember { mutableLongStateOf(0L) }
    val EXIT_INTERVAL = 2000L

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
                onBackToFirstTab()
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < EXIT_INTERVAL) {
                    activity?.finish()
                } else {
                    ToastUtils.showShortToast(activity ?: return@NavigationBackHandler, "再次返回以退出应用")
                    backPressedTime = currentTime
                }
            }
        }
    )
}
