package notifyrelay.core.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
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
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setKeySize(256)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * 生成临时 ECDH 密钥对（内存级，不经过 Keystore）。
     * 用于配对码交换阶段的临时密钥协商，用完即弃。
     */
    fun generateEphemeralKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec(CURVE))
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * 将 ECDH 公钥编码为 Base64（65 字节未压缩点：0x04 || x || y）
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        val ecPublicKey = publicKey as ECPublicKey
        val w = ecPublicKey.w
        val xBytes = w.affineX.toByteArray()
        val yBytes = w.affineY.toByteArray()

        val pointBytes = ByteArray(65)
        pointBytes[0] = 0x04
        val xLen = xBytes.size
        val xStart = if (xLen > 32) xLen - 32 else 0
        val xPad = 32 - (xLen - xStart)
        System.arraycopy(xBytes, xStart, pointBytes, 1 + xPad, xLen - xStart)
        val yLen = yBytes.size
        val yStart = if (yLen > 32) yLen - 32 else 0
        val yPad = 32 - (yLen - yStart)
        System.arraycopy(yBytes, yStart, pointBytes, 33 + yPad, yLen - yStart)

        return Base64.encodeToString(pointBytes, Base64.NO_WRAP)
    }

    /**
     * 解码 Base64 编码的 ECDH 公钥（65 字节未压缩点）
     */
    fun decodePublicKey(publicKeyBase64: String): PublicKey {
        val pointBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        require(pointBytes.size == 65 && pointBytes[0] == 0x04.toByte()) { "Invalid ECDH public key format" }

        val keyFactory = KeyFactory.getInstance("EC")
        val ecPoint = ECPoint(
            java.math.BigInteger(1, pointBytes.copyOfRange(1, 33)),
            java.math.BigInteger(1, pointBytes.copyOfRange(33, 65))
        )
        // 直接从 CURVE 名称构造 ECParameterSpec，不触碰 Keystore
        val algoParams = java.security.AlgorithmParameters.getInstance("EC")
        algoParams.init(ECGenParameterSpec(CURVE))
        val ecParams = algoParams.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val pubKeySpec = ECPublicKeySpec(ecPoint, ecParams)
        return keyFactory.generatePublic(pubKeySpec)
    }

    /**
     * 将 ECDH 私钥（PKCS8 格式）编码为 Base64。
     * 用于将临时私钥序列化后传输或存储。
     */
    fun encodePrivateKey(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }

    /**
     * 解码 Base64 编码的 ECDH 私钥（PKCS8 格式）。
     */
    fun decodePrivateKey(privateKeyBase64: String): PrivateKey {
        val keyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    /**
     * 执行 ECDH 密钥协商，返回原始共享密钥字节数组。
     *
     * @param privateKey 私钥（可以是临时私钥或 Keystore 私钥）
     * @param remotePublicKeyBase64 远端公钥的 Base64 编码
     * @return 原始共享密钥字节数组
     */
    fun deriveRawSharedSecret(privateKey: PrivateKey, remotePublicKeyBase64: String): ByteArray {
        val remotePublicKey = decodePublicKey(remotePublicKeyBase64)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(remotePublicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * 获取公钥的 Base64 编码（未压缩点，65 字节：0x04 || x || y）
     * 与 PC 端 BouncyCastle 的 DecodePoint 兼容
     */
    fun getPublicKeyBase64(): String {
        return encodePublicKey(getOrCreateKeyPair().public)
    }

    /**
     * 执行 ECDH 密钥协商，返回 SHA-256 哈希后的 32 字节（Base64 编码）。
     * 使用 Keystore 中的长期私钥进行协商。
     *
     * @param remotePublicKeyBase64 远端公钥的 Base64 编码（65 字节未压缩点）
     * @return Base64 编码的 32 字节共享密钥
     */
    fun generateSharedSecret(remotePublicKeyBase64: String): String {
        // 从 Keystore 获取私钥
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey

        val sharedSecret = deriveRawSharedSecret(privateKey, remotePublicKeyBase64)

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
