package com.xzyht.notifyrelay.feature.clipboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Base64
import notifyrelay.base.util.Logger
import notifyrelay.core.util.SecureKeyStorage
import org.fcitx.fcitx5.android.common.ipc.IBroadcastPairingService
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object FcitxClipboardManager {
    private const val TAG = "Fcitx5广播剪切板"
    private const val FCITX5_PAIRING_ACTION = "org.fcitx.fcitx5.android.BROADCAST_PAIRING"
    private const val FCITX5_SERVICE_CLASS = "org.fcitx.fcitx5.android.BroadcastPairingService"
    private val FCITX5_PACKAGES =
        listOf(
            "org.fcitx.fcitx5.android",
            "org.fcitx.fcitx5.android.debug",
        )
    private const val CLIPBOARD_KEY_ALIAS = "fcitx5_clipboard_key"

    var isPaired: Boolean = false
        private set

    private var pairingService: IBroadcastPairingService? = null
    private var bindingContext: Context? = null
    private var isBinding = false

    private var pendingPairingRequest: Pair<String, (Boolean) -> Unit>? = null

    fun restorePairedState(context: Context) {
        isPaired = SecureKeyStorage
            .retrieveAndDecrypt(context, CLIPBOARD_KEY_ALIAS) != null
    }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                pairingService = IBroadcastPairingService.Stub.asInterface(service)
                isBinding = false
                Logger.d(TAG, "已连接到 Fcitx5 配对服务")

                pendingPairingRequest?.let { (code, callback) ->
                    pendingPairingRequest = null
                    doRequestPairing(code, callback)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                pairingService = null
                isBinding = false
                Logger.d(TAG, "Fcitx5 配对服务已断开")
            }
        }

    fun bindService(context: Context): Boolean {
        if (pairingService != null || isBinding) return true
        val appContext = context.applicationContext
        bindingContext = appContext
        isBinding = true

        for (pkg in FCITX5_PACKAGES) {
            val intent =
                Intent(FCITX5_PAIRING_ACTION).apply {
                    component = ComponentName(pkg, FCITX5_SERVICE_CLASS)
                }
            try {
                val bound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                if (bound) {
                    Logger.d(TAG, "正在绑定 Fcitx5 配对服务 ($pkg)...")
                    return true
                }
            } catch (_: Exception) {
            }
        }
        isBinding = false
        Logger.e(TAG, "绑定 Fcitx5 配对服务失败：所有包名均不可用")
        return false
    }

    fun unbindService() {
        pendingPairingRequest = null
        bindingContext?.let { ctx ->
            try {
                ctx.unbindService(serviceConnection)
            } catch (e: Exception) {
                Logger.e(TAG, "解绑 Fcitx5 配对服务失败", e)
            }
        }
        pairingService = null
        bindingContext = null
        isBinding = false
    }

    fun requestPairing(
        context: Context,
        code: String,
        onResult: (Boolean) -> Unit,
    ) {
        if (pairingService != null) {
            doRequestPairing(code, onResult)
            return
        }
        pendingPairingRequest = code to onResult
        if (!bindService(context)) {
            pendingPairingRequest = null
            onResult(false)
        }
    }

    private fun doRequestPairing(
        code: String,
        onResult: (Boolean) -> Unit,
    ) {
        try {
            val ctx =
                bindingContext ?: run {
                    onResult(false)
                    return
                }
            val packageName = ctx.packageName
            val appName = ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
            val success = pairingService!!.requestPairing(code, packageName, appName)
            if (success) {
                val sharedKey = pairingService!!.getSharedKey(packageName)
                if (!sharedKey.isNullOrBlank()) {
                    SecureKeyStorage
                        .encryptAndStore(ctx, CLIPBOARD_KEY_ALIAS, sharedKey)
                    isPaired = true
                    Logger.d(TAG, "Fcitx5 配对成功，密钥已保存")
                } else {
                    Logger.w(TAG, "Fcitx5 配对成功但获取密钥失败")
                    isPaired = false
                    onResult(false)
                    return
                }
            } else {
                Logger.d(TAG, "Fcitx5 配对失败：配对码错误或已过期")
            }
            onResult(success)
        } catch (e: DeadObjectException) {
            Logger.e(TAG, "Fcitx5 服务已断开，清理后重新绑定")
            val ctx = bindingContext
            try {
                ctx?.unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            pairingService = null
            bindingContext = null
            isBinding = false
            pendingPairingRequest = code to onResult
            if (ctx != null) {
                bindService(ctx)
            } else {
                onResult(false)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Fcitx5 配对请求异常", e)
            onResult(false)
        }
    }

    fun revokePairing(context: Context): Boolean {
        var remoteSuccess = false
        try {
            if (pairingService == null) bindService(context)
            remoteSuccess = pairingService?.revokePairing(context.packageName) ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "远程撤销失败，仅清除本地状态", e)
        }
        isPaired = false
        SecureKeyStorage
            .removeKey(context, "fcitx5_clipboard_key")
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias("fcitx5_clipboard_imported")) {
                ks.deleteEntry("fcitx5_clipboard_imported")
            }
        } catch (_: Exception) {
        }
        try {
            unbindService()
        } catch (_: Exception) {
        }
        Logger.d(TAG, "Fcitx5 配对已撤销")
        return true
    }

    fun checkPairingStatus(context: Context): Boolean =
        try {
            if (pairingService == null) bindService(context)
            val result = pairingService?.isAppPaired(context.packageName) ?: false
            isPaired = result
            result
        } catch (e: Exception) {
            Logger.e(TAG, "检查 Fcitx5 配对状态失败", e)
            false
        }

    fun decryptClipboardData(
        context: Context,
        encryptedData: ByteArray,
    ): String? {
        return try {
            val secretKey =
                SecureKeyStorage
                    .retrieveAndDecrypt(context, "fcitx5_clipboard_key")
                    ?: return null

            if (encryptedData.size < 2) return null
            val ivLength = encryptedData[0].toInt() and 0xFF
            if (ivLength <= 0 || encryptedData.size <= 1 + ivLength) return null
            val iv = encryptedData.copyOfRange(1, 1 + ivLength)
            val ciphertext = encryptedData.copyOfRange(1 + ivLength, encryptedData.size)

            val keystoreKey = ensureClipboardKeyInKeystore(context, secretKey)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey, spec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.e(TAG, "解密 Fcitx5 剪贴板数据失败", e)
            null
        }
    }

    private fun ensureClipboardKeyInKeystore(
        context: Context,
        secretKeyBase64: String,
    ): SecretKey {
        val alias = "fcitx5_clipboard_imported"
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val newKeyBytes = Base64.decode(secretKeyBase64, Base64.NO_WRAP)
        // 检查现有 key 是否匹配，不匹配则替换
        if (keyStore.containsAlias(alias)) {
            val existingKey = keyStore.getKey(alias, null) as? SecretKey
            if (existingKey != null && existingKey.encoded.contentEquals(newKeyBytes)) {
                return existingKey
            }
            keyStore.deleteEntry(alias)
        }
        val originalKey = SecretKeySpec(newKeyBytes, "AES")
        val protectionParams =
            KeyProtection
                .Builder(
                    Cipher.DECRYPT_MODE,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        keyStore.setEntry(alias, KeyStore.SecretKeyEntry(originalKey), protectionParams)
        return keyStore.getKey(alias, null) as SecretKey
    }
}
