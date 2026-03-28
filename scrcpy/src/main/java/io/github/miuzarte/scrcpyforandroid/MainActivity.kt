package io.github.miuzarte.scrcpyforandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.miuzarte.scrcpyforandroid.pages.MainPage
import io.github.miuzarte.scrcpyforandroid.pages.ShortcutLaunchActivity
import notifyrelay.data.config.ScrcpyDefaults

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceIp = intent?.getStringExtra("shortcut_device_ip")
        val deviceName = intent?.getStringExtra("shortcut_device_name")

        if (!deviceIp.isNullOrBlank()) {
            ShortcutLaunchActivity.startFullscreenControl(
                context = this,
                ip = deviceIp,
                port = ScrcpyDefaults.ADB_PORT,
                name = deviceName ?: deviceIp
            )
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            MainPage()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val deviceIp = intent.getStringExtra("shortcut_device_ip")
        val deviceName = intent.getStringExtra("shortcut_device_name")

        if (!deviceIp.isNullOrBlank()) {
            ShortcutLaunchActivity.startFullscreenControl(
                context = this,
                ip = deviceIp,
                port = ScrcpyDefaults.ADB_PORT,
                name = deviceName ?: deviceIp
            )
        }
    }
}
