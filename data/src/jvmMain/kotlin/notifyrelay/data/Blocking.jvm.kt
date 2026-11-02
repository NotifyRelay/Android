package notifyrelay.data

import kotlinx.coroutines.runBlocking

/** JVM 侧的阻塞执行实现：直接委托 kotlinx.coroutines.runBlocking。 */
actual fun <T> runBlockingCommon(block: suspend () -> T): T = runBlocking { block() }
