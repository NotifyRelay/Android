package com.xzyht.notifyrelay.ui.activity

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ServiceManager
import notifyrelay.base.util.ToastUtils

/**
 * 权限检查与服务启动协调器
 *
 * 集中处理 `MainActivity` 的权限校验、引导页跳转与 `ServiceManager` 服务启动后的自启动横幅更新。
 *
 * 注意：`onCreate` 的 `startServicesAndUpdateBanner` 与 `onResume` 的 `checkPermissionsAndStartServices`
 * 职责虽相似（都调用 `ServiceManager.startAllServices`），但调用时机不同（前者只在创建时启动服务，
 * 后者每次回前台都重查权限），因此二者保持为两个独立入口，不做合并。
 */
internal class MainActivityPermissions(
    private val activity: MainActivity,
) {
    private val guideLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            activity.recreate()
        }

    /**
     * `onCreate` 阶段的权限检查：缺失时 Toast 提示并拉起引导页。
     *
     * @return `true` 表示权限齐全可继续；`false` 表示已拉起引导页，调用方应立即 `return`。
     */
    fun ensurePermissionsOrLaunchGuide(): Boolean {
        if (PermissionHelper.checkAllPermissions(activity)) return true
        ToastUtils.showShortToast(activity, "请先授权所有必要权限！")
        val intent = Intent(activity, GuideActivity::class.java)
        intent.putExtra("from", "MainActivity")
        guideLauncher.launch(intent)
        return false
    }

    /**
     * `onResume` 阶段：重置横幅 → 重查权限（缺失则跳引导页）→ 启动服务 → 更新横幅。
     */
    suspend fun checkPermissionsAndStartServices() {
        withContext(Dispatchers.Main) {
            activity.showAutoStartBanner.value = false
            activity.bannerMessage.value = null
        }

        val granted =
            withContext(Dispatchers.IO) {
                PermissionHelper.checkAllPermissions(activity)
            }
        if (!granted) {
            Logger.w("NotifyRelay", "必要权限未授权，跳转引导页")
            withContext(Dispatchers.Main) {
                val intent = Intent(activity, GuideActivity::class.java)
                intent.putExtra("from", "MainActivity")
                intent.putExtra("reauth", true)
                activity.startActivity(intent)
                activity.finish()
            }
            return
        }

        val result =
            withContext(Dispatchers.IO) {
                ServiceManager.startAllServices(activity)
            }
        val serviceStarted = result.first
        val errorMessage = result.second
        withContext(Dispatchers.Main) {
            if (errorMessage != null) {
                activity.showAutoStartBanner.value = true
                activity.bannerMessage.value = errorMessage
            }

            if (!serviceStarted) {
                activity.showAutoStartBanner.value = true
                activity.bannerMessage.value = AUTO_START_FAILED_BANNER
            }
        }
    }

    /**
     * `onCreate` 阶段：启动全部服务，并按结果更新自启动横幅。
     *
     * 调用方（`onCreate`）已在 `Dispatchers.Default` 协程内，故此处不再切换 IO 线程。
     */
    suspend fun startServicesAndUpdateBanner() {
        val result = ServiceManager.startAllServices(activity)
        val serviceStarted = result.first
        val errorMessage = result.second
        if (errorMessage != null) {
            withContext(Dispatchers.Main) {
                activity.showAutoStartBanner.value = true
                activity.bannerMessage.value = errorMessage
            }
        }

        if (!serviceStarted) {
            withContext(Dispatchers.Main) {
                activity.showAutoStartBanner.value = true
                activity.bannerMessage.value = AUTO_START_FAILED_BANNER
            }
        }
    }

    private companion object {
        /** 服务启动失败的统一横幅文案（原 `checkPermissionsAndStartServices` 与 `startServicesAndUpdateBanner` 重复字符串） */
        const val AUTO_START_FAILED_BANNER =
            "服务无法启动，可能因系统自启动/后台运行权限被拒绝。请前往系统设置手动允许自启动、后台运行和电池优化白名单，否则通知转发将无法正常工作。"
    }
}
