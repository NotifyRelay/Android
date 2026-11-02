package notifyrelay.domain.feature.device.pairing

/**
 * 配对所需的 Rust 核心能力端口。
 *
 * 由宿主侧以 `NativeCore` 实现并注入，避免公共端反向依赖核心模块。
 */
interface PairingCorePort {
    /** 核心是否已初始化。 */
    fun isReady(): Boolean

    /** 生成配对码；核心未就绪时返回 null。 */
    fun generatePairingCode(ttlSecs: Int = 300): String?

    /** 发送配对初始化请求；返回 0 表示成功。 */
    fun sendPairingInit(
        localUuid: String,
        remoteUuid: String,
        displayCode: String,
        battery: Int,
        platform: String,
    ): Int

    /**
     * 发送配对响应；返回 0 表示成功。
     *
     * @param localUuid 本机 uuid：core 将其编码为 PAIRING_RESP 首段（发送方身份），
     *   发起方据此按「对端 uuid」精确匹配自己的配对会话；勿传对端 uuid。
     * @param remoteIp 发起方地址，作为 core 解析本机地址失败时的兜底。
     */
    fun sendPairingResp(
        localUuid: String,
        localPublicKey: String,
        code: String,
        remoteIp: String,
        battery: Int,
        platform: String,
    ): Int

    /** 清理已生成的配对码。 */
    fun clearPairingCode()
}
