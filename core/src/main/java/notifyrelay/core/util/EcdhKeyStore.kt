package notifyrelay.core.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore ECDH 密钥对管理器
 *
 * 使用 Android Keystore 系统生成 secp256r1 (P-256) 椭圆曲线密钥对，
 * 私钥始终保存在硬件安全环境中，不可导出。
 * 提供公钥导出（65 字节未压缩点，Base64 编码）和 ECDH 密钥协商功能。
 */
object EcdhKeyStore {
    private const val KEY_ALIAS = "notifyrelay_ecdh_p256"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val CURVE = "secp256r1"

    /**
     * 从 Keystore 获取或生成 ECDH 密钥对
     */
    fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) ?: return generateKeyPair()
            val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return generateKeyPair()
            return KeyPair(publicKey, privateKey as java.security.PrivateKey)
        }
        return generateKeyPair()
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE
        )
        val purposes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            KeyProperties.PURPOSE_AGREE_KEY
        } else {
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            purposes
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setKeySize(256)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * 获取公钥的 Base64 编码（未压缩点，65 字节：0x04 || x || y）
     * 与 PC 端 BouncyCastle 的 DecodePoint 兼容
     */
    fun getPublicKeyBase64(): String {
        val keyPair = getOrCreateKeyPair()
        val ecPublicKey = keyPair.public as java.security.interfaces.ECPublicKey
        val w = ecPublicKey.w
        val xBytes = w.affineX.toByteArray()
        val yBytes = w.affineY.toByteArray()

        // 构造未压缩点: 0x04 || x (32字节, 补0前缀) || y (32字节, 补0前缀)
        val pointBytes = ByteArray(65)
        pointBytes[0] = 0x04  // 未压缩标识
        // x 坐标, 固定32字节大端, 补零
        val xLen = xBytes.size
        val xStart = if (xLen > 32) xLen - 32 else 0
        val xPad = 32 - (xLen - xStart)
        System.arraycopy(xBytes, xStart, pointBytes, 1 + xPad, xLen - xStart)
        // y 坐标
        val yLen = yBytes.size
        val yStart = if (yLen > 32) yLen - 32 else 0
        val yPad = 32 - (yLen - yStart)
        System.arraycopy(yBytes, yStart, pointBytes, 33 + yPad, yLen - yStart)

        return Base64.encodeToString(pointBytes, Base64.NO_WRAP)
    }

    /**
     * 执行 ECDH 密钥协商，返回 SHA-256 哈希后的 32 字节（Base64 编码）
     *
     * @param remotePublicKeyBase64 远端公钥的 Base64 编码（65 字节未压缩点）
     * @return Base64 编码的 32 字节共享密钥
     */
    fun generateSharedSecret(remotePublicKeyBase64: String): String {
        val keyPair = getOrCreateKeyPair()
        // 解码远端公钥的未压缩点
        val remotePointBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        require(remotePointBytes.size == 65 && remotePointBytes[0] == 0x04.toByte()) {
            "Invalid ECDH public key format"
        }
        // 构造 EC 公钥
        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val ecPoint = java.security.spec.ECPoint(
            java.math.BigInteger(1, remotePointBytes.copyOfRange(1, 33)),
            java.math.BigInteger(1, remotePointBytes.copyOfRange(33, 65))
        )
        val paramSpec = keyFactory.getKeySpec(keyPair.public, java.security.spec.ECPublicKeySpec::class.java)
        val params = (paramSpec as java.security.spec.ECPublicKeySpec).params
        val remotePublicKeySpec = java.security.spec.ECPublicKeySpec(ecPoint, params)
        val remotePublicKey = keyFactory.generatePublic(remotePublicKeySpec)

        // ECDH 密钥协商
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        // 从 Keystore 获取私钥
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(remotePublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // HKDF-SHA256 派生
        val mac = Mac.getInstance("HmacSHA256")
        val salt = ByteArray(32) { 0.toByte() }
        val keySpec = SecretKeySpec(salt, "HmacSHA256")
        mac.init(keySpec)
        val prk = mac.doFinal(sharedSecret)
        // HKDF expand with protocol context info（两端一致）
        val info = "NotifyRelay-ECDH-v1".toByteArray(Charsets.UTF_8)
        mac.reset()
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()

        return Base64.encodeToString(okm, Base64.NO_WRAP)
    }

    /**
     * 检查是否已有密钥对
     */
    fun hasKeyPair(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        return keyStore.containsAlias(KEY_ALIAS)
    }

    /**
     * 删除旧密钥对
     */
    fun deleteKeyPair() {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        keyStore.deleteEntry(KEY_ALIAS)
    }
}
