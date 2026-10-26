package com.xzyht.notifyrelay.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xzyht.notifyrelay.feature.appslist.AppIconRepository
import com.xzyht.notifyrelay.feature.appslist.InstalledAppsRepository
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils
import com.xzyht.notifyrelay.feature.notification.superisland.config.SuperIslandConfigUtils.SpecInjectionMode
import com.xzyht.notifyrelay.feature.notification.superisland.replica.FloatingReplicaManager
import kotlinx.coroutines.launch

/**
 * 超级岛设置页的状态与操作承载。
 *
 * 界面只读取状态并调用动作方法，不再直接访问配置工具、复刻管理器与仓库。
 */
class SuperIslandSettingsViewModel : ViewModel() {
    var showSuperIsland by mutableStateOf(false)
        private set

    var floatingWindowEnabled by mutableStateOf(false)
        private set

    var notificationListEnabled by mutableStateOf(false)
        private set

    var specInjectionMode by mutableStateOf(SpecInjectionMode.NONE)
        private set

    fun load(context: Context) {
        showSuperIsland = SuperIslandConfigUtils.isRemoteSuperIslandDisplayEnabled(context)
        floatingWindowEnabled = SuperIslandConfigUtils.isFloatingWindowEnabled(context)
        notificationListEnabled = SuperIslandConfigUtils.isNotificationListMode(context)
        specInjectionMode = SuperIslandConfigUtils.getSpecInjectionMode(context)
    }

    fun setShowSuperIsland(
        context: Context,
        value: Boolean,
    ) {
        showSuperIsland = value
        SuperIslandConfigUtils.setRemoteSuperIslandDisplayEnabled(context, value)
        if (!value) {
            viewModelScope.launch { FloatingReplicaManager.dismissAllRemoteSuperIsland(context) }
        }
    }

    fun setFloatingWindowEnabled(
        context: Context,
        value: Boolean,
    ) {
        if (SuperIslandConfigUtils.setFloatingWindowEnabled(context, value)) {
            floatingWindowEnabled = value
            viewModelScope.launch { FloatingReplicaManager.switchSuperIslandChannel(context) }
        }
    }

    fun setNotificationListEnabled(
        context: Context,
        value: Boolean,
    ) {
        if (SuperIslandConfigUtils.setNotificationListMode(context, value)) {
            notificationListEnabled = value
            viewModelScope.launch { FloatingReplicaManager.switchSuperIslandChannel(context) }
        }
    }

    fun setSpecInjectionMode(
        context: Context,
        mode: SpecInjectionMode,
    ) {
        specInjectionMode = mode
        SuperIslandConfigUtils.setSpecInjectionMode(context, mode)
    }

    fun installedPackages(context: Context): Set<String> = runCatching { InstalledAppsRepository.getInstalledPackageNamesSync(context) }.getOrDefault(emptySet())

    fun loadIcon(
        context: Context,
        packageName: String,
        onResult: (android.graphics.Bitmap?) -> Unit,
    ) {
        viewModelScope.launch {
            onResult(runCatching { AppIconRepository.getAppIconAsync(context, packageName) }.getOrNull())
        }
    }
}
