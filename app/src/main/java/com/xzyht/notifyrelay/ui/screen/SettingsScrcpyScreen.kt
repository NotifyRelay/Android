package com.xzyht.notifyrelay.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.xzyht.notifyrelay.util.saveScrcpyServerUri
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyRootScreen
import io.github.miuzarte.scrcpyforandroid.pages.ScrcpyScreenHost
import notifyrelay.base.log.Logger
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
            saveScrcpyServerUri(context, uri, "SettingsScrcpyScreen")
        }
    SettingsSubPage("屏幕镜像") {
        ScrcpyScreenHost(
            startScreen = ScrcpyRootScreen.Settings,
            onPickServer = { serverPicker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) },
            onExit = { navigator.pop() },
        )
    }
}
