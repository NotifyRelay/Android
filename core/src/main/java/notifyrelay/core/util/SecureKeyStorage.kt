package notifyrelay.core.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureKeyStorage {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "notifyrelay_secure_storage_master"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREF_NAME = "secure_key_storage"

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        return if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    fun encryptAndStore(context: Context, keyAlias: String, plainData: String) {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainData.toByteArray(Charsets.UTF_8))
        val out = ByteArray(1 + iv.size + encryptedBytes.size)
        out[0] = iv.size.toByte()
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(encryptedBytes, 0, out, 1 + iv.size, encryptedBytes.size)
        val encoded = Base64.encodeToString(out, Base64.NO_WRAP)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyAlias, encoded)
            .apply()
    }

    fun retrieveAndDecrypt(context: Context, keyAlias: String): String? {
        val encoded = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(keyAlias, null) ?: return null
        return try {
            val masterKey = getOrCreateMasterKey()
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = data[0].toInt() and 0xFF
            val iv = data.copyOfRange(1, 1 + ivLength)
            val ciphertext = data.copyOfRange(1 + ivLength, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.w("SecureKeyStorage", "Failed to decrypt stored secure value for alias=$keyAlias", e)
            null
        }
    }

    fun removeKey(context: Context, keyAlias: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(keyAlias)
            .apply()
    }
}
