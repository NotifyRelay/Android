package io.github.miuzarte.scrcpyforandroid.services

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

object AppWakeLocks {
    private lateinit var appContext: Context
    private lateinit var powerManager: PowerManager

    fun init(context: Context) {
        appContext = context.applicationContext
        powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("WakelockTimeout")
    fun acquire() {
        if (wakeLock != null) return

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${appContext.packageName}:scrcpy-wakelock",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    fun release() {
        wakeLock?.runCatching { release() }
        wakeLock = null
    }
}
