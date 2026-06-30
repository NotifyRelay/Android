package notifyrelay.core.util

import java.security.SecureRandom

/**
 * 配对码管理器，提供统一的配对码生成、验证、过期管理。
 *
 * 设备间 TCP 配对和 fcitx5 剪贴板配对都使用此组件。
 * 调用方负责决定"谁生成码、谁输入码"。
 *
 * 使用示例（服务端）：
 *   val code = PairingCodeManager.generate()
 *   UI.show(code)
 *   // 收到客户端传来的 code 后：
 *   if (PairingCodeManager.verify(receivedCode)) { ... }
 *
 * 使用示例（客户端）：
 *   // 用户输入 code，发送给服务端
 */
object PairingCodeManager {

    private const val CODE_LENGTH = 6
    private const val EXPIRY_MS = 5 * 60 * 1000L // 5 分钟

    private val secureRandom = SecureRandom()
    private var currentCode: String? = null
    private var generatedAt: Long = 0L
    private var verifiedCode: String? = null

    /**
     * 生成 6 位数字配对码（覆盖之前未使用的码）。
     */
    @Synchronized
    fun generate(): String {
        val code = (secureRandom.nextInt(900_000) + 100_000).toString()
        currentCode = code
        generatedAt = System.currentTimeMillis()
        verifiedCode = null
        return code
    }

    /**
     * 获取当前有效配对码，过期返回 null。
     */
    @Synchronized
    fun getCurrent(): String? {
        if (currentCode == null) return null
        if (System.currentTimeMillis() - generatedAt > EXPIRY_MS) {
            currentCode = null
            return null
        }
        return currentCode
    }

    /**
     * 验证配对码是否有效且匹配。
     * 验证成功后自动清除（一次性使用）。
     */
    @Synchronized
    fun verify(code: String): Boolean {
        val stored = getCurrent() ?: return false
        if (code != stored) return false
        verifiedCode = code
        clear()
        return true
    }

    /**
     * 检查指定配对码是否已被验证过。
     */
    @Synchronized
    fun isVerified(code: String): Boolean {
        return code == verifiedCode && verifiedCode != null
    }

    /**
     * 手动清除当前配对码。
     */
    @Synchronized
    fun clear() {
        currentCode = null
        generatedAt = 0L
        verifiedCode = null
    }

    /**
     * 存储外部传入的配对码（用于接收端验证）。
     * 由发起端生成并随 PAIRING_REQ 发送，接收端将其存储供用户输入验证。
     */
    @Synchronized
    fun storeForVerification(code: String) {
        currentCode = code
        generatedAt = System.currentTimeMillis()
    }

    /**
     * 配对码是否有效（未过期）。
     */
    @Synchronized
    fun isValid(): Boolean {
        return getCurrent() != null
    }
}
