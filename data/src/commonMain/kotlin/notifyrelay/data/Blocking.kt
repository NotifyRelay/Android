package notifyrelay.data

/**
 * 公共端的阻塞执行入口。
 *
 * commonMain 无法直接进行阻塞等待，由各平台源集提供实现
 * （Android 侧为 kotlinx.coroutines.runBlocking）。
 */
expect fun <T> runBlockingCommon(block: suspend () -> T): T
