package com.xzyht.notifyrelay.ui.guide

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.xzyht.notifyrelay.servers.appslist.AppListHelper
import notifyrelay.base.util.PermissionHelper

internal data class GuidePermissionUiState(
    val notificationListener: Boolean = false,
    val queryApps: Boolean = false,
    val postNotifications: Boolean = false,
    val bluetoothConnect: Boolean = false,
    val manageExternalStorage: Boolean = false,
    val backgroundUnlimited: Boolean = false,
    val overlay: Boolean = false,
) {
    val requiredGranted: Boolean
        get() = notificationListener && queryApps && postNotifications
}

internal fun readGuidePermissionState(context: Context): GuidePermissionUiState {
    val enabledListeners =
        Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        )
    val hasNotification = enabledListeners?.split(":")?.any { entry ->
        entry.substringBefore("/") == context.packageName
    } == true

    // 与 PermissionHelper.checkAllPermissions 保持一致：MIUI/澎湃系统还需要
    // 显式授予 com.android.permission.GET_INSTALLED_APPS，否则主界面会再次跳回引导页。
    val isMiuiOrPengpai =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            try {
                val permissionInfo =
                    context.packageManager.getPermissionInfo(
                        "com.android.permission.GET_INSTALLED_APPS",
                        0,
                    )
                permissionInfo.packageName == "com.lbe.security.miui"
            } catch (_: Exception) {
                false
            }
    val canQueryApps =
        AppListHelper.canQueryApps(context) &&
            (
                !isMiuiOrPengpai ||
                    ContextCompat.checkSelfPermission(
                        context,
                        "com.android.permission.GET_INSTALLED_APPS",
                    ) == PackageManager.PERMISSION_GRANTED
            )

    return GuidePermissionUiState(
        notificationListener = hasNotification,
        queryApps = canQueryApps,
        postNotifications =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        bluetoothConnect = PermissionHelper.checkBluetoothConnectPermission(context),
        manageExternalStorage = PermissionHelper.checkManageExternalStoragePermission(context),
        backgroundUnlimited = PermissionHelper.checkBackgroundUnlimitedPermission(context),
        overlay = PermissionHelper.checkOverlayPermission(context),
    )
}
