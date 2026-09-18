package com.xzyht.notifyrelay.ui.pages.remoteapps

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.appslist.model.RemoteAppInfo
import com.xzyht.notifyrelay.ui.viewmodel.LocalAppInfo
import com.xzyht.notifyrelay.ui.viewmodel.RemoteAppsViewModel
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 上下文菜单宿主：把 [RemoteAppsPageState.showMenuForApp] 持有的擦除类型应用对象
 * 按三类 `when` 分支下转型后交给 [AppContextMenu]。
 */
@Composable
internal fun RemoteAppsMenuHost(
    showMenuForApp: Any?,
    context: Context,
    remoteViewModel: RemoteAppsViewModel,
    onDismiss: () -> Unit,
) {
    showMenuForApp?.let { app ->
        AppContextMenu(
            appName =
                when (app) {
                    is RemoteAppInfo -> app.appName
                    is LocalAppInfo -> app.appName
                    else -> ""
                },
            packageName =
                when (app) {
                    is RemoteAppInfo -> app.packageName
                    is LocalAppInfo -> app.packageName
                    else -> ""
                },
            isPinned =
                when (app) {
                    is RemoteAppInfo -> app.isPinned
                    else -> false
                },
            showPinButton = app is RemoteAppInfo,
            onDismiss = onDismiss,
            onPin =
                if (app is RemoteAppInfo) {
                    { remoteViewModel.pinApp(context, app.packageName) }
                } else {
                    null
                },
            onUnpin =
                if (app is RemoteAppInfo) {
                    { remoteViewModel.unpinApp(context, app.packageName) }
                } else {
                    null
                },
        )
    }
}

@Composable
internal fun AppContextMenu(
    appName: String,
    packageName: String,
    isPinned: Boolean,
    showPinButton: Boolean,
    onDismiss: () -> Unit,
    onPin: (() -> Unit)?,
    onUnpin: (() -> Unit)?,
) {
    val dismiss = LocalDismissState.current

    WindowDialog(
        title = appName,
        summary = packageName,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = "关闭",
                onClick = { dismiss?.invoke() },
            )
            if (showPinButton && onPin != null && onUnpin != null) {
                Spacer(modifier = Modifier.width(8.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = if (isPinned) "取消置顶" else "置顶",
                    onClick = {
                        if (isPinned) onUnpin() else onPin()
                        dismiss?.invoke()
                    },
                )
            }
        }
    }
}
