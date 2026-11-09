package com.xzyht.notifyrelay.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xzyht.notifyrelay.ui.common.SetupSystemBars
import com.xzyht.notifyrelay.ui.devtools.NotificationFieldDumpPage
import notifyrelay.base.util.IntentUtils
import notifyrelay.shared.ui.common.NotifyRelayTheme
import notifyrelay.shared.ui.common.ScrollableTopAppBarPage
import notifyrelay.shared.ui.screen.DeveloperModeScreen

/**
 * 开发者选项页（Android 入口）。
 *
 * 页面主体复用公共端 [DeveloperModeScreen]；本类只提供页面容器与 Android 专属的
 * 通知字段转储页切换、引导页分支调试入口。
 *
 * 日志级别与调试开关的持久化初值由 `NotifyRelayApplication` 装载，本类不再重复读取。
 */
class DeveloperModeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先设置沉浸式虚拟键和状态栏
        androidx.core.view.WindowCompat
            .setDecorFitsSystemWindows(this.window, false)
        this.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            // 使用统一的主题
            NotifyRelayTheme(darkTheme = isDarkTheme) {
                // 设置系统栏外观
                SetupSystemBars(isDarkTheme)
                // 页内切换：主设置页 ↔ 通知字段转储页（不新增 Activity 与 Manifest 条目）
                var showNotificationDump by remember { mutableStateOf(false) }
                // 系统返回键：在转储页时先回到开发者选项主页面，而不是直接退出 Activity
                androidx.activity.compose.BackHandler(enabled = showNotificationDump) {
                    showNotificationDump = false
                }
                // 使用标准 TopAppBar 页面容器（内容区 colorScheme.background）
                ScrollableTopAppBarPage(
                    title = if (showNotificationDump) "通知字段转储" else "开发者选项",
                    onBack = {
                        if (showNotificationDump) {
                            showNotificationDump = false
                        } else {
                            finish()
                        }
                    },
                ) {
                    if (showNotificationDump) {
                        NotificationFieldDumpPage()
                    } else {
                        DeveloperModeScreen(
                            onOpenGuideBranch = { branch -> openGuideBranch(branch) },
                            onOpenDumpPage = { showNotificationDump = true },
                        )
                    }
                }
            }
        }
    }

    /** 以强制分支进入引导页，供开发者选项的多分支调试入口使用。 */
    private fun openGuideBranch(branch: String) {
        val intent = IntentUtils.createIntent(this, GuideActivity::class.java)
        intent.putExtra("fromInternal", true)
        intent.putExtra("forceBranch", branch)
        IntentUtils.startActivity(this, intent, true)
    }
}
