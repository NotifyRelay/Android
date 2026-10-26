package com.xzyht.notifyrelay.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyRootScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyScreenHost
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyUiViewModel
import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyPreferenceKeys
import notifyrelay.shared.ui.navigation.LocalNavigator
import notifyrelay.shared.ui.screen.SettingsSubPage

@Composable
fun SettingsScrcpyScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val serverPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure { e ->
                Logger.e("SettingsScrcpyScreen", "takePersistableUriPermission 失败: uri=$uri", e)
            }
            runCatching {
                val uriString = uri.toString()
                Storage.putString(
                    ScrcpyPreferenceKeys.CUSTOM_SERVER_URI,
                    uriString,
                    Storage.PrefsType.SCRCPY,
                )
                val app = context.applicationContext as android.app.Application
                ScrcpyUiViewModel.getInstance(app).customServerUri = uriString
            }.onFailure { e ->
                Logger.e("SettingsScrcpyScreen", "scrcpy server URI 保存失败: uri=$uri", e)
            }
        }
    SettingsSubPage("屏幕镜像") {
        ScrcpyScreenHost(
            startScreen = ScrcpyRootScreen.Settings,
            onPickServer = { serverPicker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) },
            onExit = { navigator.pop() },
        )
    }
}
