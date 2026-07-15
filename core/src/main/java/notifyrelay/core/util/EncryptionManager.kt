package notifyrelay.core.util

import android.content.Context
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {

    // =================== AES加密实现 ===================
    private object AESEncryption {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        fun generateKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
            keyGenerator.init(256)
            return keyGenerator.generateKey()
        }

        fun keyToString(key: SecretKey): String {
            return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        }

        fun stringToKey(keyString: String): SecretKey {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            return SecretKeySpec(keyBytes, ALGORITHM)
        }

        fun encrypt(data: String, key: String): String {
            val secretKey = stringToKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            val out = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, out, iv.size, encryptedBytes.size)
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun decrypt(encryptedData: String, key: String): String {
            val secretKey = stringToKey(key)
            val data = Base64.decode(encryptedData, Base64.NO_WRAP)
            if (data.size < GCM_IV_LENGTH) throw IllegalArgumentException("Invalid encrypted data")
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val cipherBytes = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        }

        fun hkdfDeriveKey(ikm: ByteArray, info: String): String {
            val prk = hkdfExtract(null, ikm)
            val okm = hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), 32)
            return Base64.encodeToString(okm, Base64.NO_WRAP)
        }

        private fun hkdfExtract(salt: ByteArray?, ikm: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            val realSalt = salt ?: ByteArray(32) { 0.toByte() }
            val keySpec = SecretKeySpec(realSalt, "HmacSHA256")
            mac.init(keySpec)
            return mac.doFinal(ikm)
        }

        private fun hkdfExpand(prk: ByteArray, info: ByteArray, len: Int): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            val keySpec = SecretKeySpec(prk, "HmacSHA256")
            mac.init(keySpec)
            val hashLen = 32
            val n = (len + hashLen - 1) / hashLen
            var t = ByteArray(0)
            val okm = ByteArray(len)
            var copied = 0
            for (i in 1..n) {
                mac.reset()
                mac.update(t)
                mac.update(info)
                mac.update(i.toByte())
                t = mac.doFinal()
                val toCopy = Math.min(hashLen, len - copied)
                System.arraycopy(t, 0, okm, copied, toCopy)
                copied += toCopy
            }
            return okm
        }
    }

    // =================== RSA加密实现 ===================
    private object RSAEncryption {
        private const val ALGORITHM = "RSA"
        private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val KEY_SIZE = 2048

        fun generateKeyPair(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
            keyPairGenerator.initialize(KEY_SIZE)
            return keyPairGenerator.generateKeyPair()
        }

        fun publicKeyToString(publicKey: PublicKey): String {
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        }

        fun privateKeyToString(privateKey: PrivateKey): String {
            return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        }

        fun stringToPublicKey(publicKeyString: String): PublicKey {
            val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            return keyFactory.generatePublic(keySpec)
        }

        fun stringToPrivateKey(privateKeyString: String): PrivateKey {
            val keyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            return keyFactory.generatePrivate(keySpec)
        }

        fun encryptWithPublicKey(data: String, publicKey: PublicKey): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        }

        fun decryptWithPrivateKey(encryptedData: String, privateKey: PrivateKey): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val encryptedBytes = Base64.decode(encryptedData, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        }
    }

    // =================== AES加密公共接口 ===================

    /**
     * 使用 AES 密钥加密数据
     */
    fun encrypt(data: String, key: String): String {
        return AESEncryption.encrypt(data, key)
    }

    /**
     * 使用 AES 密钥解密数据
     */
    fun decrypt(encryptedData: String, key: String): String {
        return AESEncryption.decrypt(encryptedData, key)
    }

    /**
     * 使用 HKDF-SHA256 从原始密钥材料派生 AES-256 密钥（Base64 编码）。
     */
    fun hkdfDeriveKey(ikm: ByteArray, info: String = "pairing-code-encryption"): String {
        return AESEncryption.hkdfDeriveKey(ikm, info)
    }

    // =================== RSA专用方法 ===================

    fun generateRSAKeyPair(): KeyPair {
        return RSAEncryption.generateKeyPair()
    }

    fun encryptWithRSAPublicKey(data: String, publicKey: PublicKey): String {
        return RSAEncryption.encryptWithPublicKey(data, publicKey)
    }

    fun decryptWithRSAPrivateKey(encryptedData: String, privateKey: PrivateKey): String {
        return RSAEncryption.decryptWithPrivateKey(encryptedData, privateKey)
    }

    fun rsaPublicKeyToString(publicKey: PublicKey): String {
        return RSAEncryption.publicKeyToString(publicKey)
    }

    fun rsaPrivateKeyToString(privateKey: PrivateKey): String {
        return RSAEncryption.privateKeyToString(privateKey)
    }

    fun stringToRSAPublicKey(publicKeyString: String): PublicKey {
        return RSAEncryption.stringToPublicKey(publicKeyString)
    }

    fun stringToRSAPrivateKey(privateKeyString: String): PrivateKey {
        return RSAEncryption.stringToPrivateKey(privateKeyString)
    }

    // =================== AES专用方法 ===================

    fun generateAESKey(): SecretKey {
        return AESEncryption.generateKey()
    }

    fun aesKeyToString(key: SecretKey): String {
        return AESEncryption.keyToString(key)
    }

    fun stringToAESKey(keyString: String): SecretKey {
        return AESEncryption.stringToKey(keyString)
    }

    // =================== Android Keystore AES 密钥管理 ===================

    private val AES_DEVICE_SECRET_PREFIX = "aes_device_secret_"
    private val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private val GCM_IV_LENGTH = 12

    /**
     * 将设备 sharedSecret（Base64 编码的 AES-256 密钥）导入 Android Keystore。
     */
    fun importAesKeyToKeystore(context: Context, uuid: String, base64Key: String) {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        require(keyBytes.size == 32) { "Invalid AES-256 key length: ${keyBytes.size} for uuid=$uuid, expected 32 bytes" }
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
        val secretKey = SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES)
        val protectionParams = KeyProtection.Builder(
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyStore.setEntry(alias, KeyStore.SecretKeyEntry(secretKey), protectionParams)
    }

    /**
     * 使用 Android Keystore 中存储的设备密钥加密数据。
     */
    fun encryptWithDeviceKey(data: String, uuid: String, context: Context): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        val secretKey = keyStore.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Keystore key not found for device $uuid")
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, out, iv.size, encryptedBytes.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /**
     * 使用 Android Keystore 中存储的设备密钥解密数据。
     */
    fun decryptWithDeviceKey(encryptedData: String, uuid: String, context: Context): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        val secretKey = keyStore.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Keystore key not found for device $uuid")
        val data = Base64.decode(encryptedData, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * 从 Android Keystore 中移除指定设备的 AES 密钥。
     */
    fun removeDeviceKey(uuid: String, context: Context) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * 检查指定设备的 AES 密钥是否已导入 Android Keystore。
     */
    fun hasDeviceKey(uuid: String, context: Context): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.containsAlias(AES_DEVICE_SECRET_PREFIX + uuid)
    }

}
