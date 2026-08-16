package com.xzyht.notifyrelay.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import com.xzyht.notifyrelay.ui.common.NotifyRelayTheme
import com.xzyht.notifyrelay.ui.common.ProvideNavigationEventDispatcherOwner
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import com.xzyht.notifyrelay.ui.guide.GuideScreen
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ThemeSettingsManager
import notifyrelay.data.StorageManager

class GuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isFirstLaunch = StorageManager.getBoolean(this, "isFirstLaunch", true, StorageManager.PrefsType.GENERAL)
        val fromInternal = intent.getBooleanExtra("fromInternal", false)
        val fromftp = intent.getBooleanExtra("fromftp", false)

        // 仅冷启动且权限满足时自动跳主界面，应用内跳转（fromInternal=true）始终渲染引导页。
        // 流程仿照 HyperCeiler：欢迎页 -> 使用须知 -> 权限设置 -> 基础设置（设置总览 + 多个设置页）-> 完成页。
        if (!fromInternal && PermissionHelper.checkAllPermissions(this) && !isFirstLaunch) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // 沉浸式虚拟键，内容延伸到手势提示线区域
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ProvideNavigationEventDispatcherOwner {
                val appContext = LocalContext.current
                val systemDarkTheme = isSystemInDarkTheme()
                var themeBaseIndex by remember {
                    mutableIntStateOf(ThemeSettingsManager.getThemeBaseIndex(appContext))
                }
                val isDarkTheme =
                    when (themeBaseIndex) {
                        ThemeSettingsManager.THEME_LIGHT -> false
                        ThemeSettingsManager.THEME_DARK -> true
                        else -> systemDarkTheme
                    }

                NotifyRelayTheme(darkTheme = isDarkTheme) {
                    SetupSystemBars(isDarkTheme)
                    GuideScreen(
                        themeBaseIndex = themeBaseIndex,
                        onThemeChanged = { newIndex ->
                            ThemeSettingsManager.setThemeBaseIndex(appContext, newIndex)
                            themeBaseIndex = newIndex
                        },
                        onContinue = {
                            // 首次启动后标记为已启动
                            StorageManager.putBoolean(this@GuideActivity, "isFirstLaunch", false, StorageManager.PrefsType.GENERAL)

                            if (fromftp) {
                                // 如果是从 FTP 请求跳转过来的，尝试重新启动 FTP 服务
                                Logger.d("GuideActivity", "从 FTP 请求跳转，尝试重新启动 FTP 服务")
                            }

                            startActivity(Intent(this@GuideActivity, MainActivity::class.java))
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 当从系统设置页面返回时，刷新权限状态
        GuideScreen.refreshTrigger++

        // 检查是否从 FTP 请求跳转过来，并且已经获取了文件管理权限
        val fromftp = intent.getBooleanExtra("fromftp", false)
        if (fromftp && PermissionHelper.checkManageExternalStoragePermission(this)) {
            Logger.d("GuideActivity", "从 FTP 请求跳转，已经获取文件管理权限，尝试重新启动 FTP 服务")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    object GuideScreen {
        // 用于触发刷新
        var refreshTrigger by mutableIntStateOf(0)
    }
}
