package com.xzyht.notifyrelay.nativecore.device.appsync

/**
 * 图标编解码端口。
 *
 * 应用列表/图标同步的报文与缓存只处理 PNG 字节；
 * 字节与平台位图之间的转换、以及本机图标来源由平台侧实现注入。
 */
interface IconCodecPort {
    /** 解码 PNG 字节为平台位图；失败返回 null。 */
    fun decodePng(bytes: ByteArray): Any?

    /** 把平台位图编码为 PNG 字节；失败返回 null。 */
    fun encodePng(platformBitmap: Any): ByteArray?

    /** 取本机已安装应用的图标 PNG 字节；本机无该应用或平台无本机来源时返回 null。 */
    suspend fun localIconBytes(packageName: String): ByteArray?
}

/** [IconCodecPort] 的注册点。 */
object IconCodecPortRegistry {
    @Volatile
    var current: IconCodecPort? = null
        private set

    fun install(port: IconCodecPort) {
        current = port
    }
}
