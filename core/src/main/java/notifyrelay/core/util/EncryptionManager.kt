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

/**
 * 统一加密管理器
 *
 * 整合 AES 与 RSA 两种加密算法的工具类。提供密钥生成、加解密、密钥与字符串互转以及
 * 生成共享密钥等常用方法。
 *
 * 注意：
 * - AES 方法使用对称加密，适用于大数据量和性能敏感场景；RSA 为非对称加密，适用于密钥交换或
 *   对安全性要求极高的场景。
 * - 本类将 AES 与 RSA 的实现封装在私有对象中，公共方法根据当前的加密类型（可通过
 *   [setEncryptionType] 修改）来选择行为；也提供 RSA/AES 的专用方法供需要时直接调用。
 */
object EncryptionManager {

    /**
     * 支持的加密类型枚举
     *
     * AES - 对称加密（推荐）
     * RSA - 非对称加密（最高安全）
     */
    enum class EncryptionType {
        ECDH,   // ECDH 密钥协商（推荐）
        AES,    // AES对称加密
        RSA     // RSA非对称加密（最高安全）
    }

    // 当前使用的加密类型（默认ECDH）
    private var currentEncryptionType: EncryptionType = EncryptionType.ECDH

    // =================== AES加密实现 ===================
    private object AESEncryption {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // bits
        private const val GCM_IV_LENGTH = 12 // bytes

        /**
         * 生成一个新的 AES 对称密钥
         *
         * @return 生成的 [SecretKey] 对象，密钥长度为 256 位（若平台不支持 256 则可能抛出异常）
         */
        fun generateKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
            keyGenerator.init(256)
            return keyGenerator.generateKey()
        }

        /**
         * 将 AES 密钥转换为 Base64 编码的字符串
         *
         * @param key 要编码的 [SecretKey]
         * @return Base64 编码的密钥字符串（无换行）
         */
        fun keyToString(key: SecretKey): String {
            return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        }

        /**
         * 将 Base64 编码的密钥字符串还原为 [SecretKey]
         *
         * @param keyString Base64 编码的密钥字符串（无换行）
         * @return 还原后的 [SecretKey]
         */
        fun stringToKey(keyString: String): SecretKey {
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            return SecretKeySpec(keyBytes, ALGORITHM)
        }

        /**
         * 使用 AES 加密数据
         *
         * @param data 要加密的明文字符串，使用 UTF-8 编码
         * @param key Base64 编码的 AES 密钥字符串（通过 [keyToString] 或 [generateSharedSecret] 获取）
         * @return Base64 编码的密文字符串（无换行）
         */
        fun encrypt(data: String, key: String): String {
            val secretKey = stringToKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            // 输出格式：IV || ciphertext (ciphertext 包含 tag)
            val out = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, out, iv.size, encryptedBytes.size)
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        /**
         * 使用 AES 解密数据
         *
         * @param encryptedData Base64 编码的密文字符串（无换行）
         * @param key Base64 编码的 AES 密钥字符串
         * @return 解密后的明文字符串（UTF-8）
         */
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

        /**
         * 根据本地与远端的密钥字符串生成一个共享的 AES 密钥（Base64 编码）
         *
         * 此方法通过拼接并截取/填充得到固定长度（32 字节）的字节序列，然后返回其 Base64 编码。
         * 注意：该方法不是标准的密钥协商算法，仅用于简单场景的共享密钥生成；如需更高安全性，
         * 请使用基于 Diffie-Hellman 或密钥交换协议的实现。
         *
         * @param localKey 本地标识或密钥字符串（非 Base64 必需）
         * @param remoteKey 远端标识或密钥字符串（非 Base64 必需）
         * @return Base64 编码的 32 字节共享密钥字符串（无换行）
         */
        fun generateSharedSecret(localKey: String, remoteKey: String): String {
            // 使用 HKDF-SHA256 从 localKey||remoteKey 派生 32 字节密钥，确保双方一致
            val a = localKey
            val b = remoteKey
            val combined = if (a < b) a + b else b + a
            val ikm = combined.toByteArray(Charsets.UTF_8)
            val prk = hkdfExtract(null, ikm)
            val okm = hkdfExpand(prk, "shared-secret".toByteArray(Charsets.UTF_8), 32)
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

        /**
         * 使用 HKDF-SHA256 从原始密钥材料派生 AES-256 密钥（Base64 编码）。
         *
         * @param ikm 输入密钥材料
         * @param info 上下文区分信息，防止不同用途派生相同密钥
         * @return Base64 编码的 32 字节 AES 密钥
         */
        fun hkdfDeriveKey(ikm: ByteArray, info: String): String {
            val prk = hkdfExtract(null, ikm)
            val okm = hkdfExpand(prk, info.toByteArray(Charsets.UTF_8), 32)
            return Base64.encodeToString(okm, Base64.NO_WRAP)
        }
    }

    // =================== RSA加密实现 ===================
    private object RSAEncryption {
        private const val ALGORITHM = "RSA"
        private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val KEY_SIZE = 2048

        /**
         * 生成 RSA 密钥对
         *
         * @return 生成的 [KeyPair]，包含公钥与私钥（2048 位）
         */
        fun generateKeyPair(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM)
            keyPairGenerator.initialize(KEY_SIZE)
            return keyPairGenerator.generateKeyPair()
        }

        /**
         * 将 RSA 公钥编码为 Base64 字符串
         *
         * @param publicKey 要编码的 [PublicKey]
         * @return Base64 编码的公钥字符串（无换行）
         */
        fun publicKeyToString(publicKey: PublicKey): String {
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        }

        /**
         * 将 RSA 私钥编码为 Base64 字符串
         *
         * @param privateKey 要编码的 [PrivateKey]
         * @return Base64 编码的私钥字符串（无换行）
         */
        fun privateKeyToString(privateKey: PrivateKey): String {
            return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        }

        /**
         * 将 Base64 编码的公钥字符串还原为 [PublicKey]
         *
         * @param publicKeyString Base64 编码的公钥字符串（无换行）
         * @return 还原后的 [PublicKey]
         */
        fun stringToPublicKey(publicKeyString: String): PublicKey {
            val keyBytes = Base64.decode(publicKeyString, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            return keyFactory.generatePublic(keySpec)
        }

        /**
         * 将 Base64 编码的私钥字符串还原为 [PrivateKey]
         *
         * @param privateKeyString Base64 编码的私钥字符串（无换行）
         * @return 还原后的 [PrivateKey]
         */
        fun stringToPrivateKey(privateKeyString: String): PrivateKey {
            val keyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            return keyFactory.generatePrivate(keySpec)
        }

        /**
         * 使用 RSA 公钥对数据加密
         *
         * @param data 要加密的明文字符串（UTF-8）
         * @param publicKey 用于加密的 [PublicKey]
         * @return Base64 编码的密文字符串（无换行）
         */
        fun encryptWithPublicKey(data: String, publicKey: PublicKey): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        }

        /**
         * 使用 RSA 私钥对密文解密
         *
         * @param encryptedData Base64 编码的密文字符串（无换行）
         * @param privateKey 用于解密的 [PrivateKey]
         * @return 解密后的明文字符串（UTF-8）
         */
        fun decryptWithPrivateKey(encryptedData: String, privateKey: PrivateKey): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val encryptedBytes = Base64.decode(encryptedData, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        }

        /**
         * 基于字符串生成一个简易的共享密钥表示（仅用于低安全性场景）
         *
         * @param localKey 本地标识或密钥字符串
         * @param remoteKey 远端标识或密钥字符串
         * @return 基于字符串计算的共享密钥（通过 hashCode 转为字符串）
         */
        fun generateSharedSecret(localKey: String, remoteKey: String): String {
            return (localKey + remoteKey).hashCode().toString()
        }
    }

    // =================== 公共接口 ===================

    /**
     * 设置当前使用的加密类型
     *
     * @param type 要设置的 [EncryptionType]
     */
    fun setEncryptionType(type: EncryptionType) {
        currentEncryptionType = type
    }

    /**
     * 获取当前使用的加密类型
     *
     * @return 当前的 [EncryptionType]
     */
    fun getCurrentEncryptionType(): EncryptionType {
        return currentEncryptionType
    }


    /**
     * 对数据进行加密
     *
     * 注意：当当前加密类型为 AES 时，使用 Base64 编码的 AES 密钥字符串进行加解密；
     * 当当前加密类型为 RSA 时，此通用方法不支持直接传入字符串形式的密钥（RSA 需要 [PublicKey]/[PrivateKey] 对象），
     * 会抛出 [UnsupportedOperationException]。
     *
     * @param data 要加密的明文字符串（UTF-8）
     * @param key AES 模式下为 Base64 编码的密钥字符串；RSA 模式下不适用（会抛出异常）
     * @return 加密后的 Base64 编码密文字符串
     * @throws UnsupportedOperationException 当当前类型为 RSA 时抛出
     */
    fun encrypt(data: String, key: String): String {
        return when (currentEncryptionType) {
            EncryptionType.ECDH -> AESEncryption.encrypt(data, key)
            EncryptionType.AES -> AESEncryption.encrypt(data, key)
            EncryptionType.RSA -> throw UnsupportedOperationException("RSA encryption requires PublicKey object")
        }
    }


    /**
     * 对密文进行解密
     *
     * @param encryptedData Base64 编码的密文字符串（无换行）
     * @param key AES 模式下为 Base64 编码的密钥字符串；RSA 模式下不适用（会抛出异常）
     * @return 解密后的明文字符串（UTF-8）
     * @throws UnsupportedOperationException 当当前类型为 RSA 时抛出
     */
    fun decrypt(encryptedData: String, key: String): String {
        return when (currentEncryptionType) {
            EncryptionType.ECDH -> AESEncryption.decrypt(encryptedData, key)
            EncryptionType.AES -> AESEncryption.decrypt(encryptedData, key)
            EncryptionType.RSA -> throw UnsupportedOperationException("RSA decryption requires PrivateKey object")
        }
    }


    /**
     * 根据当前加密类型生成共享密钥字符串
     *
     * @param localKey 本地标识或密钥字符串
     * @param remoteKey 远端标识或密钥字符串
     * @return 生成的共享密钥字符串（AES 返回 Base64 编码的 32 字节密钥，RSA 返回基于 hashCode 的字符串）
     */
    @Deprecated("Use generateSharedSecret(context, localKey, remoteKey) instead", ReplaceWith("generateSharedSecret(context, localKey, remoteKey)"))
    fun generateSharedSecret(localKey: String, remoteKey: String): String {
        return when (currentEncryptionType) {
            EncryptionType.ECDH -> throw UnsupportedOperationException("ECDH requires generateSharedSecret(context, localKey, remoteKey)")
            EncryptionType.AES -> AESEncryption.generateSharedSecret(localKey, remoteKey)
            EncryptionType.RSA -> RSAEncryption.generateSharedSecret(localKey, remoteKey)
        }
    }

    // =================== ECDH 方法 ===================

    /**
     * 执行 ECDH 密钥协商，返回 SHA-256 哈希后的 32 字节（Base64 编码）
     */
    fun ecdhGenerateSharedSecret(remotePublicKeyBase64: String): String {
        return EcdhKeyStore.generateSharedSecret(remotePublicKeyBase64)
    }

    /**
     * 获取本地 ECDH 公钥的 Base64 编码（65 字节未压缩点）
     */
    fun getEcdhPublicKeyBase64(): String {
        return EcdhKeyStore.getPublicKeyBase64()
    }

    /**
     * 检测公钥是否是 ECDH 格式
     * 旧格式（UUID hex，32 位十六进制字符串）返回 false
     * 新格式（Base64 编码的 EC 未压缩点）返回 true
     */
    enum class KeyFormat { ECDH, INVALID }

    fun detectKeyFormat(publicKey: String): KeyFormat {
        return try {
            val bytes = Base64.decode(publicKey, Base64.NO_WRAP)
            if (bytes.size == 65 && bytes[0] == 0x04.toByte()) KeyFormat.ECDH else KeyFormat.INVALID
        } catch (_: Exception) {
            KeyFormat.INVALID
        }
    }

    fun isEcdhFormat(publicKey: String): Boolean {
        return detectKeyFormat(publicKey) == KeyFormat.ECDH
    }

    /**
     * 格式感知的共享密钥派生
     *
     * 两端都是 ECDH 格式 → ECDH 协商 → SHA-256 → Base64
     * 两端都是旧 UUID 格式 → 旧 HKDF 逻辑
     *
     * @param context Android 上下文（用于 Keystore 访问）
     * @param localKey 本地公钥
     * @param remoteKey 远端公钥
     * @return Base64 编码的共享密钥
     */
    fun generateSharedSecret(context: android.content.Context, localKey: String, remoteKey: String): String {
        return ecdhGenerateSharedSecret(remoteKey)
    }

    // =================== RSA专用方法 ===================


    /**
     * 生成 RSA 密钥对（公私钥）
     *
     * @return 生成的 [KeyPair]
     */
    fun generateRSAKeyPair(): KeyPair {
        return RSAEncryption.generateKeyPair()
    }


    /**
     * 使用 RSA 公钥加密数据（专用方法）
     *
     * @param data 要加密的明文字符串（UTF-8）
     * @param publicKey 用于加密的 [PublicKey]
     * @return Base64 编码的密文字符串（无换行）
     */
    fun encryptWithRSAPublicKey(data: String, publicKey: PublicKey): String {
        return RSAEncryption.encryptWithPublicKey(data, publicKey)
    }


    /**
     * 使用 RSA 私钥解密数据（专用方法）
     *
     * @param encryptedData Base64 编码的密文字符串（无换行）
     * @param privateKey 用于解密的 [PrivateKey]
     * @return 解密后的明文字符串（UTF-8）
     */
    fun decryptWithRSAPrivateKey(encryptedData: String, privateKey: PrivateKey): String {
        return RSAEncryption.decryptWithPrivateKey(encryptedData, privateKey)
    }


    /**
     * 将 RSA 公钥编码为字符串（Base64）
     *
     * @param publicKey 要编码的 [PublicKey]
     * @return Base64 编码的公钥字符串（无换行）
     */
    fun rsaPublicKeyToString(publicKey: PublicKey): String {
        return RSAEncryption.publicKeyToString(publicKey)
    }


    /**
     * 将 RSA 私钥编码为字符串（Base64）
     *
     * @param privateKey 要编码的 [PrivateKey]
     * @return Base64 编码的私钥字符串（无换行）
     */
    fun rsaPrivateKeyToString(privateKey: PrivateKey): String {
        return RSAEncryption.privateKeyToString(privateKey)
    }


    /**
     * 将 Base64 编码的公钥字符串还原为 [PublicKey]
     *
     * @param publicKeyString Base64 编码的公钥字符串（无换行）
     * @return 还原后的 [PublicKey]
     */
    fun stringToRSAPublicKey(publicKeyString: String): PublicKey {
        return RSAEncryption.stringToPublicKey(publicKeyString)
    }


    /**
     * 将 Base64 编码的私钥字符串还原为 [PrivateKey]
     *
     * @param privateKeyString Base64 编码的私钥字符串（无换行）
     * @return 还原后的 [PrivateKey]
     */
    fun stringToRSAPrivateKey(privateKeyString: String): PrivateKey {
        return RSAEncryption.stringToPrivateKey(privateKeyString)
    }

    // =================== AES专用方法 ===================

    /**
     * 生成 AES 对称密钥（快捷方法）
     *
     * @return 新生成的 [SecretKey]
     */
    fun generateAESKey(): SecretKey {
        return AESEncryption.generateKey()
    }


    /**
     * 将 AES 密钥转为 Base64 字符串（快捷方法）
     *
     * @param key 要编码的 [SecretKey]
     * @return Base64 编码的密钥字符串（无换行）
     */
    fun aesKeyToString(key: SecretKey): String {
        return AESEncryption.keyToString(key)
    }


    /**
     * 将 Base64 编码的 AES 密钥字符串还原为 [SecretKey]（快捷方法）
     *
     * @param keyString Base64 编码的密钥字符串（无换行）
     * @return 还原后的 [SecretKey]
     */
    fun stringToAESKey(keyString: String): SecretKey {
        return AESEncryption.stringToKey(keyString)
    }

    /**
     * 使用 HKDF-SHA256 从原始密钥材料派生 AES-256 密钥（Base64 编码）。
     *
     * @param ikm 输入密钥材料（如 ECDH 共享密钥的原始字节）
     * @param info 上下文区分信息，防止不同用途派生相同密钥
     * @return Base64 编码的 32 字节 AES 密钥
     */
    fun hkdfDeriveKey(ikm: ByteArray, info: String = "pairing-code-encryption"): String {
        return AESEncryption.hkdfDeriveKey(ikm, info)
    }

    // =================== 配置和工具方法 ===================


    /**
     * 获取加密类型的显示名称（中文）
     *
     * @param type 要获取名称的 [EncryptionType]
     * @return 中文显示名称
     */
    fun getEncryptionTypeDisplayName(type: EncryptionType): String {
        return when (type) {
            EncryptionType.ECDH -> "ECDH密钥协商（推荐）"
            EncryptionType.AES -> "AES加密"
            EncryptionType.RSA -> "RSA加密（最高安全）"
        }
    }


    /**
     * 获取加密类型的详细描述（中文）
     *
     * @param type 要获取描述的 [EncryptionType]
     * @return 对该类型的中文描述
     */
    fun getEncryptionTypeDescription(type: EncryptionType): String {
        return when (type) {
            EncryptionType.ECDH ->
                "ECDH密钥协商，私钥由硬件安全模块保护。安全性高，推荐用于配对场景。"
            EncryptionType.AES ->
                "AES对称加密，安全性高，性能良好。推荐用于大多数场景。"
            EncryptionType.RSA ->
                "RSA非对称加密，最安全的加密方式，但性能相对较低。适用于对安全性要求极高的场景。"
        }
    }


    /**
     * 判断指定的加密类型是否支持对称加密
     *
     * @param type 要判断的 [EncryptionType]
     * @return 如果为 AES 则返回 true，否则返回 false
     */
    fun supportsSymmetricEncryption(type: EncryptionType): Boolean {
        return type == EncryptionType.AES || type == EncryptionType.ECDH
    }

    /**
     * 判断指定的加密类型是否支持非对称加密
     *
     * @param type 要判断的 [EncryptionType]
     * @return 如果为 RSA 则返回 true，否则返回 false
     */
    fun supportsAsymmetricEncryption(type: EncryptionType): Boolean {
        return type == EncryptionType.RSA
    }

    // =================== Android Keystore AES 密钥管理 ===================

    private val AES_DEVICE_SECRET_PREFIX = "aes_device_secret_"
    private val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private val GCM_IV_LENGTH = 12

    /**
     * 将设备 sharedSecret（Base64 编码的 AES-256 密钥）导入 Android Keystore。
     * 导入后密钥由硬件安全模块保护，应用无法再导出明文。
     *
     * @param context Android 上下文
     * @param uuid 设备 UUID，用于生成唯一的 Key alias
     * @param base64Key Base64 编码的 AES 密钥
     */
    fun importAesKeyToKeystore(context: Context, uuid: String, base64Key: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        // 移除旧密钥（如有），确保重新配对时覆盖
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        require(keyBytes.size == 32) { "Invalid AES-256 key length: ${keyBytes.size} for uuid=$uuid, expected 32 bytes" }
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
     * 密钥需事先通过 [importAesKeyToKeystore] 导入。
     *
     * @param data 明文字符串（UTF-8）
     * @param uuid 设备 UUID
     * @param context Android 上下文
     * @return Base64 编码的密文（12 字节 IV + GCM 密文，含 16 字节 tag）
     */
    fun encryptWithDeviceKey(data: String, uuid: String, context: Context): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val alias = AES_DEVICE_SECRET_PREFIX + uuid
        val secretKey = keyStore.getKey(alias, null) as? SecretKey
            ?: throw IllegalStateException("Keystore key not found for device $uuid")
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        // 由 Keystore 生成随机 IV（不允许调用方传入）
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
     *
     * @param encryptedData Base64 编码的密文
     * @param uuid 设备 UUID
     * @param context Android 上下文
     * @return 明文字符串（UTF-8）
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
     *
     * @param uuid 设备 UUID
     * @param context Android 上下文
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
     *
     * @param uuid 设备 UUID
     * @param context Android 上下文
     * @return 如果密钥存在返回 true
     */
    fun hasDeviceKey(uuid: String, context: Context): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.containsAlias(AES_DEVICE_SECRET_PREFIX + uuid)
    }
}
