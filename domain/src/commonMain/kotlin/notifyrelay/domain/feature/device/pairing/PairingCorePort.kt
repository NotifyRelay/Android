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

    /** 发送配对响应；返回 0 表示成功。 */
    fun sendPairingResp(
        remoteUuid: String,
        localPublicKey: String,
        code: String,
        remoteIp: String,
        battery: Int,
        platform: String,
    ): Int

    /** 清理已生成的配对码。 */
    fun clearPairingCode()
}
