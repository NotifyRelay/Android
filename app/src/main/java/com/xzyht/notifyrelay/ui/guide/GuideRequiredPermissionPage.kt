package com.xzyht.notifyrelay.ui.guide

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import notifyrelay.base.util.IntentUtils
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideRequiredPermissionPage(
    permissionState: GuidePermissionUiState,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MiuixTheme.colorScheme

    fun showToast(message: String) {
        ToastUtils.showShortToast(context, message)
    }

    fun openNotificationListenerSettings() {
        showToast("请开启通知访问权限")
        IntentUtils.startActivity(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, addNewTaskFlag = true)
    }

    fun requestQueryAppsPermission() {
        try {
            val isMiuiOrPengpai = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                try {
                    val permissionInfo = context.packageManager.getPermissionInfo("com.android.permission.GET_INSTALLED_APPS", 0)
                    permissionInfo != null && permissionInfo.packageName == "com.lbe.security.miui"
                } catch (_: Exception) {
                    false
                }
            if (isMiuiOrPengpai) {
                if (ContextCompat.checkSelfPermission(context, "com.android.permission.GET_INSTALLED_APPS") != PackageManager.PERMISSION_GRANTED) {
                    (context as? Activity)?.let { act ->
                        ActivityCompat.requestPermissions(
                            act,
                            arrayOf("com.android.permission.GET_INSTALLED_APPS"),
                            999
                        )
                        showToast("已请求应用列表权限，请在弹窗中允许")
                    } ?: run {
                        showToast("请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
                    }
                } else {
                    showToast("已获得应用列表权限")
                }
            } else {
                showToast("请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
                IntentUtils.startActivity(
                    context,
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                    true
                )
            }
        } catch (_: Exception) {
            showToast("请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
            IntentUtils.startActivity(
                context,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
                true
            )
        }
    }

    fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showToast("请求通知发送权限")
            (context as? Activity)?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        } else {
            showToast("请在系统设置中开启通知权限")
        }
    }

    fun openSelfStartSettings() {
        showToast("请在应用详情页启用自启动权限")
        IntentUtils.startActivity(
            context,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
            true
        )
    }

    val requiredChecks = buildList {
        add(permissionState.notificationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(permissionState.postNotifications)
        }
        add(permissionState.queryApps)
    }
    val requiredGrantedCount = requiredChecks.count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GuidePageHeader(
            stepLabel = "2 / 6",
            title = "必要权限",
            subtitle = if (permissionState.requiredGranted) {
                "所有必要权限已开启，可以继续下一步"
            } else {
                "通知转发依赖以下权限，请逐项开启（$requiredGrantedCount/${requiredChecks.size}）"
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            GuideSectionLabel(
                title = "必要权限",
                description = "缺少任一项都会影响通知读取、应用识别或后台服务运行"
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    GuidePermissionItem(
                        title = "通知访问权限",
                        summary = if (permissionState.notificationListener) {
                            "已允许读取通知内容，用于跨设备转发"
                        } else {
                            "用于读取通知内容，实现核心转发功能"
                        },
                        granted = permissionState.notificationListener,
                        onClick = ::openNotificationListenerSettings
                    )
                    GuidePermissionItem(
                        title = "应用列表权限",
                        summary = if (permissionState.queryApps) {
                            "已允许查询本机已安装应用，可辅助通知跳转"
                        } else {
                            "用于发现本机已安装应用，辅助通知跳转"
                        },
                        granted = permissionState.queryApps,
                        onClick = ::requestQueryAppsPermission
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        GuidePermissionItem(
                            title = "通知发送权限",
                            summary = if (permissionState.postNotifications) {
                                "已允许发送本地通知"
                            } else {
                                "用于发送本地通知，部分功能需要开启"
                            },
                            granted = permissionState.postNotifications,
                            onClick = ::requestPostNotificationPermission
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 自启动权限无法可靠读取状态，按需求直接使用 ArrowPreference 引导确认。
            ArrowPreference(
                title = "自启动权限",
                summary = "必选项：部分系统无法直接读取状态。用于保证通知监听服务在后台稳定运行，请点击前往应用详情确认并开启。",
                onClick = ::openSelfStartSettings
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        GuidePageFooter(
            hint = if (permissionState.requiredGranted) null else "完成必要权限授权后，按钮会自动变为可点击状态",
            nextText = if (permissionState.requiredGranted) "下一步" else "请先完成必要权限",
            nextEnabled = permissionState.requiredGranted,
            onBack = onBack,
            onNext = onNext,
            hintColor = colorScheme.error
        )
    }
}
