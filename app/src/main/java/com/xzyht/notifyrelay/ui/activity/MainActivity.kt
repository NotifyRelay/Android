package com.xzyht.notifyrelay.ui.activity

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import com.xzyht.notifyrelay.feature.appslist.launch.AppLaunchManager
import com.xzyht.notifyrelay.feature.device.model.NotificationRepository
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.notification.superisland.notification.LiveUpdatesNotificationManager
import com.xzyht.notifyrelay.ui.navigation.Route
import com.xzyht.notifyrelay.ui.navigation.rememberNavigator
import io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.data.config.DeviceInfoManager

class MainActivity : FragmentActivity() {
    internal val showAutoStartBanner = mutableStateOf(false)
    internal val bannerMessage = mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        screenCaptureCoordinator.processPendingScreenCapture()
        // 后台执行权限检查和服务启动，避免阻塞 UI 线程
        lifecycleScope.launch(Dispatchers.Default) {
            permissions.checkPermissionsAndStartServices()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureCoordinator.release()
    }

    // 权限与服务启动协调器：内部 guideLauncher 需在本 Activity 字段初始化期注册，
    // 故该字段保持在此处初始化（与原 guideLauncher 同位置）。
    private val permissions = MainActivityPermissions(this)

    // 屏幕捕获协调器：其内部三个 registerForActivityResult 必须在本 Activity 字段初始化期注册，
    // 故该字段保持在此处初始化（与原先的 screenCaptureLauncher / recordAudioPermissionLauncher 同位置）。
    private val screenCaptureCoordinator = ScreenCaptureCoordinator(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ShortcutLaunchActivity.setAppLaunchCallback { deviceIp, packageName, displayId ->
            val deviceManager = DeviceConnectionManager.getInstance(this)
            val devices = deviceManager.getAuthenticatedOnlineDevices()
            val targetDevice = devices.find { it.ip == deviceIp }
            if (targetDevice != null) {
                Logger.d("MainActivity", "发送应用启动请求: $packageName 到 ${targetDevice.displayName}, displayId: $displayId")
                AppLaunchManager.sendAppLaunchRequest(this, deviceManager, targetDevice, packageName, displayId)
            } else {
                Logger.w("MainActivity", "未找到目标设备: $deviceIp")
            }
        }

        DeveloperModeActivity.initLogConfig(this)
        DeveloperModeActivity.initDebugUiConfig(this)

        // 调试入口：通过 intent 直接触发超级岛测试样本（仅 debug 构建生效，见 SuperIslandTestLauncher）
        SuperIslandTestLauncher.tryLaunchFromIntent(this)

        PermissionHelper.AppForegroundDetector.initialize(this)

        WindowCompat.setDecorFitsSystemWindows(this.window, false)
        this.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            val navigator = rememberNavigator(Route.Main)
            MainActivityContent(navigator)
        }

        if (!permissions.ensurePermissionsOrLaunchGuide()) {
            return
        }

        screenCaptureCoordinator.registerProjectionRequestCallback()

        // 后台初始化，避免阻塞 UI 线程
        lifecycleScope.launch(Dispatchers.Default) {
            val deviceManager = DeviceConnectionManager.getInstance(this@MainActivity)
            DeviceInfoManager.generateDeviceInfoFile(this@MainActivity, deviceManager.localUuid)
            LiveUpdatesNotificationManager.initialize(this@MainActivity)
            NotificationRepository.init(this@MainActivity)
            AppRepository.loadApps(this@MainActivity)
            permissions.startServicesAndUpdateBanner()
        }
    }
}
