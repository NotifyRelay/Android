package com.xzyht.notifyrelay.servers.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import notifyrelay.base.util.Logger

class FcitxClipboardReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "Fcitx5广播剪切板"
        const val ACTION_CLIPBOARD_BROADCAST = "org.fcitx.fcitx5.android.action.CLIPBOARD_BROADCAST"
        const val EXTRA_ENCRYPTED_DATA = "encrypted_data"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_SENDER_PACKAGE = "sender_package"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLIPBOARD_BROADCAST) return

        val encryptedData = intent.getByteArrayExtra(EXTRA_ENCRYPTED_DATA) ?: run {
            Logger.w(TAG, "收到 Fcitx5 广播但无加密数据")
            return
        }
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0)
        val senderPackage = intent.getStringExtra(EXTRA_SENDER_PACKAGE) ?: return

        Logger.d(TAG, "收到 Fcitx5 剪贴板广播: sender=$senderPackage, timestamp=$timestamp")

        val decryptedText = FcitxClipboardManager.decryptClipboardData(context, encryptedData)
        if (decryptedText != null) {
            val deviceManager = DeviceConnectionManager.getInstance(context)
            ClipboardSyncManager.syncTextDirectly(deviceManager, decryptedText)
            Logger.d(TAG, "Fcitx5 剪贴板已转发至远端设备")
        }
    }
}
