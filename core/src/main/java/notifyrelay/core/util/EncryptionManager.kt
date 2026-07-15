package notifyrelay.core.util

import android.content.Context
import android.security.keystore.KeyProtection
import android.util.Base64
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {

    private const val AES_DEVICE_SECRET_PREFIX = "aes_device_secret_"

    /**
     * 从 Android Keystore 中移除指定设备的 AES 密钥（旧版密钥清理）。
     */
    fun removeDeviceKey(uuid: String, context: Context) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
